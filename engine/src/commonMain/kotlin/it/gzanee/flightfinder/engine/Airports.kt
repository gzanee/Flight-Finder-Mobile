package it.gzanee.flightfinder.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Offline airport catalog: port of flightcore/airports.py (data from tools/export_data.py). */
object Airports {
    private class Row(val name: String, val city: String, val display: String, val country: String, val zone: String, val decoder: Boolean)

    @kotlinx.serialization.Serializable
    data class Details(val code: String, val city: String, val name: String, val country: String)

    data class Hit(val code: String, val name: String, val city: String, val home: Boolean)

    data class CountryGroup(val linked: Boolean, val airports: MutableList<Place>)

    data class Place(val code: String, val city: String, val name: String)

    data class DestinationSet(val label: String, val codes: List<String>)

    private var rows: Map<String, Row> = emptyMap()
    private var snapshot: Map<String, List<String>> = emptyMap()
    private var aliases: List<Pair<String, List<String>>> = emptyList()
    private var mainAirports: Map<String, List<String>> = emptyMap()
    var home: Map<String, String> = emptyMap(); private set
    var sets: Map<String, DestinationSet> = linkedMapOf(); private set
    var airlineCodes: Set<String> = emptySet(); private set
    var countryNames: Map<String, String> = emptyMap(); private set
    const val DEFAULT_SET = "europe-short"

    val installed get() = rows.isNotEmpty()

    /** Load the data files of data/catalog (bundled with the app). */
    fun install(airports: String, places: String, routes: String, airlines: String, countries: String = "{}") {
        rows = Json.parseToJsonElement(airports).jsonObject.mapValues { (_, v) ->
            val a = v.jsonArray
            Row(
                a[0].jsonPrimitive.content, a[1].jsonPrimitive.content, a[2].jsonPrimitive.content,
                a[3].jsonPrimitive.content, a[4].jsonPrimitive.content, a[5].jsonPrimitive.int == 1,
            )
        }
        val p = Json.parseToJsonElement(places).jsonObject
        home = p.getValue("home").jsonObject.mapValues { it.value.jsonPrimitive.content }
        sets = LinkedHashMap<String, DestinationSet>().apply {
            p.getValue("sets").jsonObject.forEach { (k, v) ->
                put(k, DestinationSet(v.jsonObject.getValue("label").jsonPrimitive.content, v.jsonObject.getValue("codes").strings()))
            }
        }
        aliases = p.getValue("aliases").jsonObject.map { (k, v) -> k to v.strings() }
        mainAirports = p.getValue("main").jsonObject.mapValues { it.value.strings() }
        snapshot = Json.parseToJsonElement(routes).jsonObject.mapValues { (_, v) -> (v as? JsonArray)?.strings() ?: emptyList() }
        airlineCodes = Json.parseToJsonElement(airlines).jsonArray.map { it.jsonPrimitive.content }.toSet()
        countryNames = Json.parseToJsonElement(countries).jsonObject.mapValues { it.value.jsonPrimitive.content }
        normalized = null
    }

    private fun kotlinx.serialization.json.JsonElement.strings() = jsonArray.map { it.jsonPrimitive.content }

    fun exists(code: String) = rows[code.uppercase()]?.name?.isNotEmpty() == true

    fun name(code: String): String? = rows[code.uppercase()]?.name?.ifEmpty { null }

    /** Codes fli's decoder accepts: rows with others are rejected, as in Python. */
    fun knownToDecoder(code: String) = rows[code]?.decoder == true

    fun zone(code: String): String? = rows[code]?.zone?.ifEmpty { null }

    fun country(code: String): String? = rows[code]?.country?.ifEmpty { null }

    fun details(raw: String): Details {
        val code = raw.uppercase()
        val row = rows[code]
        if (row != null && row.city.isNotEmpty()) return Details(code, row.city, row.display, row.country.lowercase())
        val label = row?.name?.ifEmpty { null } ?: "Aeroporto non identificato"
        return Details(code, label, label, (row?.country ?: "").lowercase())
    }

    fun city(code: String) = details(code).city

