package it.gzanee.flightfinder.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertTrue

class MiscTest {
    private val golden by lazy { Fixtures.installCatalog(); Fixtures.json("misc.json").jsonObject }

    @Test
    fun requestValidationMatchesPython() {
        val today = golden.getValue("today").jsonPrimitive.content
        val failures = ArrayList<String>()
        for ((i, c) in golden.getValue("requests").jsonArray.withIndex()) {
            val o = c.jsonObject
            val got = try {
                buildJsonObject { put("ok", json.encodeToJsonElement(FlexibleRequest.serializer(), parseRequest(o.getValue("spec").jsonObject, today))) }
            } catch (e: SearchValidationError) {
                buildJsonObject { put("error", e.message) }
            }
            val want = JsonObject(o.filterKeys { it == "ok" || it == "error" })
            sameJson(got, want)?.let { failures += "$i ${o["spec"].toString().take(80)}: $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun airportSearchCountriesAndDetailsMatchPython() {
        val failures = ArrayList<String>()
        for ((word, hits) in golden.getValue("search").jsonObject) {
            val got = buildJsonArray {
                for (h in Airports.search(word)) add(buildJsonObject { put("code", h.code); put("name", h.name); put("city", h.city); put("home", h.home) })
            }
            sameJson(got, hits)?.let { failures += "search '$word': $it" }
        }
        for ((origins, grouped) in golden.getValue("countries").jsonObject) {
            val got = JsonObject(Airports.countries(origins.split(",")).mapValues { (_, g) ->
                buildJsonObject {
                    put("linked", g.linked)
                    putJsonArray("airports") { g.airports.forEach { a -> add(buildJsonObject { put("code", a.code); put("city", a.city); put("name", a.name) }) } }
                }
            })
            sameJson(got, grouped)?.let { failures += "countries $origins: $it" }
            if (got.keys.toList() != grouped.jsonObject.keys.toList()) failures += "countries $origins: order"
        }
        for ((code, info) in golden.getValue("details").jsonObject) {
            sameJson(json.encodeToJsonElement(Airports.Details.serializer(), Airports.details(code)), info)?.let { failures += "details $code: $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
