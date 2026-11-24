package it.gzanee.flightfinder.engine

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A Google that answers only the requests the Python engine made, with its pages. */
class FakeGoogle(private val served: Map<String, String>) : Http {
    val asked = ArrayList<String>()
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        val tfs = URLDecoder.decode(Regex("[?&]tfs=([^&]+)").find(url)!!.groupValues[1], "UTF-8")
        asked += tfs
        val page = served[tfs] ?: throw AssertionError("Richiesta non fatta dal Python: $url")
        return HttpResponse(200, url, page)
    }
    override suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpResponse =
        throw AssertionError("no consent flow in tests")
}

class FakeClock(var t: Double = System.currentTimeMillis() / 1000.0) : EngineClock {
    val sleeps = ArrayList<Double>()
    override fun now(): Double { t += 0.001; return t }
    override suspend fun sleep(seconds: Double) { sleeps += seconds; t += seconds }
}

private val VIEWS = mapOf(
    "price" to ("price" to emptyMap<String, String>()),
    "useful" to ("useful" to emptyMap()),
    "duration" to ("duration" to emptyMap()),
    "departure_filtered" to ("departure" to mapOf("max_price" to "150")),
    "home_before" to ("price" to mapOf("home_before" to "21:00")),
    "airline" to ("price" to mapOf("airline" to "FR")),
    "min_hours" to ("useful" to mapOf("min_hours" to "45")),
    "places" to ("price" to mapOf("origin" to "VCE", "destination" to "BCN,BER,STN,LTN", "exclude" to "LTN")),
)

private val VOLATILE = setOf("checked_at", "needs_recheck", "eta_seconds", "updated_at", "created_at")

class JobsTest {
    @Test
    fun completeSearchesMatchPython() = runTest {
        Fixtures.installCatalog()
        val golden = Fixtures.json("jobs.json").jsonObject
        val today = golden.getValue("today").jsonPrimitive.content
        val scenarios = golden.getValue("scenarios").jsonObject
        assertEquals(8, scenarios.size)
        for ((name, s) in scenarios) {
            val sc = s.jsonObject
            val request = parseRequest(sc.getValue("spec").jsonObject, today)
            val plan = buildPlan(request, Airports.candidateRoutes(request), today)
            assertSameJson(sc.getValue("plan"), json.encodeToJsonElement(Plan.serializer(), plan), label = "$name plan")

            val served = sc.getValue("served").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val google = FakeGoogle(served)
            val clock = FakeClock()
            val manager = JobManager(MemoryJobs(), LiveQuotes(google, clock = clock), clock)
            val id = manager.create(request, plan, today)
            manager.idle()
            val job = manager.get(id)
            val expected = sc.getValue("job").jsonObject
            assertEquals(served.size, google.asked.size, "$name requests")
            val got = JOB_JSON.encodeToJsonElement(Job.serializer(), job).jsonObject
            for (field in listOf("status", "issues", "completed", "total", "requests", "return_days", "return_requests",
                "unverified", "empty_searches", "cursor", "pass", "outbound", "results", "round_trips")) {
                assertSameJson(expected[field], got[field], VOLATILE, "$name $field")
            }
            for ((key, args) in VIEWS) {
                val view = manager.view(id, args.first, 0, ResultFilters.parse(args.second))
                val want = sc.getValue("views").jsonObject.getValue(key).jsonObject
                val b = json.encodeToJsonElement(Browse.serializer(), view.browse).jsonObject
                for (field in listOf("result_count", "filtered_count", "facets", "groups", "recommendations", "calendar", "results", "offset", "has_more", "focused_id")) {
                    assertSameJson(want[field], b[field], VOLATILE, "$name/$key $field")
                }
                val airports = json.encodeToJsonElement(MapSerializer(String.serializer(), Airports.Details.serializer()), view.airports)
                assertSameJson(want["airports"], airports, label = "$name/$key airports")
            }
        }
    }

    @Test
    fun browsingMatchesPythonOnRandomTrips() {
        Fixtures.installCatalog()
        val cases = Fixtures.json("browse.json").jsonArray
        val failures = ArrayList<String>()
        for ((i, c) in cases.withIndex()) {
            val o = c.jsonObject
            val trips = json.decodeFromJsonElement(ListSerializer(Trip.serializer()), o.getValue("trips"))
            val args = o.getValue("args").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val got = browseResults(trips, o.getValue("sort").jsonPrimitive.content, o.getValue("offset").jsonPrimitive.content.toInt(),
                ResultFilters.parse(args), o.getValue("focus").jsonPrimitive.content)
            // The random trips are partial: compare only the keys the Python wrote in trips,
            // everything else exactly.
            val b = json.encodeToJsonElement(Browse.serializer(), got).jsonObject
            val want = o.getValue("result").jsonObject
            sameJson(b["results"], want["results"], onlyExpectedKeys = true)?.let { failures += "case $i results: $it" }
            for (field in want.keys - "results") sameJson(b[field], want[field])?.let { failures += "case $i $field: $it" }
        }
        assertTrue(failures.isEmpty(), failures.take(10).joinToString("\n"))
    }
}

private val JsonElement.jsonArray get() = this as kotlinx.serialization.json.JsonArray
