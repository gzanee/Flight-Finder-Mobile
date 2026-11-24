package it.gzanee.flightfinder.data.repository

import it.gzanee.flightfinder.data.google.GoogleConsent
import it.gzanee.flightfinder.data.google.GoogleFlightsClient
import it.gzanee.flightfinder.data.google.GoogleHttp
import it.gzanee.flightfinder.data.google.GooglePageDecoder
import it.gzanee.flightfinder.data.google.GoogleQuery
import it.gzanee.flightfinder.data.google.ConsentStore
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.RecheckResult
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.model.TripType
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.repository.ConnectionTest
import it.gzanee.flightfinder.domain.repository.PlannedSearch
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.domain.repository.SearchStore
import it.gzanee.flightfinder.domain.search.SearchRunner
import it.gzanee.flightfinder.domain.search.SearchView
import it.gzanee.flightfinder.domain.search.buildPlan
import it.gzanee.flightfinder.domain.search.validated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.Locale

/** Le ricerche sul telefono: motore, Google e salvataggio messi insieme. */
class SearchRepositoryImpl(
    private val airports: AirportRepository,
    private val store: SearchStore,
    private val http: GoogleHttp,
    private val consentStore: ConsentStore,
    private val scope: CoroutineScope,
) : SearchRepository {
    private class Engine(val client: GoogleFlightsClient, val runner: SearchRunner, val decoder: GooglePageDecoder)

    private val mutex = Mutex()
    private var engine: Engine? = null
    private val _updates = MutableSharedFlow<SearchJob>(extraBufferCapacity = 64)

    override val updates: Flow<SearchJob> = _updates

    override val activeId: String? get() = engine?.runner?.activeId

    /** Il motore nasce al primo uso, quando il catalogo aeroporti è caricato. */
    private suspend fun engine(): Engine = mutex.withLock {
        engine ?: run {
            val catalog = airports.catalog()
            val decoder = GooglePageDecoder(catalog)
            val client = GoogleFlightsClient(http, decoder, GoogleConsent(http, consentStore))
            val runner = SearchRunner(store, client, catalog, scope)
            scope.launch { runner.updates.collect(_updates::emit) }
            Engine(client, runner, decoder)
        }.also { engine = it }
    }

    override suspend fun plan(request: SearchRequest): PlannedSearch {
        val catalog = airports.catalog()
        val today = LocalDate.now()
        val valid = request.validated(catalog, today)
        val plan = buildPlan(valid, catalog.candidateRoutes(valid), catalog, today)
        val seconds = plan.searchesUpperBound * engine().client.secondsPerSearch(valid)
        return PlannedSearch(valid, plan, (seconds / 60).toInt())
    }

    override suspend fun start(search: PlannedSearch) = engine().runner.start(search.request, search.plan)
    override suspend fun resume(id: String) = engine().runner.resume(id)
    override suspend fun cancel(id: String) = engine().runner.cancel(id)
    override suspend fun remove(id: String) = engine().runner.remove(id)
    override suspend fun all(): List<SearchJob> = engine().runner.all()
    override suspend fun trip(id: String, tripId: String): Trip? = engine().runner.trip(id, tripId)
    override suspend fun recheck(id: String, tripId: String): RecheckResult = engine().runner.recheck(id, tripId)

    override suspend fun view(id: String, sort: SortOrder, offset: Int, filters: ResultFilters, focus: String?): SearchView =
        engine().runner.view(id, sort, offset, filters, focus)

    /** Una sola ricerca vera, Venezia → Barcellona tra 30 giorni, per vedere se Google risponde. */
    override suspend fun testConnection(): ConnectionTest {
        val e = engine()
        val day = LocalDate.now().plusDays(30)
        val lines = ArrayList<String>()
        return try {
            val request = SearchRequest(
                origins = listOf("VCE"), destinations = listOf("BCN"), startDate = day, endDate = day,
                tripType = TripType.ONE_WAY, dateMode = DateMode.FIXED, departureMin = java.time.LocalTime.MIDNIGHT, maxStops = 1,
            )
            val query = GoogleQuery.oneWay(listOf("VCE"), listOf("BCN"), day, request)
            val started = System.currentTimeMillis()
            val html = e.client.fetchPage(query)
            lines += "Risposta in ${System.currentTimeMillis() - started} ms, ${html.length / 1024} kB."
            val page = e.decoder.decode(html, request, query.bookingUrl, listOf("VCE") to listOf("BCN"))
            lines += "Venezia → Barcellona, $day: ${page.quotes.size} voli letti, ${page.rejected} righe non verificabili."
            page.quotes.take(5).forEach { q ->
                lines += "${q.first.airlineCode} ${q.first.flightNumber} alle ${q.first.departure.toLocalTime()}: ${"%.2f".format(Locale.ITALY, q.price)} €"
            }
            ConnectionTest(true, lines)
        } catch (ex: Exception) {
            lines += "${ex::class.simpleName}: ${ex.message}"
            ConnectionTest(false, lines)
        }
    }
}

