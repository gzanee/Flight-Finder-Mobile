package it.gzanee.flightfinder.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/*
 * Resumable search jobs on the device: port of flightcore/flexible_jobs.py.
 * Progress is saved; a block stops the search; no availability is invented.
 */

const val ENGINE = 3
private const val SAVE_INTERVAL = 1.0
private const val PACE_SAMPLE = 3
const val ROUND_TRIP_CHECKS = 150
private const val SHOWN_AIRPORTS = 3

@Serializable
data class Recheck(
    val status: String, // confirmed | not_confirmed | outside_filters
    val result: Trip? = null,
    @SerialName("price_changed") val priceChanged: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class Job(
    val id: String,
    val request: FlexibleRequest,
    val plan: Plan,
    var status: String,
    var engine: Int = ENGINE,
    var completed: Int = 0,
    var total: Int = 0,
    var cursor: List<Int> = listOf(0, 0, 0),
    var pass: Int = 0,
    var outbound: MutableMap<String, List<Quote>> = LinkedHashMap(),
    var requests: Int = 0,
    @SerialName("return_days") var returnDays: Int = 0,
    @SerialName("return_requests") var returnRequests: Int = 0,
    @SerialName("eta_seconds") var etaSeconds: Int? = null,
    var results: MutableMap<String, Trip> = LinkedHashMap(),
    var rechecks: MutableMap<String, Recheck> = LinkedHashMap(),
    @SerialName("round_trips") var roundTrips: MutableMap<String, Map<String, Double>> = LinkedHashMap(),
    var unverified: Int = 0,
    @SerialName("empty_searches") var emptySearches: Int = 0,
    var issues: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") var updatedAt: String,
    var current: String? = null,
    /** Date of the search, for the horizon of Google's sales (device "today"). */
    val today: String? = null,
) {
    val running get() = status == "running" || status == "cancelling"
}

interface JobStore {
    suspend fun load(id: String): String?
    suspend fun save(id: String, body: String)
    suspend fun list(): List<String>
    suspend fun remove(id: String)
}

class MemoryJobs : JobStore {
    val data = LinkedHashMap<String, String>()
    override suspend fun load(id: String) = data[id]
    override suspend fun save(id: String, body: String) {
        data[id] = body
    }
    override suspend fun list() = data.keys.toList()
    override suspend fun remove(id: String) {
        data.remove(id)
    }
}

val JOB_JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }

fun airportList(codes: List<String>): String =
    if (codes.size <= SHOWN_AIRPORTS) codes.joinToString(", ")
    else "${codes.take(SHOWN_AIRPORTS - 1).joinToString(", ")} e altri ${codes.size - SHOWN_AIRPORTS + 1}"

fun resultAirports(request: FlexibleRequest, results: List<Trip>): Map<String, Airports.Details> {
    val codes = request.origins.toMutableSet()
    for (t in results) for (leg in listOfNotNull(t.outbound, t.inbound)) for (s in leg.segments) codes += listOf(s.fromCode, s.toCode)
    return codes.sortedWith(PY_STRING).associateWith(Airports::details)
}

/** What the results screen shows: the job and one page of explored results. */
data class JobView(val job: Job, val browse: Browse, val airports: Map<String, Airports.Details>)

private class Pace(val started: Double) {
    var requests = 0
    var later = 0.0
    var done = 0
    var label = ""
}

