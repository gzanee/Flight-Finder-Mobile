package it.gzanee.flightfinder.data

import it.gzanee.flightfinder.FakeClock
import it.gzanee.flightfinder.FakeGoogle
import it.gzanee.flightfinder.Fixtures
import it.gzanee.flightfinder.InMemorySearchStore
import it.gzanee.flightfinder.data.google.GoogleBlockedException
import it.gzanee.flightfinder.data.google.GoogleConsent
import it.gzanee.flightfinder.data.google.GoogleFlightsClient
import it.gzanee.flightfinder.data.google.GooglePageDecoder
import it.gzanee.flightfinder.data.google.GoogleUnavailableException
import it.gzanee.flightfinder.data.google.HttpPage
import it.gzanee.flightfinder.data.google.InMemoryConsentStore
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.domain.model.RecheckResult
import it.gzanee.flightfinder.domain.search.SearchRunner
import it.gzanee.flightfinder.domain.search.QuoteSource
import it.gzanee.flightfinder.domain.search.QuotesUnavailableException
import it.gzanee.flightfinder.domain.search.RouteQuotes
import it.gzanee.flightfinder.domain.search.Route
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.search.buildPlan
import it.gzanee.flightfinder.domain.search.validated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/** Rete: pause, cache, blocchi, nuovi tentativi, consenso; e Ferma/Riprendi/Aggiorna prezzo. */
class GoogleFlightsClientTest {
    private val catalog = Fixtures.catalog
    private val golden = Fixtures.json("jobs.json").jsonObject
    private val today = LocalDate.parse(golden.getValue("today").jsonPrimitive.content)
    private val scenario = golden.getValue("scenarios").jsonObject.getValue("weekend").jsonObject
    private val served = scenario.getValue("served").jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val request = Fixtures.request(scenario.getValue("spec").jsonObject)!!.validated(catalog, today)
    private val plan = buildPlan(request, catalog.candidateRoutes(request), catalog, today)

    private fun ok(url: String) = HttpPage(200, url, served[FakeGoogle.tfsOf(url)] ?: served.values.first())

    private fun client(google: FakeGoogle, clock: FakeClock = FakeClock()) =
        GoogleFlightsClient(google, GooglePageDecoder(catalog), GoogleConsent(google, InMemoryConsentStore()), clock)

