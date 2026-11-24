package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.Cursor
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.domain.model.Plan
import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.RecheckResult
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.ResultPage
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.model.TripType
import it.gzanee.flightfinder.domain.repository.SearchStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Una ricerca vista dalla schermata dei risultati. */
data class SearchView(val job: SearchJob, val page: ResultPage)

/**
 * Le ricerche sul telefono: port di flightcore/flexible_jobs.py. L'avanzamento si
 * salva e si riprende; un blocco di Google ferma la ricerca; nessuna disponibilità
 * viene inventata. Lo stato di lavoro vive in un unico dispatcher seriale: fuori
 * escono solo copie immutabili ([SearchJob]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchRunner(
    private val store: SearchStore,
    private val quotes: QuoteSource,
    private val catalog: AirportCatalog,
    scope: CoroutineScope,
    private val clock: EngineClock = EngineClock.System,
) {
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(scope.coroutineContext + serial)
    private var work: Work? = null
    private var worker: Job? = null
    private var savedAt = 0.0
    private var saveCost = 0.0

    private val _updates = MutableSharedFlow<SearchJob>(extraBufferCapacity = 64)

    /** Ogni stato salvato di una ricerca, per aggiornare le schermate. */
    val updates: SharedFlow<SearchJob> = _updates.asSharedFlow()

    val isRunning get() = worker?.isActive == true

    /** La ricerca in corso, se c'è. */
    val activeId get() = if (isRunning) work?.id else null

    private suspend fun <T> confined(block: suspend () -> T): T = withContext(serial) { block() }

    private suspend fun load(id: String): SearchJob =
        work?.takeIf { it.id == id }?.snapshot() ?: store.load(id) ?: throw NoSuchElementException("Ricerca o risultato non trovato.")

    private suspend fun save(job: SearchJob) {
        store.save(job)
        _updates.emit(job)
    }

    suspend fun get(id: String): SearchJob = confined { load(id) }

    suspend fun all(): List<SearchJob> = confined {
        store.list().map { saved -> work?.takeIf { it.id == saved.id }?.snapshot() ?: saved }.sortedByDescending { it.createdAt }
    }

    suspend fun start(request: SearchRequest, plan: Plan, today: LocalDate = LocalDate.now()): String = confined {
        check(!isRunning) { "Una ricerca è già in corso: fermala prima di avviarne un altra." }
        val now = Instant.now()
        val job = SearchJob(
            id = UUID.randomUUID().toString().replace("-", ""), request = request, plan = plan, status = JobStatus.RUNNING,
            total = plan.searchesUpperBound, createdAt = now, updatedAt = now, today = today,
        )
        save(job)
        launch(job)
        job.id
    }

    suspend fun resume(id: String) = confined {
        check(!isRunning) { "Attendi che la ricerca attiva si fermi." }
        val job = load(id)
        check(job.status != JobStatus.COMPLETE) { "Ricerca già completata. Avviane una nuova per prezzi aggiornati." }
        val resumed = job.copy(status = JobStatus.RUNNING, issues = emptyList())
        save(resumed)
        launch(resumed)
    }

    suspend fun cancel(id: String) = confined {
        val active = work?.takeIf { it.id == id && isRunning }
        if (active != null) {
            if (active.status == JobStatus.RUNNING) active.status = JobStatus.CANCELLING
        } else {
            val job = load(id)
            if (job.status == JobStatus.RUNNING) save(job.copy(status = JobStatus.CANCELLING))
        }
    }

    suspend fun remove(id: String) = confined {
        check(activeId != id) { "Ferma la ricerca prima di eliminarla." }
        if (work?.id == id) work = null
        store.remove(id)
    }

    /** Aspetta che la ricerca in corso, se c'è, si fermi e venga salvata. */
    suspend fun idle() {
        worker?.join()
    }

    private fun launch(job: SearchJob) {
        val w = Work(job)
        work = w
        worker = scope.launch { run(w) }
    }

    // ---------------- Il lavoro ----------------

    /** Lo stato mutabile di una ricerca mentre gira; [snapshot] ne fa una copia immutabile. */
    private class Work(job: SearchJob) {
        val id = job.id
        val base = job
        var status = job.status
        var completed = job.completed
        var total = job.total
        var cursor = job.cursor
        var pass = job.pass
        val outbound = LinkedHashMap(job.outbound)
        var requests = job.requests
        var returnDays = job.returnDays
        var returnRequests = job.returnRequests
        var etaSeconds = job.etaSeconds
        val results = LinkedHashMap(job.results)
        val roundTrips = LinkedHashMap(job.roundTrips)
        var unverified = job.unverified
        var emptySearches = job.emptySearches
        var issues = job.issues
        var current = job.current
        var updatedAt = job.updatedAt

        fun snapshot() = base.copy(
            status = status, completed = completed, total = total, cursor = cursor, pass = pass, outbound = LinkedHashMap(outbound),
            requests = requests, returnDays = returnDays, returnRequests = returnRequests, etaSeconds = etaSeconds,
            results = LinkedHashMap(results), roundTrips = LinkedHashMap(roundTrips), unverified = unverified,
            emptySearches = emptySearches, issues = issues, current = current, updatedAt = updatedAt,
        )
    }

    private class Pace(val started: Double) {
        var requests = 0
        var later = 0.0
        var done = 0
        var label = ""
    }

    private enum class Outcome { COMPLETE, CANCELLED }

    private suspend fun run(w: Work) {
        val request = w.base.request
        val passes = searchPasses(request, w.base.plan.destinations, w.base.today)
        w.total = passes.sumOf { it.total }
        savedAt = 0.0
        saveCost = 0.0
        val pace = Pace(clock.now())
        var status = JobStatus.COMPLETE
        try {
            for (number in w.pass until passes.size) {
                pace.later = passes.drop(number + 1).sumOf { it.total * quotes.secondsPerSearch(it.request) }
                pace.done = passes.take(number).sumOf { it.total }
                pace.label = if (passes.size > 1) listOf("Voli diretti · ", "Con scalo · ")[number] else ""
                if (search(w, request, passes[number], pace) == Outcome.CANCELLED) {
                    status = JobStatus.CANCELLED
                    break
                }
                w.pass = number + 1
                w.cursor = Cursor()
                w.outbound.clear()
                w.returnDays = 0
                w.returnRequests = 0
            }
            if (status == JobStatus.COMPLETE && !compareRoundTrips(w, request)) status = JobStatus.CANCELLED
            val last = passes.last()
            if (status == JobStatus.COMPLETE && last.unsoldFrom != null) {
                w.issues = listOf(
                    "Google vende i voli fino al ${last.horizon.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}: " +
                        "le date successive non sono state cercate.",
                )
            }
        } catch (e: CancellationException) {
            status = JobStatus.INTERRUPTED
            w.issues = listOf("Ricerca interrotta; il progresso è salvato.")
        } catch (e: QuotesBlockedException) {
            status = JobStatus.BLOCKED
            w.issues = listOfNotNull(e.message)
        } catch (e: Exception) {
            status = JobStatus.INTERRUPTED
            w.issues = listOf(if (e is QuotesUnavailableException) e.message.orEmpty() else "Ricerca interrotta da un errore; il progresso è salvato.")
        } finally {
            // Uno stop chiesto nel frattempo vince, come nel portale.
            w.status = if (w.status == JobStatus.CANCELLING) JobStatus.CANCELLED else status
            w.updatedAt = Instant.now()
            if (w.status == JobStatus.COMPLETE) w.etaSeconds = 0
            withContext(kotlinx.coroutines.NonCancellable) { save(w.snapshot()) }
        }
    }

    /**
     * Una passata dal cursore salvato (giorno, fase, gruppo): la fase 0 cerca le andate
     * del giorno, la fase 1 i ritorni che le andate trovate rendono utili. Si cerca con la
     * richiesta della passata, si giudica con quella dell'utente.
     */
    private suspend fun search(w: Work, request: SearchRequest, schedule: Schedule, pace: Pace): Outcome {
        var stage = w.cursor.stage
        var batch = w.cursor.batch
        for (index in w.cursor.day until schedule.days.size) {
            val day = schedule.days[index]
            val base = pace.done + schedule.unitsBefore(index)
            val outs = if (day in schedule.outDays) schedule.outBatches.size else 0
            if (stage == 0) {
                for (number in batch until outs) {
                    val (origins, destinations) = schedule.outBatches[number].let { it.from to it.to }
                    if (!begin(w, pace.label + "${airportList(origins)} → ${airportList(destinations)} · $day")) return Outcome.CANCELLED
                    keepOutbound(w, request, day, quotes.searchMany(origins, destinations, day, schedule.request))
                    w.cursor = Cursor(index, 0, number + 1)
                    w.completed = base + number + 1
                    advance(w, schedule, pace)
                }
                stage = 1
                batch = 0
            }
            if (day in schedule.outsFor) {
                val wanted = wantedReturns(w, request, schedule, day)
                val batches = schedule.returnBatches(wanted.keys)
                for (number in batch until batches.size) {
                    val (aways, homes) = batches[number].let { it.from to it.to }
                    if (!begin(w, pace.label + "Ritorno: ${airportList(aways)} → ${airportList(homes)} · $day")) return Outcome.CANCELLED
                    combineReturns(w, request, day, quotes.searchMany(aways, homes, day, schedule.request, inbound = true), wanted)
                    w.cursor = Cursor(index, 1, number + 1)
                    w.completed = base + outs + minOf(number + 1, schedule.backBound)
                    w.returnRequests += 1
                    advance(w, schedule, pace)
                }
                w.returnDays += 1
            }
            // Tutti i giorni di ritorno di queste andate sono stati cercati.
            w.outbound.keys.removeAll { key -> (schedule.lastBack[LocalDate.parse(key.takeLast(10))] ?: LocalDate.MIN) <= day }
            stage = 0
            batch = 0
            w.cursor = Cursor(index + 1, 0, 0)
            w.completed = pace.done + schedule.unitsBefore(index + 1)
        }
        return Outcome.COMPLETE
    }

    /**
     * Confronto con il biglietto A/R unico di Google: una richiesta per tratta e coppia
     * di date, prima le date più economiche di ogni tratta. False se la ricerca è stata fermata.
     */
    private suspend fun compareRoundTrips(w: Work, request: SearchRequest): Boolean {
        val price = LinkedHashMap<String, Double>()
        for (trip in w.results.values) {
            val key = roundTripKey(trip) ?: continue
            price[key] = minOf(trip.price, price[key] ?: trip.price)
        }
        val byPrice = price.keys.sortedWith(compareBy({ price.getValue(it) }, { it }))
        val first = LinkedHashMap<String, String>()
        for (key in byPrice) first.getOrPut(key.take(7)) { key }
        val ordered = first.values + byPrice.filter { first[it.take(7)] != it }
        val keys = ordered.take(ROUND_TRIP_CHECKS).filter { it !in w.roundTrips }
        val base = w.completed
        w.total = base + keys.size
        for ((number, key) in keys.withIndex()) {
            val (origin, destination, out, back) = key.split("|")
            if (!begin(w, "Confronto con biglietto A/R: $origin → $destination · $out")) return false
            quotes.roundTripFares(origin, destination, LocalDate.parse(out), LocalDate.parse(back), request)?.let { w.roundTrips[key] = it }
            w.completed = base + number + 1
            w.etaSeconds = keys.size - number - 1
            progress(w)
        }
        return true
    }

    private suspend fun begin(w: Work, label: String): Boolean {
        // Lascia passare uno stop, un ricontrollo o un aggiornamento dello schermo fra due richieste.
        yield()
        if (w.status == JobStatus.CANCELLING) return false
        w.current = label
        progress(w)
        return true
    }

    private suspend fun advance(w: Work, schedule: Schedule, pace: Pace) {
        w.requests += 1
        pace.requests += 1
        val measured = (clock.now() - pace.started) / pace.requests
        val seconds = if (pace.requests >= PACE_SAMPLE) measured else quotes.secondsPerSearch(schedule.request)
        val share = if (w.returnDays != 0 && schedule.backBound != 0) w.returnRequests.toDouble() / (w.returnDays * schedule.backBound) else 1.0
        val left = schedule.remaining(w.cursor.day, w.cursor.stage, w.cursor.batch, share)
        w.etaSeconds = Math.round(left * seconds + pace.later).toInt()
        progress(w)
    }

    private fun keepOutbound(w: Work, request: SearchRequest, day: LocalDate, found: Map<Route, RouteQuotes>) {
        for ((route, result) in found) {
            val (origin, destination) = route
            w.unverified += result.unverified
            if (result.empty) w.emptySearches += 1
            val outbound = result.quotes.filter { it.matches(request, catalog, origin, destination, day) && it.price <= request.budget }
            if (request.tripType == TripType.ONE_WAY) {
                outbound.mapNotNull { singleTrip(it, request) }.forEach { w.results[it.id] = it }
            } else if (outbound.isNotEmpty()) {
                w.outbound["$origin|$destination|$day"] = outbound
            }
        }
    }

    /** Ritorni da cercare nel giorno, ognuno con le andate che completa. */
    private fun wantedReturns(w: Work, request: SearchRequest, schedule: Schedule, day: LocalDate): Map<Route, List<Quote>> {
        val wanted = LinkedHashMap<Route, MutableList<Quote>>()
        for (out in schedule.outsFor[day].orEmpty()) {
            for ((origin, destination) in schedule.routes) {
                val outbound = w.outbound["$origin|$destination|$out"] ?: continue
                for (route in request.returnRoutes(origin, destination)) wanted.getOrPut(route) { ArrayList() } += outbound
            }
        }
        return wanted
    }

    private fun combineReturns(w: Work, request: SearchRequest, day: LocalDate, found: Map<Route, RouteQuotes>, wanted: Map<Route, List<Quote>>) {
        for ((route, result) in found) {
            val (away, home) = route
            w.unverified += result.unverified
            if (result.empty) w.emptySearches += 1
            val inbound = result.quotes.filter { it.matches(request, catalog, away, home, day, inbound = true) }
            for (a in wanted[route].orEmpty()) for (b in inbound) combine(a, b, request)?.let { w.results[it.id] = it }
        }
    }

    /** Salva al massimo ogni secondo, e mai più di un quinto del tempo. */
    private suspend fun progress(w: Work) {
        if (clock.now() - savedAt < maxOf(SAVE_INTERVAL, 5 * saveCost)) return
        val started = clock.now()
        w.updatedAt = Instant.now()
        if (w.status == JobStatus.CANCELLING) return
        save(w.snapshot())
        savedAt = clock.now()
        saveCost = savedAt - started
    }

    // ---------------- Risultati ----------------

    /** La ricerca come la mostra la schermata: una pagina di viaggi con filtri, gruppi e suggerimenti. */
    suspend fun view(id: String, sort: SortOrder = SortOrder.PRICE, offset: Int = 0, filters: ResultFilters = ResultFilters(), focus: String? = null): SearchView = confined {
        var job = load(id)
        if (job.status.isRunning && activeId != id) {
            job = job.copy(status = JobStatus.INTERRUPTED, issues = listOf("App chiusa durante la ricerca: premi Riprendi per continuare."))
        }
        val all = currentResults(job)
        val page = browseResults(all, catalog, sort, offset, filters, focus)
        SearchView(job, page.copy(results = page.results.map { it.copy(roundTripUrl = quotes.roundTripUrl(it, job.request)) }))
    }

    suspend fun trip(id: String, tripId: String): Trip? = confined {
        val job = load(id)
        currentResults(job).firstOrNull { it.id == tripId }?.let { it.copy(roundTripUrl = quotes.roundTripUrl(it, job.request)) }
    }

    /** "Aggiorna prezzo": cerca di nuovo gli stessi voli, senza cache. */
    suspend fun recheck(id: String, tripId: String): RecheckResult {
        val (request, trip) = confined {
            val job = load(id)
            val original = job.results[tripId] ?: throw NoSuchElementException("Ricerca o risultato non trovato.")
            job.request to ((job.rechecks[tripId] as? RecheckResult.Confirmed)?.result ?: original)
        }
        val selected = ArrayList<Quote>()
        for ((inbound, quote) in listOf(false to trip.outbound, true to trip.inbound)) {
            if (quote == null) continue
            val origin = quote.first.fromCode
            val destination = quote.last.toCode
            val day = quote.first.departure.toLocalDate()
            val result = quotes.search(origin, destination, day, request, inbound, fresh = true)
            selected += result.quotes.firstOrNull { it.fingerprint() == quote.fingerprint() && it.matches(request, catalog, origin, destination, day, inbound) }
                ?: return storeRecheck(id, tripId, RecheckResult.NotConfirmed("Gli stessi voli non sono stati confermati con questi filtri. Il risultato precedente non è più valido."))
        }
        val updated = (if (request.tripType == TripType.ONE_WAY) singleTrip(selected[0], request) else combine(selected[0], selected[1], request))
            ?: return storeRecheck(id, tripId, RecheckResult.OutsideFilters("Il prezzo o gli orari non rispettano più i filtri."))
        return storeRecheck(id, tripId, RecheckResult.Confirmed(updated, priceChanged = updated.price != trip.price))
    }

    private suspend fun storeRecheck(id: String, tripId: String, result: RecheckResult): RecheckResult = confined {
        val job = load(id)
        save(job.copy(rechecks = job.rechecks + (tripId to result), updatedAt = Instant.now()))
        result
    }

    /** I viaggi salvati con i ricontrolli applicati e la freschezza del prezzo. */
    private fun currentResults(job: SearchJob): List<Trip> {
        val results = LinkedHashMap(job.results)
        for ((id, check) in job.rechecks) {
            if (check is RecheckResult.Confirmed) results[id] = check.result else results.remove(id)
        }
        val now = clock.now()
        return results.values.map { trip ->
            val age = now - trip.checkedAt.toEpochMilli() / 1000.0
            // Un biglietto A/R unico più economico con questa andata è solo un'indicazione: il ritorno può essere un altro.
            val fare = job.roundTrips[roundTripKey(trip) ?: ""]?.get(trip.id.take(24))
            trip.copy(needsRecheck = age > QUOTE_TTL_SECONDS, roundTripFrom = fare?.takeIf { it < trip.price })
        }
    }

    companion object {
        private const val SAVE_INTERVAL = 1.0
        private const val PACE_SAMPLE = 3
        const val ROUND_TRIP_CHECKS = 150
        const val QUOTE_TTL_SECONDS = 300.0
        private const val SHOWN_AIRPORTS = 3

        fun airportList(codes: List<String>): String =
            if (codes.size <= SHOWN_AIRPORTS) codes.joinToString(", ")
            else "${codes.take(SHOWN_AIRPORTS - 1).joinToString(", ")} e altri ${codes.size - SHOWN_AIRPORTS + 1}"

        /** Tratta e date di un viaggio che un unico biglietto A/R potrebbe coprire. */
        fun roundTripKey(trip: Trip): String? {
            val back = trip.inbound ?: return null
            if (back.first.fromCode != trip.destination || back.last.toCode != trip.origin) return null
            return "${trip.origin}|${trip.destination}|${trip.outbound.first.departure.toLocalDate()}|${back.first.departure.toLocalDate()}"
        }
    }
}