class JobManager(
    private val store: JobStore,
    val live: LiveQuotes,
    private val clock: EngineClock = RealClock,
    scope: CoroutineScope? = null,
) {
    /** Every access to job state runs here, one at a time. */
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = scope ?: CoroutineScope(SupervisorJob() + serial)
    private var active: Job? = null
    private var worker: CoJob? = null
    private var savedAt = 0.0
    private var saveCost = 0.0
    private val listeners = ArrayList<(Job) -> Unit>()

    /** Called after every saved step (on the engine's thread), for a live progress view. */
    fun subscribe(listener: (Job) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    val running get() = worker?.isActive == true
    val activeId get() = if (running) active?.id else null

    private suspend fun <T> confined(block: suspend () -> T): T = withContext(serial) { block() }

    private suspend fun load(id: String): Job {
        active?.takeIf { it.id == id }?.let { return it }
        val body = store.load(id) ?: throw NoSuchElementException("Ricerca o risultato non trovato.")
        return JOB_JSON.decodeFromString(Job.serializer(), body)
    }

    suspend fun get(id: String): Job = confined { load(id).copyDeep() }

    private suspend fun save(job: Job) {
        store.save(job.id, JOB_JSON.encodeToString(Job.serializer(), job))
        listeners.toList().forEach { it(job) }
    }

    suspend fun create(request: FlexibleRequest, plan: Plan, today: String = Calendar.today()): String = confined {
        if (running) throw IllegalStateException("Una ricerca è già in corso: fermala prima di avviarne un altra.")
        val now = Calendar.utcNow()
        val job = Job(
            id = (1..32).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString(""),
            request = request, plan = plan, status = "running", total = plan.searchesUpperBound,
            createdAt = now, updatedAt = now, today = today,
        )
        save(job)
        launch(job)
        job.id
    }

    private fun launch(job: Job) {
        active = job
        worker = scope.launch(serial) { run(job) }
    }

    /** Suspends until the running search, if any, has stopped and been saved. */
    suspend fun idle() {
        worker?.join()
    }

    suspend fun resume(id: String) = confined {
        if (running) throw IllegalStateException("Attendi che la ricerca attiva si fermi.")
        val job = load(id)
        if (job.status == "complete") throw IllegalStateException("Ricerca già completata. Avviane una nuova per prezzi aggiornati.")
        job.status = "running"
        job.issues = emptyList()
        save(job)
        launch(job)
    }

    suspend fun cancel(id: String) = confined {
        val job = load(id)
        if (job.status == "running") {
            job.status = "cancelling"
            if (active !== job || !running) save(job)
        }
    }

    suspend fun remove(id: String) = confined {
        if (activeId == id) throw IllegalStateException("Ferma la ricerca prima di eliminarla.")
        if (active?.id == id) active = null
        store.remove(id)
    }

    suspend fun ids(): List<String> = confined { store.list() }

    private suspend fun run(job: Job) {
        val request = job.request
        val passes = searchPasses(request, job.plan.destinations, job.today ?: Calendar.today())
        if (job.engine != ENGINE) {
            job.engine = ENGINE; job.completed = 0; job.cursor = listOf(0, 0, 0); job.outbound = LinkedHashMap(); job.requests = 0
            job.pass = 0; job.returnDays = 0; job.returnRequests = 0
        }
        job.total = passes.sumOf { it.total }
        savedAt = 0.0
        saveCost = 0.0
        val pace = Pace(clock.now())
        var status = "complete"
        try {
            for (number in job.pass until passes.size) {
                pace.later = passes.drop(number + 1).sumOf { it.total * secondsPerSearch(it.request, live.requestInterval) }
                pace.done = passes.take(number).sumOf { it.total }
                pace.label = if (passes.size > 1) listOf("Voli diretti · ", "Con scalo · ")[number] else ""
                if (search(job, request, passes[number], pace) == "cancelled") {
                    status = "cancelled"
                    break
                }
                job.pass = number + 1; job.cursor = listOf(0, 0, 0); job.outbound = LinkedHashMap()
                job.returnDays = 0; job.returnRequests = 0
            }
            if (status == "complete" && !compareRoundTrips(job, request)) status = "cancelled"
            val last = passes.last()
            if (status == "complete" && last.unsoldFrom != null) {
                job.issues = listOf(
                    "Google vende i voli fino al ${last.horizon.split("-").reversed().joinToString("/")}: " +
                        "le date successive non sono state cercate.",
                )
            }
        } catch (e: CancellationException) {
            status = "interrupted"
            job.issues = listOf("Ricerca interrotta; il progresso è salvato.")
        } catch (e: LiveBlocked) {
            status = "blocked"
            job.issues = listOf(e.message ?: "")
        } catch (e: Exception) {
            status = "interrupted"
            job.issues = listOf(if (e is LiveUnavailable) e.message ?: "" else "Ricerca interrotta da un errore; il progresso è salvato.")
        } finally {
            // A stop requested meanwhile wins, as in Python.
            job.status = if (job.status == "cancelling") "cancelled" else status
            job.updatedAt = Calendar.utcNow()
            if (job.status == "complete") job.etaSeconds = 0
            save(job)
        }
    }

    /** Run one schedule from the saved cursor (day, stage, batch); return the final status. */
    private suspend fun search(job: Job, request: FlexibleRequest, schedule: Schedule, pace: Pace): String {
        val (start, stage0, batch0) = job.cursor
        var stage = stage0
        var batch = batch0
        val searched = schedule.request
        for (index in start until schedule.days.size) {
            val day = schedule.days[index]
            val base = pace.done + schedule.unitsBefore(index)
            val outs = if (day in schedule.outDays) schedule.outBatches.size else 0
            if (stage == 0) {
                for (number in batch until outs) {
                    val (origins, destinations) = schedule.outBatches[number]
                    if (!begin(job, pace.label + "${airportList(origins)} → ${airportList(destinations)} · $day")) return "cancelled"
                    val found = live.searchMany(origins, destinations, day, searched)
                    keepOutbound(job, request, day, found)
                    job.cursor = listOf(index, 0, number + 1)
                    job.completed = base + number + 1
                    advance(job, schedule, pace)
                }
                stage = 1
                batch = 0
            }
            if (day in schedule.outsFor) {
                val wanted = wantedReturns(job, request, schedule, day)
                val batches = schedule.returnBatches(wanted.keys.toList())
                for (number in batch until batches.size) {
                    val (aways, homes) = batches[number]
                    if (!begin(job, pace.label + "Ritorno: ${airportList(aways)} → ${airportList(homes)} · $day")) return "cancelled"
                    val found = live.searchMany(aways, homes, day, searched, inbound = true)
                    combineReturns(job, request, day, found, wanted)
                    job.cursor = listOf(index, 1, number + 1)
                    job.completed = base + outs + minOf(number + 1, schedule.backBound)
                    job.returnRequests += 1
                    advance(job, schedule, pace)
                }
                job.returnDays += 1
            }
            // Every return day of that outbound has been priced.
            job.outbound.keys.filter { (schedule.lastBack[it.takeLast(10)] ?: "") <= day }.forEach { job.outbound.remove(it) }
            stage = 0
            batch = 0
            job.cursor = listOf(index + 1, 0, 0)
            job.completed = pace.done + schedule.unitsBefore(index + 1)
        }
        return "complete"
    }

    /** Compare trips with Google's single-ticket return fares; false if stopped. */
    private suspend fun compareRoundTrips(job: Job, request: FlexibleRequest): Boolean {
        val price = LinkedHashMap<String, Double>()
        for (trip in job.results.values) {
            val key = roundTripKey(trip) ?: continue
            price[key] = minOf(trip.price, price[key] ?: trip.price)
        }
        val byPrice = price.keys.sortedWith(compareBy<String> { price.getValue(it) }.thenComparator { a, b -> a.compareTo(b) })
        val first = LinkedHashMap<String, String>()
        for (key in byPrice) first.getOrPut(key.take(7)) { key }
        val ordered = first.values + byPrice.filter { first[it.take(7)] != it }
        val keys = ordered.take(ROUND_TRIP_CHECKS).filter { it !in job.roundTrips }
        val base = job.completed
        job.total = base + keys.size
        for ((number, key) in keys.withIndex()) {
            val (origin, destination, out, back) = key.split("|")
            if (!begin(job, "Confronto con biglietto A/R: $origin → $destination · $out")) return false
            live.roundTrip(origin, destination, out, back, request)?.let { job.roundTrips[key] = it }
            job.completed = base + number + 1
            job.etaSeconds = keys.size - number - 1
            progress(job)
        }
        return true
    }

    private suspend fun begin(job: Job, label: String): Boolean {
        // Let a stop, a recheck or a screen refresh run between two requests.
        yield()
        if (job.status == "cancelling") return false
        job.current = label
        progress(job)
        return true
    }

    private suspend fun advance(job: Job, schedule: Schedule, pace: Pace) {
        job.requests += 1
        pace.requests += 1
        val measured = (clock.now() - pace.started) / pace.requests
        val seconds = if (pace.requests >= PACE_SAMPLE) measured else secondsPerSearch(schedule.request, live.requestInterval)
        val share = if (job.returnDays != 0 && schedule.backBound != 0) job.returnRequests.toDouble() / (job.returnDays * schedule.backBound) else 1.0
        job.etaSeconds = kotlin.math.round(schedule.remaining(job.cursor, share) * seconds + pace.later).toInt()
        progress(job)
    }

    private fun keepOutbound(job: Job, request: FlexibleRequest, day: String, found: Map<Route, RouteResult>) {
        for ((pair, result) in found) {
            val (origin, destination) = pair
            job.unverified += result.unverified
            job.emptySearches += if (result.empty) 1 else 0
            val outbound = result.quotes.filter { matches(it, request, origin, destination, day) && it.price <= request.budget }
            if (request.tripType == "one_way") {
                for (quote in outbound) singleTrip(quote, request)?.let { job.results[it.id] = it }
            } else if (outbound.isNotEmpty()) {
                job.outbound["$origin|$destination|$day"] = outbound
            }
        }
    }

    private fun wantedReturns(job: Job, request: FlexibleRequest, schedule: Schedule, day: String): LinkedHashMap<Route, MutableList<Quote>> {
        val wanted = LinkedHashMap<Route, MutableList<Quote>>()
        for (out in schedule.outsFor[day] ?: emptyList()) {
            for ((origin, destination) in schedule.routes) {
                val outbound = job.outbound["$origin|$destination|$out"] ?: continue
                for (route in returnRoutes(request, origin, destination)) wanted.getOrPut(route) { ArrayList() }.addAll(outbound)
            }
        }
        return wanted
    }

    private fun combineReturns(job: Job, request: FlexibleRequest, day: String, found: Map<Route, RouteResult>, wanted: Map<Route, List<Quote>>) {
        for ((pair, result) in found) {
            val (away, home) = pair
            job.unverified += result.unverified
            job.emptySearches += if (result.empty) 1 else 0
            val inbound = result.quotes.filter { matches(it, request, away, home, day, inbound = true) }
            for (a in wanted[pair] ?: emptyList()) for (b in inbound) combine(a, b, request)?.let { job.results[it.id] = it }
        }
    }

    private suspend fun progress(job: Job) {
        // Never spend more than about a fifth of the time saving.
        if (clock.now() - savedAt < maxOf(SAVE_INTERVAL, 5 * saveCost)) return
        val started = clock.now()
        job.updatedAt = Calendar.utcNow()
        if (job.status == "cancelling") return
        save(job)
        savedAt = clock.now()
        saveCost = savedAt - started
    }

    /** The job as the results screen shows it: a page of trips, filters, groups, suggestions. */
    suspend fun view(id: String, sort: String = "price", offset: Int = 0, filters: ResultFilters = ResultFilters(), focus: String = ""): JobView = confined {
        val job = load(id).copyDeep()
        if (job.running && activeId != id) {
            job.status = "interrupted"
            job.issues = listOf("App chiusa durante la ricerca: premi Riprendi per continuare.")
        }
        val all = currentResults(job, clock)
        val browse = browseResults(all, sort, offset, filters, focus)
        val page = browse.copy(results = browse.results.map { it.copy(roundTripUrl = roundTripUrl(it, job.request)) })
        JobView(job, page, resultAirports(job.request, all))
    }

    /** Filtered trips, cheapest first, for sharing. No provider calls. */
    suspend fun export(id: String, filters: ResultFilters = ResultFilters()): Pair<FlexibleRequest, List<Trip>> = confined {
        val job = load(id)
        job.request to currentResults(job, clock).filter(filters::matches).sortedWith(sortComparator("price"))
    }

    suspend fun trip(id: String, tripId: String): Trip? = confined {
        val job = load(id)
        currentResults(job, clock).firstOrNull { it.id == tripId }?.let { it.copy(roundTripUrl = roundTripUrl(it, job.request)) }
    }

    suspend fun recheck(id: String, resultId: String): Recheck {
        val (request, pair) = confined {
            val job = load(id)
            val original = job.results[resultId] ?: throw NoSuchElementException("Ricerca o risultato non trovato.")
            val previous = job.rechecks[resultId]
            job.request to (if (previous?.status == "confirmed") previous.result!! else original)
        }
        val selected = ArrayList<Quote>()
        for ((inbound, quote) in listOf(false to pair.outbound, true to pair.inbound)) {
            if (quote == null) continue
            val origin = quote.first.fromCode
            val destination = quote.last.toCode
            val day = quote.first.departure.take(10)
            val result = live.search(origin, destination, day, request, inbound, fresh = true)
            val match = result.quotes.firstOrNull {
                fingerprint(it) == fingerprint(quote) && matches(it, request, origin, destination, day, inbound)
            } ?: return storeRecheck(
                id, resultId,
                Recheck("not_confirmed", message = "Gli stessi voli non sono stati confermati con questi filtri. Il risultato precedente non è più valido."),
            )
            selected.add(match)
        }
        val updated = (if (request.tripType == "one_way") singleTrip(selected[0], request) else combine(selected[0], selected[1], request))
            ?: return storeRecheck(id, resultId, Recheck("outside_filters", message = "Il prezzo o gli orari non rispettano più i filtri."))
        return storeRecheck(id, resultId, Recheck("confirmed", result = updated, priceChanged = updated.price != pair.price))
    }

    private suspend fun storeRecheck(id: String, resultId: String, check: Recheck): Recheck = confined {
        val job = load(id)
        job.rechecks[resultId] = check
        job.updatedAt = Calendar.utcNow()
        save(job)
        check
    }

    companion object {
        /** Route and dates of a trip that one return ticket could cover, else null. */
        fun roundTripKey(trip: Trip): String? {
            val out = trip.outbound.segments
            val back = trip.inbound?.segments ?: return null
            val origin = out.first().fromCode
            val destination = out.last().toCode
            if (back.first().fromCode != destination || back.last().toCode != origin) return null
            return "$origin|$destination|${out.first().departure.take(10)}|${back.first().departure.take(10)}"
        }

        /** Saved trips with rechecks applied and freshness marked. */
        fun currentResults(job: Job, clock: EngineClock = RealClock): List<Trip> {
            val results = LinkedHashMap(job.results)
            for ((id, check) in job.rechecks) {
                if (check.status == "confirmed") results[id] = check.result!! else results.remove(id)
            }
            val now = clock.now()
            return results.values.map { item ->
                val age = now - (Calendar.instantMs(item.checkedAt) ?: 0L) / 1000.0
                val fare = job.roundTrips[roundTripKey(item) ?: ""]?.get(item.id.take(24))
                item.copy(needsRecheck = age > QUOTE_TTL, roundTripFrom = fare?.takeIf { it < item.price })
            }
        }
    }
}

/** An independent copy: the view never shares mutable maps with the worker. */
fun Job.copyDeep(): Job = copy(
    outbound = LinkedHashMap(outbound), results = LinkedHashMap(results), rechecks = LinkedHashMap(rechecks),
    roundTrips = LinkedHashMap(roundTrips), cursor = cursor.toList(), issues = issues.toList(),
)
