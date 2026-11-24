package it.gzanee.flightfinder.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Live Google quotes over the device's own connection: port of LiveQuotes
 * (flightcore/live_quotes.py) and of the consent cookie flow (consent.py).
 * One request at a time, a pause after every answer, no automatic retry of a
 * 403/429, a five-minute cache: the same rules as the Python engine.
 */

const val QUOTE_TTL = 300.0
private const val CACHE_LIMIT = 2000
private const val NETWORK_RETRIES = 2
private const val GOOGLE_ERROR_RETRIES = 5
private const val MAX_GOOGLE_ERROR_WAIT = 30.0
const val DEFAULT_REQUEST_INTERVAL = 0.3
private const val REQUEST_OVERHEAD = 0.45
private const val BATCH_REQUEST_OVERHEAD = 0.75
private const val BLOCK_SECONDS = 900.0

fun secondsPerSearch(r: FlexibleRequest, interval: Double = DEFAULT_REQUEST_INTERVAL) =
    interval + if (airportsPerSearch(r) > 1) BATCH_REQUEST_OVERHEAD else REQUEST_OVERHEAD

data class HttpResponse(val status: Int, val url: String, val text: String, val setCookies: List<String> = emptyList())

/** The phone's HTTP client (Ktor in the app, a fake in tests). */
interface Http {
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpResponse
}

interface EngineClock {
    fun now(): Double // seconds
    suspend fun sleep(seconds: Double)
}

object RealClock : EngineClock {
    override fun now() = Calendar.nowSeconds()
    override suspend fun sleep(seconds: Double) = delay((seconds * 1000).toLong())
}

interface CookieStore {
    suspend fun load(): Map<String, String>?
    suspend fun save(cookies: Map<String, String>)
}

class MemoryCookies : CookieStore {
    private var saved: Map<String, String>? = null
    override suspend fun load() = saved
    override suspend fun save(cookies: Map<String, String>) {
        saved = cookies
    }
}

/** Google's "Reject all" consent cookie, as sent by many open-source clients. */
const val DEFAULT_SOCS = "CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
private val RELEVANT_COOKIES = setOf("SOCS", "CONSENT", "__Secure-ENID")
private const val PROBE_URL = "https://www.google.com/travel/flights?hl=en&gl=IT&curr=EUR"
private const val CONSENT_SAVE_URL = "https://consent.google.com/save"
val BASE_HEADERS = mapOf(
    "Accept-Language" to "it-IT,it;q=0.9,en;q=0.8",
    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
)

private fun decodeEntities(text: String): String = text
    .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
    .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