    /** Lower case without accents, so "zurigo" finds "Zürich". */
    fun normalize(text: String): String = stripAccents(text.lowercase())

    private var normalized: List<Triple<String, String, String>>? = null

    /** Autocomplete: aliases first, then exact code, name prefix, substring, code prefix. */
    fun search(keyword: String?, limit: Int = 10): List<Hit> {
        val kw = (keyword ?: "").trim()
        if (kw.length < 2) return emptyList()
        val needle = normalize(kw)
        val aliasHits = aliases.filter { (alias, _) -> alias.startsWith(needle) || needle == alias }.flatMap { it.second }
        val names = normalized ?: rows.filter { it.value.name.isNotEmpty() }
            .map { (code, row) -> Triple(code, row.name, normalize(row.name)) }
            .also { normalized = it }
        val scored = ArrayList<Triple<Int, String, String>>()
        for ((code, label, haystack) in names) {
            val score = when {
                needle == code.lowercase() -> 0
                haystack.startsWith(needle) -> 1
                needle in haystack -> 2
                code.lowercase().startsWith(needle) -> 3
                else -> continue
            }
            scored.add(Triple(score, label, code))
        }
        scored.sortWith(compareBy<Triple<Int, String, String>>({ it.first }, { it.second.length }).thenBy(PY_STRING) { it.second })
        val final = ArrayList<String>()
        for (code in aliasHits + scored.map { it.third }) {
            if (code !in final && exists(code)) final.add(code)
            if (final.size >= limit) break
        }
        return final.map { Hit(it, name(it)!!, details(it).city, it in home) }
    }

    fun destinationSet(key: String): List<String> {
        val set = sets[key] ?: sets.getValue(DEFAULT_SET)
        return set.codes.distinct().filter(::exists)
    }

    /** Snapshot routes of an origin (the app has no Wikipedia refresh). */
    fun routeDestinations(origin: String): List<String> = snapshot[origin.uppercase()] ?: emptyList()

    fun routePool(): List<String> = snapshot.values.flatten().distinct().sortedWith(PY_STRING)

    /** Airports to pick a whole country at once, by ISO country code (airports.countries). */
    fun countries(origins: List<String>): Map<String, CountryGroup> {
        val pool = routePool().toSet()
        var linked: Set<String> = origins.flatMap(::routeDestinations).toSet()
        if (linked.isEmpty()) linked = pool
        val known = pool.toMutableSet()
        sets.values.forEach { known.addAll(it.codes) }
        mainAirports.values.forEach { known.addAll(it) }
        val upper = origins.map { it.uppercase() }
        val grouped = LinkedHashMap<String, CountryGroup>()
        for ((isLinked, codes) in listOf(true to linked, false to known - linked)) {
            for (code in codes.sortedWith(PY_STRING)) {
                val info = details(code)
                if (!exists(code) || info.country.isEmpty() || code.uppercase() in upper) continue
                val entry = grouped.getOrPut(info.country) { CountryGroup(isLinked, ArrayList()) }
                if (entry.linked == isLinked) entry.airports.add(Place(code, info.city, info.name))
            }
        }
        return grouped
    }

    /** Candidate destinations of an "Ovunque" search, per origin (flexible_api.candidate_routes). */
    fun candidateRoutes(r: FlexibleRequest): Map<String, List<String>> {
        if (r.destinations.isNotEmpty()) return emptyMap()
        if (r.destinationSet != "real") return r.origins.associateWith { destinationSet(r.destinationSet) }
        val map = r.origins.associateWith(::routeDestinations)
        if (map.values.none { it.isNotEmpty() }) return mapOf("catalogo_generale" to routePool())
        return map
    }
}

/** Python's default string order: by code point (Kotlin compares UTF-16 units; equal for BMP text). */
val PY_STRING: Comparator<String> = Comparator { a, b -> a.compareTo(b) }

/** unicodedata.normalize("NFKD") without the combining accents, as airports._normalize. */
fun stripAccents(text: String): String = nfkd(text).filter { it.code !in 0x300..0x36F }

/** Unicode NFKD of the platform (java.text.Normalizer, NSString). */
expect fun nfkd(text: String): String
