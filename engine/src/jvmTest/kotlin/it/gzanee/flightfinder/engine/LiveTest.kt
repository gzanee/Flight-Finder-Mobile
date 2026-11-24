package it.gzanee.flightfinder.engine

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class ScriptedHttp(val handler: (url: String, headers: Map<String, String>, n: Int) -> HttpResponse) : Http {
    val calls = ArrayList<Pair<String, Map<String, String>>>()
    val posts = ArrayList<Map<String, String>>()
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        calls += url to headers
        return handler(url, headers, calls.size)
    }
    override suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpResponse {
        posts += fields
        return HttpResponse(200, "https://www.google.com/", "", listOf("SOCS=fresh-socs; Domain=.google.com, NID=1; Path=/"))
    }
}

class LiveTest {
    private val golden by lazy { Fixtures.installCatalog(); Fixtures.json("jobs.json").jsonObject }
    private val today get() = golden.getValue("today").jsonPrimitive.content
    private val scenario get() = golden.getValue("scenarios").jsonObject.getValue("weekend").jsonObject
    private val served get() = scenario.getValue("served").jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val request get() = parseRequest(scenario.getValue("spec").jsonObject, today)
    private val plan get() = buildPlan(request, Airports.candidateRoutes(request), today)

    private fun tfsOf(url: String) = URLDecoder.decode(Regex("[?&]tfs=([^&]+)").find(url)!!.groupValues[1], "UTF-8")
    private fun ok(url: String) = HttpResponse(200, url, served[tfsOf(url)] ?: served.values.first())

    private suspend fun search(live: LiveQuotes, fresh: Boolean = false, day: Int = 0) =
        live.searchMany(request.origins, plan.destinations, plan.datePairs[day][0]!!, request, fresh = fresh)

    @Test
    fun sendsConsentCookieAndPausesBetweenRequests() = runTest {
        val clock = FakeClock()
        val http = ScriptedHttp { url, _, _ -> ok(url) }
        val live = LiveQuotes(http, clock = clock)
        search(live); search(live, day = 1)
        assertEquals(2, http.calls.size)
        assertEquals("SOCS=$DEFAULT_SOCS", http.calls[0].second["Cookie"])
        assertEquals(1, clock.sleeps.size)
        assertEquals(0.3, clock.sleeps[0], 0.02)
    }

    @Test
    fun repeatedSearchesComeFromTheCacheUnlessFresh() = runTest {
        val clock = FakeClock()
        val http = ScriptedHttp { url, _, _ -> ok(url) }
        val live = LiveQuotes(http, clock = clock)
        val first = search(live)
        assertEquals(first, search(live))
        assertEquals(1, http.calls.size)
        search(live, fresh = true)
        clock.t += 301
        search(live)
        assertEquals(3, http.calls.size)
    }

    @Test
    fun stopsOnABlockWithoutRetrying() = runTest {
        for (response in listOf(
            HttpResponse(429, "https://www.google.com/sorry/index", ""),
            HttpResponse(403, "https://www.google.com/travel/flights", ""),
            HttpResponse(200, "https://www.google.com/sorry/index?continue=x", "<html></html>"),
            HttpResponse(200, "https://www.google.com/x", "Our systems have detected unusual traffic"),
        )) {
            val http = ScriptedHttp { _, _, _ -> response }
            val live = LiveQuotes(http, clock = FakeClock())
            repeat(2) { assertFailsWith<LiveBlocked> { search(live) } }
            assertEquals(1, http.calls.size)
        }
    }

    @Test
    fun retriesATemporaryGoogleErrorWithGrowingPauses() = runTest {
        val clock = FakeClock()
        val error = "<script class=\"ds:1\">AF_initDataCallback({key: 'ds:1', data:[13,null,[[\"type.googleapis.com/travel.frontend.flights.ErrorResponse\"]]],x:1});</script>"
        val http = ScriptedHttp { url, _, n -> if (n <= 2) HttpResponse(200, url, error) else ok(url) }
        val found = search(LiveQuotes(http, clock = clock))
        assertTrue(found.values.any { it.quotes.isNotEmpty() })
        assertEquals(listOf(2.0, 4.0), clock.sleeps.filter { it >= 1 })
    }

    @Test
    fun givesUpAfterNetworkErrors() = runTest {
        val http = ScriptedHttp { _, _, _ -> throw java.io.IOException("offline") }
        val e = assertFailsWith<LiveUnavailable> { search(LiveQuotes(http, clock = FakeClock())) }
        assertTrue("Connessione a Google non riuscita" in e.message!!)
        // As in Python: three tries on the group, then three on its first route.
        assertEquals(6, http.calls.size)
    }