fun hiddenInputs(block: String): Map<String, String> {
    val fields = LinkedHashMap<String, String>()
    for (m in Regex("""<input[^>]*type="hidden"[^>]*>""").findAll(block)) {
        val tag = m.value
        val name = Regex("""name="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: continue
        fields[name] = decodeEntities(Regex("""value="([^"]*)"""").find(tag)?.groupValues?.get(1) ?: "")
    }
    return fields
}

private fun parseSetCookies(lines: List<String>): Map<String, String> {
    val out = HashMap<String, String>()
    for (line in lines) {
        for (part in line.split(Regex(""",(?=\s*[A-Za-z0-9_\-]+=)"""))) {
            val m = Regex("""^\s*([^=;\s]+)=([^;]*)""").find(part) ?: continue
            if (m.groupValues[1] in RELEVANT_COOKIES) out[m.groupValues[1]] = m.groupValues[2]
        }
    }
    return out
}

class Consent(private val http: Http, private val store: CookieStore) {
    private var cookies: Map<String, String>? = null

    suspend fun get(): Map<String, String> {
        cookies?.let { return it }
        val saved = runCatching { store.load() }.getOrNull()
        return (if (saved != null && "SOCS" in saved) saved else mapOf("SOCS" to DEFAULT_SOCS)).also { cookies = it }
    }

    /** Reject-all handshake, like consent._perform_handshake. Falls back to the default cookie. */
    suspend fun refresh(): Map<String, String> {
        var fresh = mapOf("SOCS" to DEFAULT_SOCS)
        try {
            val probe = http.get(PROBE_URL, BASE_HEADERS + ("Accept-Language" to "en-US,en;q=0.9"))
            val page = probe.text
            if ("consent.google.com" in page.take(2000) || "consent.google.com" in probe.url) {
                val anchor = page.indexOf("set_eom\" value=\"true\"")
                if (anchor >= 0) {
                    val start = page.lastIndexOf("<form", anchor)
                    val end = page.indexOf("</form>", anchor).let { if (it < 0) page.length else it }
                    val saved = http.postForm(CONSENT_SAVE_URL, hiddenInputs(page.substring(maxOf(start, 0), end)), mapOf("Accept-Language" to "en-US,en;q=0.9"))
                    val got = parseSetCookies(saved.setCookies)
                    if ("SOCS" in got) fresh = got
                }
            }
        } catch (e: Exception) {
            // Keep the default consent cookie.
        }
        cookies = fresh
        runCatching { store.save(fresh) }
        return fresh
    }

    fun header(cookies: Map<String, String>) = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

data class RouteResult(val quotes: List<Quote>, val unverified: Int, val empty: Boolean)

class LiveQuotes(
    private val http: Http,
    cookies: CookieStore = MemoryCookies(),
    private val clock: EngineClock = RealClock,
) {
    private val cache = LinkedHashMap<String, Pair<Double, RouteResult>>()
    private val network = Mutex()
    private var nextRequest = 0.0
    var blockedUntil = 0.0
        private set
    var requestInterval = DEFAULT_REQUEST_INTERVAL
    val consent = Consent(http, cookies)

    fun clearCache() = cache.clear()

    suspend fun search(origin: String, destination: String, day: String, r: FlexibleRequest, inbound: Boolean = false, fresh: Boolean = false): RouteResult =
        searchMany(listOf(origin), listOf(destination), day, r, inbound, fresh).getValue(origin to destination)

    /** Live one-way quotes of every origin-destination pair. */
    suspend fun searchMany(
        origins: List<String>, destinations: List<String>, day: String, r: FlexibleRequest,
        inbound: Boolean = false, fresh: Boolean = false,
    ): Map<Route, RouteResult> {
        val pairs = origins.flatMap { o -> destinations.filter { it != o }.map { o to it } }
        val results = HashMap<Route, RouteResult>()
        val missing = ArrayList<Route>()
        val now = clock.now()
        for (pair in pairs) {
            val hit = cache[key(pair, day, r, inbound)]
            if (!fresh && hit != null && now - hit.first < QUOTE_TTL) results[pair] = hit.second else missing.add(pair)
        }
        for ((os, ds) in pairBatches(missing, minOf(airportsPerSearch(r), MAX_AIRPORTS_PER_SEARCH))) {
            for ((pair, result) in requestGroup(os, ds, day, r, inbound)) {
                if (cache.size >= CACHE_LIMIT) cache.remove(cache.keys.first())
                cache[key(pair, day, r, inbound)] = clock.now() to result
                results[pair] = result
            }
        }
        return pairs.associateWith { results.getValue(it) }
    }

    private fun key(pair: Route, day: String, r: FlexibleRequest, inbound: Boolean): String {
        val (lo, hi) = r.window(inbound)
        return listOf(
            pair.first, pair.second, day, lo, hi, r.adults, r.children, r.maxStops, r.allowSelfTransfer,
            r.baggage, r.currency, r.infants, r.seat, r.airlines, r.excludeBasicEconomy,
        ).joinToString("|")
    }

    /** One Google request; a group Google cannot answer is retried pair by pair. */
    private suspend fun requestGroup(origins: List<String>, destinations: List<String>, day: String, r: FlexibleRequest, inbound: Boolean): Map<Route, RouteResult> {
        try {
            return request(origins, destinations, day, r, inbound)
        } catch (e: LiveBlocked) {
            throw e
        } catch (e: LiveUnavailable) {
            if (origins.size == 1 && destinations.size == 1) throw e
        }
        val found = LinkedHashMap<Route, RouteResult>()
        for (o in origins) for (d in destinations) if (o != d) found.putAll(request(listOf(o), listOf(d), day, r, inbound))
        return found
    }

    private suspend fun request(origins: List<String>, destinations: List<String>, day: String, r: FlexibleRequest, inbound: Boolean): Map<Route, RouteResult> {
        val q = oneWayQuery(origins, destinations, day, r, inbound)
        var quotes: List<Quote> = emptyList()
        var rejected = 0
        for (attempt in 0..GOOGLE_ERROR_RETRIES) {
            val html = fetchPage(q)
            try {
                val (qs, rj) = decode(html, r, bookingUrl(q), origins to destinations)
                quotes = qs
                rejected = rj
                break
            } catch (e: LiveTransient) {
                if (attempt == GOOGLE_ERROR_RETRIES) throw e
                clock.sleep(minOf(MAX_GOOGLE_ERROR_WAIT, (1 shl (attempt + 1)).toDouble()))
            }
        }
        val byPair = LinkedHashMap<Route, MutableList<Quote>>()
        for (o in origins) for (d in destinations) if (o != d) byPair[o to d] = ArrayList()
        for (quote in quotes) {
            val list = byPair[quote.first.fromCode to quote.last.toCode]
            if (list != null) list.add(quote) else rejected += 1 // an offer for airports nobody asked about
        }
        return byPair.entries.withIndex().associate { (index, e) ->
            e.key to RouteResult(e.value, if (index == 0) rejected else 0, e.value.isEmpty() && rejected == 0)
        }
    }

    /** Single-ticket return fares: {outbound fingerprint: cheapest total}, or null. */
    suspend fun roundTrip(origin: String, destination: String, out: String, back: String, r: FlexibleRequest): Map<String, Double>? {
        val q = roundTripQuery(origin, destination, out, back, r)
        val quotes = try {
            decode(fetchPage(q), r, bookingUrl(q)).first
        } catch (e: LiveBlocked) {
            throw e
        } catch (e: LiveUnavailable) {
            return null
        }
        val fares = LinkedHashMap<String, Double>()
        for (quote in quotes) {
            if (quote.first.fromCode == origin && quote.last.toCode == destination) {
                val key = fingerprint(quote)
                fares[key] = minOf(quote.price, fares[key] ?: quote.price)
            }
        }
        return fares
    }

    /** Serialized: one request every interval, no parallel burst. */
    suspend fun fetchPage(q: QueryOptions): String = network.withLock { fetchNow(q) }

    private suspend fun fetchNow(q: QueryOptions): String {
        if (clock.now() < blockedUntil) throw LiveBlocked("Google limita le richieste. Ricerca fermata; riprova più tardi.")
        val wait = nextRequest - clock.now()
        if (wait > 0) clock.sleep(wait)
        var consentRetried = false
        var response: HttpResponse? = null
        var attempt = 0
        while (attempt <= NETWORK_RETRIES) {
            try {
                val cookies = consent.get()
                val res = http.get(searchUrl(q), BASE_HEADERS + ("Cookie" to consent.header(cookies)))
                if (!consentRetried && isConsentPage(res)) {
                    // The saved consent is not accepted any more: renew it once.
                    consentRetried = true
                    consent.refresh()
                    continue
                }
                response = res
                break
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == NETWORK_RETRIES) {
                    throw LiveUnavailable("Connessione a Google non riuscita dopo vari tentativi. Puoi premere Riprendi.")
                }
                clock.sleep(1.5 * (attempt + 1))
                attempt++
            }
        }
        val res = response!!
        nextRequest = clock.now() + requestInterval
        if (res.status == 403 || res.status == 429 || "/sorry/" in res.url || "unusual traffic" in res.text.take(6000).lowercase()) {
            blockedUntil = clock.now() + BLOCK_SECONDS
            throw LiveBlocked("Google ha limitato le richieste. Nessun tentativo automatico.")
        }
        if (res.status != 200) throw LiveUnavailable("Google ha risposto HTTP ${res.status}.")
        return res.text
    }

    private fun isConsentPage(res: HttpResponse) =
        "consent.google.com" in res.url || "consent.google.com/" in res.text.take(2000)
}

/** Google Flights page for the same airports and dates as one A/R search (round_trip_url). */
fun roundTripUrl(trip: Trip, r: FlexibleRequest): String? {
    val back = trip.inbound ?: return null
    val out = trip.outbound.segments
    val origin = out.first().fromCode
    val destination = out.last().toCode
    if (back.first.fromCode != destination || back.last.toCode != origin) return null
    val plain = r.copy(baggage = "base", allowSelfTransfer = true, excludeBasicEconomy = false, airlines = emptyList())
    val q = roundTripQuery(origin, destination, out.first().departure.take(10), back.first.departure.take(10), plain)
    return bookingUrl(q.copy(legs = q.legs.map { it.copy(earliestDepartureHour = null, latestDepartureHour = null, airlines = null) }))
}
