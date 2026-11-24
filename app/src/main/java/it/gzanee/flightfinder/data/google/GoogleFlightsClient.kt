package it.gzanee.flightfinder.data.google

import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.search.EngineClock
import it.gzanee.flightfinder.domain.search.MAX_AIRPORTS_PER_SEARCH
import it.gzanee.flightfinder.domain.search.QuoteSource
import it.gzanee.flightfinder.domain.search.RouteQuotes
import it.gzanee.flightfinder.domain.search.Route
import it.gzanee.flightfinder.domain.search.airportsPerSearch
import it.gzanee.flightfinder.domain.search.fingerprint
import it.gzanee.flightfinder.domain.search.pairBatches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Risposta HTTP ridotta a quello che serve: stato, URL finale dopo i redirect, testo, cookie. */
data class HttpPage(val status: Int, val url: String, val body: String, val setCookies: List<String> = emptyList())

/** La rete del telefono (OkHttp nell'app, una finta nei test). */
interface GoogleHttp {
    suspend fun get(url: String, headers: Map<String, String>): HttpPage
    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpPage
}

/**
 * Prezzi live di Google dalla connessione del telefono: port di LiveQuotes del portale.
 * Una richiesta alla volta, una pausa dopo ogni risposta, nessun nuovo tentativo dopo un
 * 403/429, una cache di cinque minuti: le stesse regole del portale.
 */
