package it.gzanee.flightfinder.domain

import it.gzanee.flightfinder.Fixtures
import it.gzanee.flightfinder.domain.model.FlightFinderJson
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SearchValidationError
import it.gzanee.flightfinder.domain.search.validated
import it.gzanee.flightfinder.sameJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidationAndAirportsTest {
    private val golden = Fixtures.json("misc.json").jsonObject
    private val catalog = Fixtures.catalog

    @Test
    fun validazioneComeNelPortale() {
        val today = LocalDate.parse(golden.getValue("today").jsonPrimitive.content)
        var typed = 0
        val failures = golden.getValue("requests").jsonArray.mapIndexedNotNull { i, case ->
            val c = case.jsonObject
            // Una richiesta con un tipo sbagliato (es. "2" invece di 2) l'app non la può costruire.
            val request = Fixtures.request(c.getValue("spec").jsonObject) ?: return@mapIndexedNotNull null
            typed++
            val got = try {
                buildJsonObject { put("ok", FlightFinderJson.encodeToJsonElement(SearchRequest.serializer(), request.validated(catalog, today))) }
            } catch (e: SearchValidationError) {
                buildJsonObject { put("error", e.message) }
            }
            sameJson(got, JsonObject(c.filterKeys { it == "ok" || it == "error" }))?.let { "$i ${c["spec"].toString().take(100)}: $it" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("casi tipizzati: $typed", typed >= 37)
    }

    @Test
    fun aeroportiPaesiENomiComeNelPortale() {
        val failures = ArrayList<String>()
        for ((word, hits) in golden.getValue("search").jsonObject) {
            val got = buildJsonArray {
                for (h in catalog.search(word)) add(buildJsonObject { put("code", h.code); put("name", h.name); put("city", h.city); put("home", h.isHome) })
            }
            sameJson(got, hits)?.let { failures += "search '$word': $it" }
        }
        for ((origins, expected) in golden.getValue("countries").jsonObject) {
            val countries = catalog.countries(origins.split(","))
            val got = JsonObject(countries.mapValues { (_, g) ->
                buildJsonObject {
                    put("linked", g.linked)
                    putJsonArray("airports") { g.airports.forEach { a -> add(buildJsonObject { put("code", a.code); put("city", a.city); put("name", a.name) }) } }
                }
            })
            sameJson(got, expected)?.let { failures += "countries $origins: $it" }
            if (got.keys.toList() != expected.jsonObject.keys.toList()) failures += "countries $origins: ordine"
        }
        for ((code, info) in golden.getValue("details").jsonObject) {
            val d = catalog.details(code)
            val got = buildJsonObject { put("code", d.code); put("city", d.city); put("name", d.name); put("country", d.country) }
            sameJson(got, info)?.let { failures += "details $code: $it" }
        }
        assertEquals("", failures.joinToString("\n"))
    }
}
