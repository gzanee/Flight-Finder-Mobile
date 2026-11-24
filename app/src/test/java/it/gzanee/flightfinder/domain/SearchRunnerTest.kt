package it.gzanee.flightfinder.domain

import it.gzanee.flightfinder.FakeClock
import it.gzanee.flightfinder.FakeGoogle
import it.gzanee.flightfinder.Fixtures
import it.gzanee.flightfinder.InMemorySearchStore
import it.gzanee.flightfinder.PYTHON_ONLY
import it.gzanee.flightfinder.assertSameJson
import it.gzanee.flightfinder.data.google.GoogleConsent
import it.gzanee.flightfinder.data.google.GoogleFlightsClient
import it.gzanee.flightfinder.data.google.GooglePageDecoder
import it.gzanee.flightfinder.data.google.InMemoryConsentStore
import it.gzanee.flightfinder.domain.model.FlightFinderJson
import it.gzanee.flightfinder.domain.model.Plan
import it.gzanee.flightfinder.domain.model.ResultPage
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.search.SearchRunner
import it.gzanee.flightfinder.domain.search.browseResults
import it.gzanee.flightfinder.domain.search.buildPlan
import it.gzanee.flightfinder.domain.search.validated
import it.gzanee.flightfinder.sameJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Ricerche complete e risultati esplorati, confrontati con il portale Python. */
class SearchRunnerTest {
    private val catalog = Fixtures.catalog

    private val views = mapOf(
        "price" to (SortOrder.PRICE to emptyMap<String, String>()),
        "useful" to (SortOrder.USEFUL to emptyMap()),
        "duration" to (SortOrder.DURATION to emptyMap()),
        "departure_filtered" to (SortOrder.DEPARTURE to mapOf("max_price" to "150")),
        "home_before" to (SortOrder.PRICE to mapOf("home_before" to "21:00")),
        "airline" to (SortOrder.PRICE to mapOf("airline" to "FR")),
        "min_hours" to (SortOrder.USEFUL to mapOf("min_hours" to "45")),
        "places" to (SortOrder.PRICE to mapOf("origin" to "VCE", "destination" to "BCN,BER,STN,LTN", "exclude" to "LTN")),
    )

    /** Campi che cambiano a ogni esecuzione o che il portale aggiunge per la sua pagina web. */
    private val volatile = PYTHON_ONLY + setOf("checked_at", "needs_recheck", "eta_seconds", "updated_at", "created_at", "destination", "round_trip_url")

    @Test
    fun ricercheCompleteComeNelPortale() = runBlocking {
        val golden = Fixtures.json("jobs.json").jsonObject
        val today = LocalDate.parse(golden.getValue("today").jsonPrimitive.content)
        val scenarios = golden.getValue("scenarios").jsonObject
        assertEquals(8, scenarios.size)
        for ((name, s) in scenarios) {
            val scenario = s.jsonObject
            val request = Fixtures.request(scenario.getValue("spec").jsonObject)!!.validated(catalog, today)
            val plan = buildPlan(request, catalog.candidateRoutes(request), catalog, today)
            assertSameJson(scenario.getValue("plan"), FlightFinderJson.encodeToJsonElement(Plan.serializer(), plan), label = "$name plan")

            val served = scenario.getValue("served").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val google = FakeGoogle.serving(served)
            val clock = FakeClock()
            val client = GoogleFlightsClient(google, GooglePageDecoder(catalog), GoogleConsent(google, InMemoryConsentStore()), clock)
            val runner = SearchRunner(InMemorySearchStore(), client, catalog, CoroutineScope(SupervisorJob()), clock)
            val id = runner.start(request, plan, today)
            runner.idle()
            val job = runner.get(id)
            val expected = scenario.getValue("job").jsonObject
            assertEquals("$name richieste", served.size, google.asked.size)
            val got = FlightFinderJson.encodeToJsonElement(SearchJob.serializer(), job).jsonObject
            for (field in listOf("status", "issues", "completed", "total", "requests", "return_days", "return_requests", "unverified", "empty_searches", "pass", "outbound", "results", "round_trips")) {
                assertSameJson(expected[field], got[field], volatile, "$name $field")
            }
            val cursor = expected.getValue("cursor").jsonArray.map { it.jsonPrimitive.content.toInt() }
            assertEquals("$name cursor", cursor, listOf(job.cursor.day, job.cursor.stage, job.cursor.batch))

            for ((key, args) in views) {
                val page = runner.view(id, args.first, 0, Fixtures.filters(args.second)).page
                val want = scenario.getValue("views").jsonObject.getValue(key).jsonObject
                val actual = FlightFinderJson.encodeToJsonElement(ResultPage.serializer(), page).jsonObject
                for (field in listOf("result_count", "filtered_count", "facets", "groups", "recommendations", "calendar", "results", "offset", "has_more", "focused_id")) {
                    assertSameJson(want[field], actual[field], volatile, "$name/$key $field")
                }
            }
        }
    }

    @Test
    fun filtriOrdinamentiSuggerimentiComeNelPortale() {
        val failures = Fixtures.json("browse.json").jsonArray.mapIndexedNotNull { i, case ->
            val c = case.jsonObject
            val trips = FlightFinderJson.decodeFromJsonElement(ListSerializer(Trip.serializer()), c.getValue("trips"))
            val args = c.getValue("args").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val sort = SortOrder.valueOf(c.getValue("sort").jsonPrimitive.content.uppercase())
            val focus = c.getValue("focus").jsonPrimitive.content.ifEmpty { null }
            val page = browseResults(trips, catalog, sort, c.getValue("offset").jsonPrimitive.content.toInt(), Fixtures.filters(args), focus)
            val got = FlightFinderJson.encodeToJsonElement(ResultPage.serializer(), page).jsonObject
            val want = c.getValue("result").jsonObject
            // I viaggi casuali sono parziali: nei viaggi si confrontano le chiavi scritte dal portale, il resto esattamente.
            sameJson(got["results"], want["results"], onlyExpectedKeys = true)?.let { return@mapIndexedNotNull "caso $i results: $it" }
            (want.keys - "results").firstNotNullOfOrNull { f -> sameJson(got[f], want[f], setOf("destination"))?.let { "caso $i $f: $it" } }
        }
        assertTrue(failures.take(10).joinToString("\n"), failures.isEmpty())
    }
}

@Suppress("unused")
private val keepImports = listOf(JsonArray(emptyList()), JsonPrimitive(0))
