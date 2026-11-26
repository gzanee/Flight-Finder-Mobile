package it.gzanee.flightfinder

import it.gzanee.flightfinder.domain.model.FlightFinderJson
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.search.AirportCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalTime

/** Casi generati dal portale Python (tools/golden_*.py) e catalogo incluso nell'app. */
object Fixtures {
    private val dir = File(System.getProperty("fixtures"))
    private val catalogDir = File(System.getProperty("catalog"))

    fun file(name: String) = File(dir, name)
    fun json(name: String): JsonElement = Json.parseToJsonElement(file(name).readText())

    val catalog: AirportCatalog by lazy {
        fun c(name: String) = File(catalogDir, name).readText()
        AirportCatalog(c("airports.json"), c("places.json"), c("routes.json"), c("airlines.json"), c("countries_it.json"), c("cities_en.json"))
    }

    /**
     * La richiesta tipizzata equivalente a una richiesta JSON del portale, oppure null
     * quando il JSON contiene un tipo che l'app non può produrre (es. "2" al posto di 2).
     */
    fun request(spec: JsonObject): SearchRequest? {
        // kotlinx leggerebbe "2" come 2: per l'app un numero è sempre un numero.
        if (spec.any { (key, value) -> key in NUMERIC && value is JsonPrimitive && value.isString }) return null
        val oneWayFixed = spec["date_mode"]?.jsonPrimitive?.content == "fixed" && spec["trip_type"]?.jsonPrimitive?.content == "one_way"
        val completed = if (oneWayFixed && spec["start_date"] != null) JsonObject(spec + ("end_date" to spec.getValue("start_date"))) else spec
        return runCatching { FlightFinderJson.decodeFromJsonElement(SearchRequest.serializer(), completed) }.getOrNull()
    }

    private val NUMERIC = setOf(
        "min_nights", "max_nights", "max_stops", "adults", "children", "infants", "min_destination_hours", "budget",
        "min_layover_minutes", "max_layover_minutes", "departure_duration_min", "departure_duration_max",
        "return_duration_min", "return_duration_max",
    )

    /** I filtri come li manda la pagina web del portale (query string). */
    fun filters(args: Map<String, String>) = ResultFilters(
        maxPrice = args["max_price"]?.toDouble(),
        minHours = args["min_hours"]?.toDouble(),
        origin = args["origin"],
        destinations = args["destination"]?.split(",")?.toSet().orEmpty(),
        airline = args["airline"],
        homeBefore = args["home_before"]?.let(LocalTime::parse),
        excluded = args["exclude"]?.split(",")?.toSet().orEmpty(),
    )
}

/** Campi che il portale scrive e l'app non tiene (sono sempre uguali o li calcola la UI). */
val PYTHON_ONLY = setOf("ticketing", "verification", "airline_verified", "baggage_basis")

/**
 * Uguaglianza JSON come la scrivono Python e Kotlin: 30 vale 30.0, una chiave
 * mancante vale null, le chiavi in `ignore` si saltano a ogni livello.
 */
fun sameJson(actual: JsonElement?, expected: JsonElement?, ignore: Set<String> = emptySet(), path: String = "$", onlyExpectedKeys: Boolean = false): String? {
    val x = actual ?: JsonNull
    val y = expected ?: JsonNull
    if (x is JsonObject && y is JsonObject) {
        for (k in if (onlyExpectedKeys) y.keys else x.keys + y.keys) {
            if (k in ignore) continue
            sameJson(x[k], y[k], ignore, "$path.$k", onlyExpectedKeys)?.let { return it }
        }
        return null
    }
    if (x is JsonArray && y is JsonArray) {
        if (x.size != y.size) return "$path: ${x.size} elementi invece di ${y.size}"
        x.indices.forEach { i -> sameJson(x[i], y[i], ignore, "$path[$i]", onlyExpectedKeys)?.let { return it } }
        return null
    }
    if (x is JsonPrimitive && y is JsonPrimitive && x !is JsonNull && y !is JsonNull) {
        if (!x.isString && !y.isString) {
            val dx = x.content.toDoubleOrNull()
            val dy = y.content.toDoubleOrNull()
            if (dx != null && dy != null) return if (dx == dy) null else "$path: $x invece di $y"
        }
        return if (x.isString == y.isString && x.content == y.content) null else "$path: $x invece di $y"
    }
    return if (x == y) null else "$path: $x invece di $y"
}

fun assertSameJson(expected: JsonElement?, actual: JsonElement?, ignore: Set<String> = emptySet(), label: String = "") {
    sameJson(actual, expected, ignore)?.let { throw AssertionError("$label $it") }
}