class GoogleFlightsClient(
    private val http: GoogleHttp,
    private val decoder: GooglePageDecoder,
    val consent: GoogleConsent,
    private val clock: EngineClock = EngineClock.System,
) : QuoteSource {
    private val cache = LinkedHashMap<String, Pair<Double, RouteQuotes>>()
    private val network = Mutex()
    private var nextRequest = 0.0
    var blockedUntil = 0.0
        private set
    var requestInterval = DEFAULT_REQUEST_INTERVAL

    /** Secondi stimati per una richiesta di questo tipo, per il tempo che manca. */
    override fun secondsPerSearch(request: SearchRequest) =
        requestInterval + if (request.airportsPerSearch > 1) BATCH_REQUEST_OVERHEAD else REQUEST_OVERHEAD

    /** Le offerte di ogni coppia di aeroporti; più coppie viaggiano in una sola richiesta quando non si perde nulla. */
    override suspend fun searchMany(
        origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest,
        inbound: Boolean, fresh: Boolean,
    ): Map<Route, RouteQuotes> {
        val pairs = origins.flatMap { o -> destinations.filter { it != o }.map { o to it } }
        val results = HashMap<Route, RouteQuotes>()
        val missing = pairs.filter { pair ->
            val hit = cache[cacheKey(pair, day, request, inbound)]
            if (!fresh && hit != null && clock.now() - hit.first < QUOTE_TTL_SECONDS) {
                results[pair] = hit.second
                false
            } else {
                true
            }
        }
        for (batch in pairBatches(missing, minOf(request.airportsPerSearch, MAX_AIRPORTS_PER_SEARCH))) {
            for ((pair, quotes) in requestGroup(batch.from, batch.to, day, request, inbound)) {
                if (cache.size >= CACHE_LIMIT) cache.remove(cache.keys.first())
                cache[cacheKey(pair, day, request, inbound)] = clock.now() to quotes
                results[pair] = quotes
            }
        }
        return pairs.associateWith { results.getValue(it) }
    }

    fun clearCache() = cache.clear()

    private fun cacheKey(pair: Route, day: LocalDate, r: SearchRequest, inbound: Boolean) = listOf(
        pair.first, pair.second, day, r.departureWindow(inbound), r.adults, r.children, r.maxStops, r.allowSelfTransfer,
        r.baggage, r.currency, r.infants, r.seat, r.airlines, r.excludeBasicEconomy,
    ).joinToString("|")

    /** Una richiesta a Google; un gruppo a cui Google non risponde si riprova una coppia alla volta. */
    private suspend fun requestGroup(origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest, inbound: Boolean): Map<Route, RouteQuotes> {
        try {
            return request(origins, destinations, day, request, inbound)
        } catch (e: GoogleBlockedException) {
            throw e
        } catch (e: GoogleUnavailableException) {
            if (origins.size == 1 && destinations.size == 1) throw e
        }
        val found = LinkedHashMap<Route, RouteQuotes>()
        for (o in origins) for (d in destinations) if (o != d) found += request(listOf(o), listOf(d), day, request, inbound)
        return found
    }

    private suspend fun request(origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest, inbound: Boolean): Map<Route, RouteQuotes> {
        val query = GoogleQuery.oneWay(origins, destinations, day, request, inbound)
        var page: GooglePage? = null
        for (attempt in 0..GOOGLE_ERROR_RETRIES) {
            val html = fetchPage(query)
            try {
                page = decoder.decode(html, request, query.bookingUrl, origins to destinations)
                break
            } catch (e: GoogleTransientException) {
                if (attempt == GOOGLE_ERROR_RETRIES) throw e
                // Gli errori arrivano a raffiche: pause crescenti invece di riprovare subito.
                clock.sleep(minOf(MAX_GOOGLE_ERROR_WAIT, (1 shl (attempt + 1)).toDouble()))
            }
        }
        var rejected = page!!.rejected
        val byPair = LinkedHashMap<Route, MutableList<Quote>>()
        for (o in origins) for (d in destinations) if (o != d) byPair[o to d] = ArrayList()
        for (quote in page.quotes) {
            byPair[quote.first.fromCode to quote.last.toCode]?.add(quote) ?: run { rejected += 1 } // offerta per aeroporti non richiesti
        }
        // Una riga illeggibile non si sa a che coppia appartenga: si conta una volta, e nessuna coppia si dice vuota.
        return byPair.entries.withIndex().associate { (index, entry) ->
            entry.key to RouteQuotes(entry.value, if (index == 0) rejected else 0, entry.value.isEmpty() && rejected == 0)
        }
    }

    /** Prezzi del biglietto A/R unico: impronta dell'andata → prezzo più basso, o null se la pagina non si legge. */
    override suspend fun roundTripFares(origin: String, destination: String, out: LocalDate, back: LocalDate, request: SearchRequest): Map<String, Double>? {
        val query = GoogleQuery.roundTrip(origin, destination, out, back, request)
        val quotes = try {
            decoder.decode(fetchPage(query), request, query.bookingUrl).quotes
        } catch (e: GoogleBlockedException) {
            throw e
        } catch (e: GoogleUnavailableException) {
            return null
        }
        return quotes.filter { it.first.fromCode == origin && it.last.toCode == destination }
            .groupBy { it.fingerprint() }
            .mapValues { (_, list) -> list.minOf { it.price } }
    }

    /** Pagina di Google Flights per gli stessi aeroporti e le stesse date come unica ricerca A/R (round_trip_url). */
    override fun roundTripUrl(trip: Trip, request: SearchRequest): String? {
        val back = trip.inbound ?: return null
        if (back.first.fromCode != trip.destination || back.last.toCode != trip.origin) return null
        val plain = request.copy(baggage = it.gzanee.flightfinder.domain.model.Baggage.BASE, allowSelfTransfer = true, excludeBasicEconomy = false, airlines = emptyList())
        val query = GoogleQuery.roundTrip(trip.origin, trip.destination, trip.outbound.first.departure.toLocalDate(), back.first.departure.toLocalDate(), plain)
        return query.copy(legs = query.legs.map { it.copy(earliestDepartureHour = null, latestDepartureHour = null) }).bookingUrl
    }

    /** Una richiesta alla volta, con la pausa dopo ogni risposta. */
    suspend fun fetchPage(query: GoogleQuery): String = network.withLock {
        if (clock.now() < blockedUntil) throw GoogleBlockedException("Google limita le richieste. Ricerca fermata; riprova più tardi.")
        val wait = nextRequest - clock.now()
        if (wait > 0) clock.sleep(wait)
        val page = getWithConsent(query.searchUrl)
        nextRequest = clock.now() + requestInterval
        if (page.status == 403 || page.status == 429 || "/sorry/" in page.url || "unusual traffic" in page.body.take(6000).lowercase()) {
            blockedUntil = clock.now() + BLOCK_SECONDS
            throw GoogleBlockedException("Google ha limitato le richieste. Nessun tentativo automatico.")
        }
        if (page.status != 200) throw GoogleUnavailableException("Google ha risposto HTTP ${page.status}.")
        page.body
    }

    private suspend fun getWithConsent(url: String): HttpPage {
        var consentRenewed = false
        var attempt = 0
        while (true) {
            try {
                val page = http.get(url, BASE_HEADERS + ("Cookie" to consent.cookieHeader()))
                if (!consentRenewed && GoogleConsent.isConsentPage(page)) {
                    // Il consenso salvato non vale più: si rinnova una volta.
                    consentRenewed = true
                    consent.renew()
                    continue
                }
                return page
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == NETWORK_RETRIES) {
                    throw GoogleUnavailableException("Connessione a Google non riuscita dopo vari tentativi. Puoi premere Riprendi.")
                }
                clock.sleep(1.5 * (attempt + 1))
                attempt++
            }
        }
    }

    companion object {
        const val QUOTE_TTL_SECONDS = 300.0
        const val DEFAULT_REQUEST_INTERVAL = 0.3
        private const val REQUEST_OVERHEAD = 0.45
        private const val BATCH_REQUEST_OVERHEAD = 0.75
        private const val CACHE_LIMIT = 2000
        private const val NETWORK_RETRIES = 2
        private const val GOOGLE_ERROR_RETRIES = 5
        private const val MAX_GOOGLE_ERROR_WAIT = 30.0
        private const val BLOCK_SECONDS = 900.0
        val BASE_HEADERS = mapOf(
            "Accept-Language" to "it-IT,it;q=0.9,en;q=0.8",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
    }
}