    private suspend fun GoogleFlightsClient.searchDay(index: Int = 0, fresh: Boolean = false) =
        searchMany(request.origins, plan.destinations, plan.datePairs[index].departure, request, fresh = fresh)

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("Attesa ${T::class.simpleName}")
        throw IllegalStateException()
    }

    @Test
    fun mandaIlConsensoEFaUnaPausa() = runBlocking {
        val clock = FakeClock()
        val google = FakeGoogle { url, _, _ -> ok(url) }
        val client = client(google, clock)
        client.searchDay(0)
        client.searchDay(1)
        assertEquals(2, google.asked.size)
        assertEquals("SOCS=${GoogleConsent.DEFAULT_SOCS}", google.asked[0].second["Cookie"])
        assertEquals(1, clock.sleeps.size)
        assertEquals(0.3, clock.sleeps[0], 0.02)
    }

    @Test
    fun cacheDiCinqueMinuti() = runBlocking {
        val clock = FakeClock()
        val google = FakeGoogle { url, _, _ -> ok(url) }
        val client = client(google, clock)
        val first = client.searchDay()
        assertEquals(first, client.searchDay())
        assertEquals(1, google.asked.size)
        client.searchDay(fresh = true)
        clock.t += 301
        client.searchDay()
        assertEquals(3, google.asked.size)
    }

    @Test
    fun unBloccoFermaSenzaNuoviTentativi() = runBlocking {
        for (page in listOf(
            HttpPage(429, "https://www.google.com/sorry/index", ""),
            HttpPage(403, "https://www.google.com/travel/flights", ""),
            HttpPage(200, "https://www.google.com/sorry/index?continue=x", "<html></html>"),
            HttpPage(200, "https://www.google.com/x", "Our systems have detected unusual traffic"),
        )) {
            val google = FakeGoogle { _, _, _ -> page }
            val client = client(google)
            repeat(2) { assertThrows<GoogleBlockedException> { runBlocking { client.searchDay() } } }
            assertEquals(1, google.asked.size)
        }
    }

    @Test
    fun erroreTemporaneoRiprovatoConPauseCrescenti() = runBlocking {
        val clock = FakeClock()
        val error = "<script class=\"ds:1\">AF_initDataCallback({key: 'ds:1', data:[13,null,[[\"type.googleapis.com/travel.frontend.flights.ErrorResponse\"]]],x:1});</script>"
        val google = FakeGoogle { url, _, n -> if (n <= 2) HttpPage(200, url, error) else ok(url) }
        val found = client(google, clock).searchDay()
        assertTrue(found.values.any { it.quotes.isNotEmpty() })
        assertEquals(listOf(2.0, 4.0), clock.sleeps.filter { it >= 1 })
    }

    @Test
    fun erroriDiReteDopoTreTentativi() = runBlocking {
        val google = FakeGoogle { _, _, _ -> throw java.io.IOException("offline") }
        val e = assertThrows<GoogleUnavailableException> { runBlocking { client(google).searchDay() } }
        assertTrue(e.message!!.startsWith("Connessione a Google non riuscita"))
        // Come nel portale: tre tentativi sul gruppo, poi tre sulla sua prima tratta.
        assertEquals(6, google.asked.size)
    }

    @Test
    fun rinnovaIlConsensoUnaVolta() = runBlocking {
        val page = "<html><a href=\"https://consent.google.com/\">x</a><form action=\"https://consent.google.com/save\"><input type=\"hidden\" name=\"bl\" value=\"boq_1\"><input type=\"hidden\" name=\"set_eom\" value=\"true\"></form>"
        val google = FakeGoogle { url, headers, _ ->
            when {
                "hl=en&gl=IT" in url -> HttpPage(200, "https://consent.google.com/m?x", page)
                "fresh-socs" !in headers.getValue("Cookie") -> HttpPage(200, "https://consent.google.com/m", page)
                else -> ok(url)
            }
        }
        client(google).searchDay()
        assertEquals(listOf(mapOf("bl" to "boq_1", "set_eom" to "true")), google.posts)
        assertEquals("SOCS=fresh-socs", google.asked.last().second["Cookie"])
    }

    @Test
    fun gruppoSenzaRispostaDivisoInTratte() = runBlocking {
        val google = FakeGoogle { url, _, _ -> HttpPage(200, url, if (FakeGoogle.tfsOf(url) in served) "<html>no data</html>" else served.values.first()) }
        val found = client(google).searchDay()
        val pairs = request.origins.size * plan.destinations.size
        assertEquals(1 + pairs, google.asked.size)
        assertEquals(pairs, found.size)
    }

    @Test
    fun fermaERiprendiStessiRisultati() = runBlocking {
        var runner: SearchRunner? = null
        var id = ""
        var stopAt = 4
        val google = FakeGoogle { url, _, n ->
            // Come un tocco su "Ferma" mentre la richiesta è in volo.
            if (n == stopAt) CoroutineScope(Dispatchers.Default).launch { runner!!.cancel(id) }
            ok(url)
        }
        val clock = FakeClock()
        runner = SearchRunner(InMemorySearchStore(), client(google, clock), catalog, CoroutineScope(SupervisorJob()), clock)
        id = runner.start(request, plan, today)
        runner.idle()
        assertEquals(JobStatus.CANCELLED, runner.get(id).status)
        assertTrue("fermata dopo ${google.asked.size} richieste", google.asked.size in 4..5)
        stopAt = -1
        runner.resume(id)
        runner.idle()
        val done = runner.get(id)
        assertEquals(JobStatus.COMPLETE, done.status)
        assertEquals(scenario.getValue("job").jsonObject.getValue("results").jsonObject.keys, done.results.keys)
    }

    @Test
    fun bloccoSalvatoERicercaLasciataAMetaInterrotta() = runBlocking {
        val clock = FakeClock()
        val store = InMemorySearchStore()
        val google = FakeGoogle { url, _, n -> if (n == 3) HttpPage(429, url, "") else ok(url) }
        val runner = SearchRunner(store, client(google, clock), catalog, CoroutineScope(SupervisorJob()), clock)
        val id = runner.start(request, plan, today)
        runner.idle()
        val job = runner.get(id)
        assertEquals(JobStatus.BLOCKED, job.status)
        assertTrue(job.issues.single().startsWith("Google ha limitato"))
        store.jobs[id] = job.copy(status = JobStatus.RUNNING)
        val afterRestart = SearchRunner(store, client(google, clock), catalog, CoroutineScope(SupervisorJob()), clock)
        assertEquals(JobStatus.INTERRUPTED, afterRestart.view(id).job.status)
    }

    @Test
    fun aggiornaPrezzoTogliIlViaggioSparito() = runBlocking {
        val clock = FakeClock()
        val store = InMemorySearchStore()
        val runner = SearchRunner(store, client(FakeGoogle { url, _, _ -> ok(url) }, clock), catalog, CoroutineScope(SupervisorJob()), clock)
        val id = runner.start(request, plan, today)
        runner.idle()
        val first = runner.view(id).page.results.first()
        val empty = "<script class=\"ds:1\">AF_initDataCallback({data:[null,null,[null],[null]],sideChannel:{}});</script>"
        val gone = SearchRunner(store, client(FakeGoogle { url, _, _ -> HttpPage(200, url, empty) }, clock), catalog, CoroutineScope(SupervisorJob()), clock)
        assertTrue(gone.recheck(id, first.id) is RecheckResult.NotConfirmed)
        val view = gone.view(id)
        assertTrue(view.page.results.none { it.id == first.id })
        assertEquals(runner.get(id).results.size, view.page.resultCount + 1)
    }

    // ---- Sul telefono: pagine senza dati ritentate, ricerche illeggibili saltate (il portale invece si ferma).

    private val noData = "<html><body>nessun dato</body></html>"

    @Test
    fun trattaSenzaDatiRiprovataDopoIlConsenso() = runBlocking {
        val clock = FakeClock()
        // 1: il gruppo senza dati si divide; 2: la prima tratta senza dati si ritenta dopo il consenso e una pausa.
        val google = FakeGoogle { url, _, n -> if (n <= 2) HttpPage(200, url, noData) else ok(url) }
        val found = client(google, clock).searchDay()
        assertTrue(found.values.any { it.quotes.isNotEmpty() })
        assertTrue(3.0 in clock.sleeps)
        assertTrue(google.asked.any { "hl=en&gl=IT" in it.first })
    }

    /** Una fonte di prezzi che non risponde alle chiamate indicate (contate da 1). */
    private class Flaky(private val inner: QuoteSource, private val failing: IntRange) : QuoteSource by inner {
        private var calls = 0
        override suspend fun searchMany(origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest, inbound: Boolean, fresh: Boolean): Map<Route, RouteQuotes> {
            calls++
            if (calls in failing) throw QuotesUnavailableException("Pagina Google senza dati voli: consenso, blocco o formato cambiato.")
            return inner.searchMany(origins, destinations, day, request, inbound, fresh)
        }
    }

    private fun runWith(failing: IntRange): it.gzanee.flightfinder.domain.model.SearchJob = runBlocking {
        val source = Flaky(client(FakeGoogle.serving(served)), failing)
        val runner = SearchRunner(InMemorySearchStore(), source, catalog, CoroutineScope(SupervisorJob()), FakeClock())
        val id = runner.start(request, plan, today)
        runner.idle()
        runner.get(id)
    }

    @Test
    fun unaRicercaIllegibileSiSaltaELaRicercaContinua() {
        val job = runWith(2..2)
        assertEquals(JobStatus.COMPLETE, job.status)
        assertTrue(job.results.isNotEmpty())
        assertEquals(listOf("Ricerche saltate perché Google non ha risposto: 1. Qualche volo potrebbe mancare."), job.issues)
    }

    @Test
    fun troppiFallimentiDiFilaFermanoLaRicerca() {
        val job = runWith(1..SearchRunner.MAX_FAILURES_IN_ROW)
        assertEquals(JobStatus.INTERRUPTED, job.status)
        assertEquals(listOf("Pagina Google senza dati voli: consenso, blocco o formato cambiato."), job.issues)
    }
}
