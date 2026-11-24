package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.AirportDetails
import it.gzanee.flightfinder.domain.model.REAL_ROUTES
import it.gzanee.flightfinder.domain.model.SearchRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

/**
 * Catalogo aeroporti offline: port di flightcore/airports.py. I dati arrivano dal
 * portale (tools/export_data.py → data/catalog) e sono inclusi nell'app.
 */
class AirportCatalog(
    airportsJson: String,
    placesJson: String,
    routesJson: String,
    airlinesJson: String,
    countriesJson: String,
) {
    private class Row(val name: String, val city: String, val display: String, val country: String, val zone: String, val decoder: Boolean)

    data class Hit(val code: String, val name: String, val city: String, val isHome: Boolean)
    data class Place(val code: String, val city: String, val name: String)
    data class CountryGroup(val linked: Boolean, val airports: List<Place>)
    data class DestinationSet(val label: String, val codes: List<String>)

    private val rows: Map<String, Row>
    private val snapshot: Map<String, List<String>>
    private val aliases: List<Pair<String, List<String>>>
    private val mainAirports: Map<String, List<String>>
    val home: Map<String, String>
    val destinationSets: Map<String, DestinationSet>
    val airlineCodes: Set<String>
    val countryNames: Map<String, String>

    init {
        fun JsonElement.strings() = jsonArray.map { it.jsonPrimitive.content }
        rows = Json.parseToJsonElement(airportsJson).jsonObject.mapValues { (_, v) ->
            val a = v.jsonArray.map { it.jsonPrimitive }
            Row(a[0].content, a[1].content, a[2].content, a[3].content, a[4].content, a[5].int == 1)
        }
        val places = Json.parseToJsonElement(placesJson).jsonObject
        home = places.getValue("home").jsonObject.mapValues { it.value.jsonPrimitive.content }
        destinationSets = places.getValue("sets").jsonObject.entries.associateTo(LinkedHashMap()) { (key, v) ->
            key to DestinationSet(v.jsonObject.getValue("label").jsonPrimitive.content, v.jsonObject.getValue("codes").strings())
        }
        aliases = places.getValue("aliases").jsonObject.map { (k, v) -> k to v.strings() }
        mainAirports = places.getValue("main").jsonObject.mapValues { it.value.strings() }
        snapshot = Json.parseToJsonElement(routesJson).jsonObject.mapValues { (_, v) -> (v as? JsonArray)?.strings().orEmpty() }
        airlineCodes = Json.parseToJsonElement(airlinesJson).jsonArray.map { it.jsonPrimitive.content }.toSet()
        countryNames = Json.parseToJsonElement(countriesJson).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    fun exists(code: String) = rows[code.uppercase()]?.name?.isNotEmpty() == true

    fun name(code: String): String? = rows[code.uppercase()]?.name?.ifEmpty { null }

    /** Codici che il decoder di fli conosce: righe con altri aeroporti vengono scartate, come nel portale. */
    fun knownToDecoder(code: String) = rows[code]?.decoder == true

    fun zone(code: String): String? = rows[code]?.zone?.ifEmpty { null }

    fun country(code: String): String? = rows[code]?.country?.ifEmpty { null }

    fun details(raw: String): AirportDetails {
        val code = raw.uppercase()
        val row = rows[code]
        if (row != null && row.city.isNotEmpty()) return AirportDetails(code, row.city, row.display, row.country.lowercase())
        val label = row?.name?.ifEmpty { null } ?: "Aeroporto non identificato"
        return AirportDetails(code, label, label, row?.country.orEmpty().lowercase())
    }

    fun city(code: String) = details(code).city

    private val normalizedNames by lazy {
        rows.filterValues { it.name.isNotEmpty() }.map { (code, row) -> Triple(code, row.name, normalize(row.name)) }
    }

    /** Autocomplete: prima gli alias italiani, poi codice esatto, inizio del nome, parte del nome, inizio del codice. */
    fun search(keyword: String, limit: Int = 10): List<Hit> {
        val kw = keyword.trim()
        if (kw.length < 2) return emptyList()
        val needle = normalize(kw)
        val aliasHits = aliases.filter { (alias, _) -> alias.startsWith(needle) }.flatMap { it.second }
        val scored = normalizedNames.mapNotNull { (code, label, haystack) ->
            val score = when {
                needle == code.lowercase() -> 0
                haystack.startsWith(needle) -> 1
                needle in haystack -> 2
                code.lowercase().startsWith(needle) -> 3
                else -> return@mapNotNull null
            }
            Triple(score, label, code)
        }.sortedWith(compareBy<Triple<Int, String, String>>({ it.first }, { it.second.length }, { it.second }))
        return (aliasHits + scored.map { it.third }).filter(::exists).distinct().take(limit)
            .map { Hit(it, name(it)!!, details(it).city, it in home) }
    }

    fun destinationSet(key: String): List<String> =
        (destinationSets[key] ?: destinationSets.getValue(DEFAULT_SET)).codes.distinct().filter(::exists)

    /** Rotte dell'aeroporto dallo snapshot del portale (l'app non le aggiorna da Wikipedia). */
    fun routeDestinations(origin: String): List<String> = snapshot[origin.uppercase()].orEmpty()

    fun routePool(): List<String> = snapshot.values.flatten().distinct().sorted()

    /** Aeroporti per scegliere un paese intero, per codice ISO (airports.countries). */
    fun countries(origins: List<String>): Map<String, CountryGroup> {
        val pool = routePool().toSet()
        val linked = origins.flatMap(::routeDestinations).toSet().ifEmpty { pool }
        val known = pool + destinationSets.values.flatMap { it.codes } + mainAirports.values.flatten()
        val upper = origins.map { it.uppercase() }
        val grouped = LinkedHashMap<String, Pair<Boolean, MutableList<Place>>>()
        for ((isLinked, codes) in listOf(true to linked, false to known - linked)) {
            for (code in codes.sorted()) {
                val info = details(code)
                if (!exists(code) || info.country.isEmpty() || code.uppercase() in upper) continue
                val entry = grouped.getOrPut(info.country) { isLinked to ArrayList() }
                if (entry.first == isLinked) entry.second += Place(code, info.city, info.name)
            }
        }
        return grouped.mapValues { (_, v) -> CountryGroup(v.first, v.second) }
    }

    /** Destinazioni candidate di "Ovunque", per origine (flexible_api.candidate_routes). */
    fun candidateRoutes(request: SearchRequest): Map<String, List<String>> = when {
        request.destinations.isNotEmpty() -> emptyMap()
        request.destinationSet != REAL_ROUTES -> request.origins.associateWith { destinationSet(request.destinationSet) }
        else -> request.origins.associateWith(::routeDestinations).takeIf { m -> m.values.any { it.isNotEmpty() } }
            ?: mapOf("catalogo_generale" to routePool())
    }

    companion object {
        const val DEFAULT_SET = "europe-short"

        /** Minuscolo e senza accenti, così "zurigo" trova "Zürich" (airports._normalize). */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).filter { it.code !in 0x300..0x36F }
    }
}