    @Test
    fun renewsTheConsentOnceWhenGoogleAsks() = runTest {
        val page = "<html><a href=\"https://consent.google.com/\">x</a><form action=\"https://consent.google.com/save\"><input type=\"hidden\" name=\"bl\" value=\"boq_1\"><input type=\"hidden\" name=\"set_eom\" value=\"true\"></form>"
        val http = ScriptedHttp { url, headers, _ ->
            when {
                "hl=en&gl=IT" in url -> HttpResponse(200, "https://consent.google.com/m?x", page)
                "fresh-socs" !in headers.getValue("Cookie") -> HttpResponse(200, "https://consent.google.com/m", page)
                else -> ok(url)
            }
        }
        search(LiveQuotes(http, clock = FakeClock()))
        assertEquals(listOf(mapOf("bl" to "boq_1", "set_eom" to "true")), http.posts)
        assertEquals("SOCS=fresh-socs", http.calls.last().second["Cookie"])
    }

    @Test
    fun splitsAGroupGoogleCannotAnswer() = runTest {
        val http = ScriptedHttp { url, _, _ -> HttpResponse(200, url, if (tfsOf(url) in served) "<html>no data</html>" else served.values.first()) }
        val found = search(LiveQuotes(http, clock = FakeClock()))
        val pairs = request.origins.size * plan.destinations.size
        assertEquals(1 + pairs, http.calls.size)
        assertEquals(pairs, found.size)
    }

    @Test
    fun stopsWhenAskedAndResumesToTheSameResults() = runTest {
        var manager: JobManager? = null
        var id = ""
        var stopAt = 4
        val http = ScriptedHttp { url, _, n ->
            // Like a tap on "Ferma" from the screen, while the request is in flight.
            if (n == stopAt) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { manager!!.cancel(id) }
            ok(url)
        }
        val clock = FakeClock()
        manager = JobManager(MemoryJobs(), LiveQuotes(http, clock = clock), clock)
        id = manager.create(request, plan, today)
        manager.idle()
        assertEquals("cancelled", manager.get(id).status)
        assertTrue(http.calls.size in 4..5, "stopped after ${http.calls.size} requests")
        stopAt = -1
        manager.resume(id)
        manager.idle()
        val done = manager.get(id)
        assertEquals("complete", done.status)
        assertEquals(scenario.getValue("job").jsonObject.getValue("results").jsonObject.keys, done.results.keys)
    }

    @Test
    fun aBlockedSearchIsSavedAndASecondOneRefused() = runTest {
        val clock = FakeClock()
        val store = MemoryJobs()
        val http = ScriptedHttp { url, _, n -> if (n == 3) HttpResponse(429, url, "") else ok(url) }
        val manager = JobManager(store, LiveQuotes(http, clock = clock), clock)
        val id = manager.create(request, plan, today)
        manager.idle()
        val job = manager.get(id)
        assertEquals("blocked", job.status)
        assertTrue("Google ha limitato" in job.issues[0])
        assertTrue("\"status\":\"blocked\"" in store.data.getValue(id))
        val fresh = JobManager(store, LiveQuotes(http, clock = clock), clock)
        val saved = store.data.getValue(id).replace("\"status\":\"blocked\"", "\"status\":\"running\"")
        store.data[id] = saved
        assertEquals("interrupted", fresh.view(id).job.status)
    }

    @Test
    fun recheckConfirmsTheSameFlights() = runTest {
        val clock = FakeClock()
        val store = MemoryJobs()
        val manager = JobManager(store, LiveQuotes(ScriptedHttp { url, _, _ -> ok(url) }, clock = clock), clock)
        val id = manager.create(request, plan, today)
        manager.idle()
        val first = manager.view(id).browse.results.first()
        // Google now lists no flight for that route and day: the trip disappears.
        val empty = "<script class=\"ds:1\">AF_initDataCallback({data:[null,null,[null],[null]],sideChannel:{}});</script>"
        val gone = JobManager(store, LiveQuotes(ScriptedHttp { url, _, _ -> HttpResponse(200, url, empty) }, clock = clock), clock)
        assertEquals("not_confirmed", gone.recheck(id, first.id).status)
        val view = gone.view(id)
        assertTrue(view.browse.results.none { it.id == first.id })
        assertEquals(manager.get(id).results.size, view.browse.resultCount + 1)
    }
}
