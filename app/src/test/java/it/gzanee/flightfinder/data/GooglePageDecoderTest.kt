package it.gzanee.flightfinder.data

import it.gzanee.flightfinder.Fixtures
import it.gzanee.flightfinder.PYTHON_ONLY
import it.gzanee.flightfinder.data.google.GooglePageDecoder
import it.gzanee.flightfinder.data.google.GoogleTransientException
import it.gzanee.flightfinder.data.google.GoogleUnavailableException
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.FlightFinderJson
import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.TripType
import it.gzanee.flightfinder.sameJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.GZIPInputStream

class GooglePageDecoderTest {
    private val decoder = GooglePageDecoder(Fixtures.catalog)
    private val day = LocalDate.parse("2026-12-24")
    private val request = SearchRequest(
        origins = listOf("VCE"), destinations = listOf("BCN"), startDate = day, endDate = day,
        tripType = TripType.ONE_WAY, dateMode = DateMode.FIXED, adults = 2, baggage = Baggage.CABIN,
    )

    private fun decodeAsPython(html: String, request: SearchRequest, expected: Pair<List<String>, List<String>>?): JsonElement = try {
        val page = decoder.decode(html, request, "https://example/url", expected)
        buildJsonObject {
            put("quotes", FlightFinderJson.encodeToJsonElement(ListSerializer(Quote.serializer()), page.quotes))
            put("rejected", page.rejected)
        }
    } catch (e: GoogleTransientException) {
        buildJsonObject { put("error", "LiveTransient") }
    } catch (e: GoogleUnavailableException) {
        buildJsonObject { put("error", "LiveUnavailable") }
    }

    @Test
    fun pagineSinteticheComeNelPortale() {
        val cases = Fixtures.json("decode.json").jsonArray
        assertEquals(222, cases.size)
        val failures = cases.mapNotNull { case ->
            val c = case.jsonObject
            val expected = (c["expected_route"] as? JsonArray)?.let { e -> e[0].jsonArray.map { it.jsonPrimitive.content } to e[1].jsonArray.map { it.jsonPrimitive.content } }
            val got = decodeAsPython(c.getValue("html").jsonPrimitive.content, request, expected)
            sameJson(got, c.getValue("result"), PYTHON_ONLY + "checked_at")?.let { "${c["name"]}: $it" }
        }
        assertTrue(failures.take(10).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun paginaVeraDiGoogleComeNelPortale() {
        val real = request.copy(adults = 1, baggage = Baggage.BASE, departureMin = LocalTime.MIDNIGHT)
        val html = GZIPInputStream(Fixtures.file("google_real_vce_bcn.html.gz").inputStream()).readBytes().decodeToString()
        val got = decodeAsPython(html, real, listOf("VCE") to listOf("BCN"))
        assertNull(sameJson(got, Fixtures.json("google_real_vce_bcn.json"), PYTHON_ONLY + setOf("checked_at", "booking_url")))
        assertTrue(got.jsonObject.getValue("quotes").jsonArray.isNotEmpty())
    }
}
