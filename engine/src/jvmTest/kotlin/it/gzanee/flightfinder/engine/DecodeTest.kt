package it.gzanee.flightfinder.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecodeTest {
    private fun request(): FlexibleRequest {
        Fixtures.installCatalog()
        return parseRequest(buildJsonObject {
            put("origins", JsonArray(listOf(JsonPrimitive("VCE"))))
            put("destinations", JsonArray(listOf(JsonPrimitive("BCN"))))
            put("trip_type", "one_way"); put("date_mode", "fixed")
            put("start_date", "2026-12-24"); put("end_date", "2026-12-24")
            put("adults", 2); put("baggage", "cabin")
        }, "2026-11-24")
    }

    private fun result(html: String, r: FlexibleRequest, expected: ExpectedRoute) = try {
        val (quotes, rejected) = decode(html, r, "https://example/url", expected)
        buildJsonObject {
            put("quotes", json.encodeToJsonElement(ListSerializer(Quote.serializer()), quotes))
            put("rejected", rejected)
        }
    } catch (e: LiveUnavailable) {
        buildJsonObject { put("error", e::class.simpleName) }
    }

    @Test
    fun syntheticPagesDecodeLikePython() {
        val r = request()
        val cases = Fixtures.json("decode.json").jsonArray
        assertEquals(222, cases.size)
        val failures = ArrayList<String>()
        for (c in cases) {
            val o = c.jsonObject
            val exp = (o["expected_route"] as? JsonArray)?.let { e -> e[0].jsonArray.map { it.jsonPrimitive.content } to e[1].jsonArray.map { it.jsonPrimitive.content } }
            val got = result(o.getValue("html").jsonPrimitive.content, r, exp)
            sameJson(got, o.getValue("result"), setOf("checked_at"))?.let { failures.add("${o["name"]}: $it") }
        }
        assertTrue(failures.isEmpty(), failures.take(10).joinToString("\n"))
    }

    @Test
    fun realGooglePageDecodesLikePython() {
        Fixtures.installCatalog()
        val r = parseRequest(buildJsonObject {
            put("origins", JsonArray(listOf(JsonPrimitive("VCE")))); put("destinations", JsonArray(listOf(JsonPrimitive("BCN"))))
            put("trip_type", "one_way"); put("date_mode", "fixed"); put("start_date", "2026-12-24"); put("departure_min", "00:00")
        }, "2026-11-24")
        val html = GZIPInputStream(Fixtures.file("google_real_vce_bcn.html.gz").inputStream()).readBytes().decodeToString()
        val got = result(html, r, listOf("VCE") to listOf("BCN"))
        assertEquals(null, sameJson(got, Fixtures.json("google_real_vce_bcn.json"), setOf("checked_at", "booking_url")))
        assertTrue((got as JsonObject).getValue("quotes").jsonArray.isNotEmpty())
    }
}
