package it.gzanee.flightfinder.engine

import it.gzanee.flightfinder.engine.Calendar.addDays
import it.gzanee.flightfinder.engine.Calendar.clockSeconds
import it.gzanee.flightfinder.engine.Calendar.daysBetween
import it.gzanee.flightfinder.engine.Calendar.hasOffset
import it.gzanee.flightfinder.engine.Calendar.instant
import it.gzanee.flightfinder.engine.Calendar.localClock
import it.gzanee.flightfinder.engine.Calendar.localDate
import it.gzanee.flightfinder.engine.Calendar.weekday
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okio.ByteString.Companion.encodeUtf8

/*
 * Calendar planning and strict matching: port of flightcore/flexible.py.
 * Route lists are discovery seeds, never evidence of dated flight availability.
 * All windows are airport-local; elapsed times are measured in UTC.
 */

const val MAX_ORIGINS = 10
const val MAX_STOPS = 3
const val MAX_DURATION_MINUTES = 4320
const val MAX_PERIOD_DAYS = 366
const val MAX_TRAVELERS = 9
const val MAX_BUDGET = 100000.0
const val MAX_DESTINATIONS = 200
const val MAX_TASKS = 50000
const val MAX_AIRPORTS_PER_SEARCH = 10
const val GOOGLE_HORIZON_DAYS = 330

val SCHENGEN_COUNTRIES = "AT BE BG HR CZ DK EE FI FR DE GR HU IS IT LV LI LT LU MT NL NO PL PT RO SK SI ES SE CH".split(" ").toSet()
val LOW_COST = "FR RK W4 W6 W9 U2 EC DS VY V7 HV TO PC EW".split(" ").toSet()

class SearchValidationError(message: String) : IllegalArgumentException(message)

@Serializable
data class FlexibleRequest(
    val origins: List<String>,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("departure_days") val departureDays: List<Int> = listOf(4),
    @SerialName("return_days") val returnDays: List<Int> = listOf(6),
    @SerialName("departure_min") val departureMin: String = "18:00",
    @SerialName("departure_max") val departureMax: String = "23:59",
    @SerialName("return_min") val returnMin: String = "18:00",
    @SerialName("return_max") val returnMax: String = "23:59",
    @SerialName("min_nights") val minNights: Int = 2,
    @SerialName("max_nights") val maxNights: Int = 2,
    @SerialName("max_stops") val maxStops: Int = 0,
    @SerialName("allow_self_transfer") val allowSelfTransfer: Boolean = false,
    @SerialName("return_airport") val returnAirport: String = "same",
    val budget: Double = 100.0,
    val adults: Int = 1,
    val children: Int = 0,
    @SerialName("airline_mode") val airlineMode: String = "all",
    val baggage: String = "base",
    @SerialName("final_arrival_max") val finalArrivalMax: String? = null,
    @SerialName("min_destination_hours") val minDestinationHours: Int = 0,
    val destinations: List<String> = emptyList(),
    val currency: String = "EUR",
    @SerialName("trip_type") val tripType: String = "round_trip",
    @SerialName("destination_set") val destinationSet: String = "real",
    @SerialName("date_mode") val dateMode: String = "flexible",
    @SerialName("return_departure") val returnDeparture: String = "same",
    val seat: String = "economy",
    val infants: Int = 0,
    val airlines: List<String> = emptyList(),
    @SerialName("exclude_basic_economy") val excludeBasicEconomy: Boolean = false,
    @SerialName("allow_airport_change") val allowAirportChange: Boolean = false,
    @SerialName("schengen_transfers_only") val schengenTransfersOnly: Boolean = false,
    @SerialName("departure_arrival_min") val departureArrivalMin: String = "00:00",
    @SerialName("departure_arrival_max") val departureArrivalMax: String = "23:59",
    @SerialName("return_arrival_min") val returnArrivalMin: String = "00:00",
    @SerialName("return_arrival_max") val returnArrivalMax: String = "23:59",
    @SerialName("min_layover_minutes") val minLayoverMinutes: Int = 0,
    @SerialName("max_layover_minutes") val maxLayoverMinutes: Int = MAX_DURATION_MINUTES,
    @SerialName("departure_duration_min") val departureDurationMin: Int = 0,
    @SerialName("departure_duration_max") val departureDurationMax: Int = MAX_DURATION_MINUTES,
    @SerialName("return_duration_min") val returnDurationMin: Int = 0,
    @SerialName("return_duration_max") val returnDurationMax: Int = MAX_DURATION_MINUTES,
) {
    val travelers get() = adults + children + infants
    val roundTrip get() = tripType == "round_trip"

    fun window(inbound: Boolean) = if (inbound) returnMin to returnMax else departureMin to departureMax
    fun durations(inbound: Boolean) = if (inbound) returnDurationMin to returnDurationMax else departureDurationMin to departureDurationMax
    fun arrivals(inbound: Boolean) = if (inbound) returnArrivalMin to returnArrivalMax else departureArrivalMin to departureArrivalMax
}

@Serializable
data class Segment(
    @SerialName("from_code") val fromCode: String,
    @SerialName("to_code") val toCode: String,
    val departure: String,
    val arrival: String,
    @SerialName("airline_code") val airlineCode: String,
    @SerialName("airline_name") val airlineName: String,
    @SerialName("flight_number") val flightNumber: String,
    @SerialName("duration_min") val durationMin: Int,
)

@Serializable
data class Quote(
    val segments: List<Segment>,
    val price: Double = 0.0,
    val currency: String = "EUR",
    @SerialName("duration_min") val durationMin: Int,
    @SerialName("self_transfer") val selfTransfer: Boolean? = null,
    @SerialName("checked_at") val checkedAt: String = "",
    val verification: String = "google_live",
    @SerialName("airline_verified") val airlineVerified: Boolean = false,
    val baggage: String = "base",
    @SerialName("baggage_basis") val baggageBasis: String = "google_search_filter",
    val travelers: Int = 1,
    val seat: String = "economy",
    @SerialName("booking_url") val bookingUrl: String = "",
) {
    val first get() = segments.first()
    val last get() = segments.last()
}

@Serializable
data class Trip(
    val id: String,
    @SerialName("trip_type") val tripType: String = "round_trip",
    val price: Double,
    val currency: String = "EUR",
    val outbound: Quote,
    val inbound: Quote? = null,
    @SerialName("useful_minutes") val usefulMinutes: Int? = null,
    val nights: Int? = null,
    val ticketing: String = "",
    val verification: String = "google_live",
    @SerialName("airline_verified") val airlineVerified: Boolean = false,
    @SerialName("checked_at") val checkedAt: String = "",
    @SerialName("destination_airport_change") val destinationAirportChange: Boolean? = null,
    // Added when results are presented.
    @SerialName("needs_recheck") val needsRecheck: Boolean? = null,
    @SerialName("round_trip_from") val roundTripFrom: Double? = null,
    @SerialName("round_trip_url") val roundTripUrl: String? = null,
)

// ---------------- Request parsing ----------------

fun integer(value: JsonElement?, label: String, low: Int, high: Int): Int {
    val p = value as? JsonPrimitive
    val text = if (p == null || p is JsonNull || p.booleanOrNull != null && !p.isString) null else p.content
    if (text == null || !Regex("""\d+""").matches(text)) throw SearchValidationError("$label: serve un numero intero.")
    val n = text.toBigIntegerOrNullSafe()
    if (n == null || n < low || n > high) throw SearchValidationError("$label: scegli un valore fra $low e $high.")
    return n
}

private fun String.toBigIntegerOrNullSafe(): Int? = if (length > 9) Int.MAX_VALUE else toIntOrNull()

fun clock(value: JsonElement?): String {
    val p = value as? JsonPrimitive
    val text = if (p != null && p.isString) p.content else null
    if (text == null || !Regex("""\d{2}:\d{2}""").matches(text)) throw SearchValidationError("Gli orari devono avere formato HH:MM.")
    if (text.substring(0, 2).toInt() > 23 || text.substring(3).toInt() > 59) throw SearchValidationError("Orario non valido.")
    return text
}

private fun weekdays(value: JsonElement?, label: String): List<Int> {
    if (value !is JsonArray || value.isEmpty()) throw SearchValidationError("Seleziona almeno un giorno per $label.")
    return value.map { integer(it, label, 0, 6) }.toSortedSet().toList()
}

private val SEPARATORS = Regex("""[\s,;]+""")

fun airportCodes(raw: JsonElement?, label: String, maximum: Int, required: Boolean = false): List<String> {
    val list: List<JsonElement>? = when {
        raw is JsonPrimitive && raw.isString -> raw.content.trim().let { t -> if (t.isEmpty()) emptyList() else t.split(SEPARATORS).map(::JsonPrimitive) }
        raw is JsonArray -> raw
        else -> null
    }
    if (list == null || list.size > maximum || (required && list.isEmpty())) {
        throw SearchValidationError("$label: scegli da ${if (required) 1 else 0} a $maximum aeroporti.")
    }
    if (list.any { !(it is JsonPrimitive && it.isString) || !Airports.exists(it.content.trim()) }) {
        throw SearchValidationError("$label: uno dei codici aeroporto non esiste.")
    }
    return list.map { (it as JsonPrimitive).content.trim().uppercase() }.distinct()
}

/** Python float(value): a number, or null where Python raises. */
fun pythonFloat(value: JsonElement?): Double? {
    val p = value as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (!p.isString) {
        p.booleanOrNull?.let { return if (it) 1.0 else 0.0 }
        return p.content.toDoubleOrNull()
    }
    return pythonFloat(p.content)
}

fun pythonFloat(text: String): Double? {
    val s = text.trim().replace("_", "")
    if (Regex("""[+-]?(nan|inf|infinity)""", RegexOption.IGNORE_CASE).matches(s)) {
        return when {
            "nan" in s.lowercase() -> Double.NaN
            s.startsWith("-") -> Double.NEGATIVE_INFINITY
            else -> Double.POSITIVE_INFINITY
        }
    }
    if (!Regex("""[+-]?(\d+\.?\d*|\.\d+)(e[+-]?\d+)?""", RegexOption.IGNORE_CASE).matches(s)) return null
    return s.toDouble()
}

/** FlexibleRequest.parse: validate the request at one boundary. */
fun parseRequest(data: JsonObject, today: String = Calendar.today()): FlexibleRequest {
    fun get(key: String): JsonElement? = data[key]
    fun str(key: String) = (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun choice(key: String, allowed: List<String>, fallback: String): String {
        val raw = if (key in data) data[key] else JsonPrimitive(fallback)
        val value = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (value == null || value !in allowed) throw SearchValidationError("Valore non valido per $key.")
        return value
    }
    fun bool(key: String): Boolean {
        if (key !in data) return false
        val p = data[key] as? JsonPrimitive
        if (p == null || p.isString || p.booleanOrNull == null) throw SearchValidationError("$key deve essere vero o falso.")
        return p.booleanOrNull!!
    }
    fun int(key: String, fallback: Int, low: Int, high: Int, label: String = key) =
        integer(if (key in data) data[key] else JsonPrimitive(fallback), label, low, high)

    val fixedOneWay = str("date_mode") == "fixed" && str("trip_type") == "one_way"
    val start = str("start_date")
    val endRaw = if (fixedOneWay) start else str("end_date")
    if (start == null || endRaw == null || !Calendar.isDay(start) || !Calendar.isDay(endRaw)) {
        throw SearchValidationError("Indica inizio e fine del periodo di ricerca.")
    }
    if (start < today || endRaw < start || daysBetween(start, endRaw) > MAX_PERIOD_DAYS) {
        throw SearchValidationError("Periodo non valido: date future, massimo un anno.")
    }
    val origins = airportCodes(if ("origins" in data) data["origins"] else JsonArray(listOf(JsonPrimitive("VCE"), JsonPrimitive("TSF"))), "Partenza", MAX_ORIGINS, required = true)
    val tripType = choice("trip_type", listOf("round_trip", "one_way"), "round_trip")
    val dateMode = choice("date_mode", listOf("fixed", "flexible"), "flexible")
    val end = if (tripType == "one_way" && dateMode == "fixed") start else endRaw
    val all = (0..6).toList()
    val departureDays = if (dateMode == "fixed") all else weekdays(if ("departure_days" in data) get("departure_days") else JsonArray(listOf(JsonPrimitive(4))), "departure_days")
    val returnDays = if (dateMode == "fixed" || tripType == "one_way") all else weekdays(if ("return_days" in data) get("return_days") else JsonArray(listOf(JsonPrimitive(6))), "return_days")
    val clocks = HashMap<String, String>()
    for (prefix in listOf("departure", "return", "departure_arrival", "return_arrival")) {
        for ((suffix, fallback) in listOf("min" to "18:00", "max" to "23:59")) {
            val key = "${prefix}_$suffix"
            val def = if (prefix.endsWith("arrival") && suffix == "min") "00:00" else fallback
            clocks[key] = clock(if (key in data) data[key] else JsonPrimitive(def))
        }
        if (clocks.getValue("${prefix}_min") > clocks.getValue("${prefix}_max")) {
            throw SearchValidationError("La fascia oraria deve stare nello stesso giorno.")
        }
    }
    val nightsIgnored = dateMode == "fixed" || tripType == "one_way"
    val minNights = if (nightsIgnored) 2 else int("min_nights", 2, 0, 30)
    val maxNights = if (nightsIgnored) 2 else int("max_nights", 2, 0, 30)
    val maxStops = int("max_stops", 0, 0, MAX_STOPS)
    val adults = int("adults", 1, 1, 9)
    val children = int("children", 0, 0, 8)
    val infants = int("infants", 0, 0, 8)
    val minHours = int("min_destination_hours", 0, 0, 720)
    if (minNights > maxNights) throw SearchValidationError("Il minimo di notti supera il massimo.")
    if (adults + children + infants > MAX_TRAVELERS) throw SearchValidationError("Massimo 9 viaggiatori.")
    if (infants > adults) throw SearchValidationError("Serve un adulto per ogni neonato in braccio.")
    val budget = pythonFloat(if ("budget" in data) data["budget"] else JsonPrimitive(100)) ?: throw SearchValidationError("Budget non valido.")
    val budgetIsBool = (data["budget"] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull != null } == true
    if (budgetIsBool || !budget.isFinite() || !(budget > 0 && budget <= MAX_BUDGET)) {
        throw SearchValidationError("Il budget totale deve essere positivo e finito.")
    }
    val returnAirport = choice("return_airport", listOf("same", "group"), "same")
    val airlineMode = choice("airline_mode", listOf("all", "low_cost"), "all")
    val baggage = choice("baggage", listOf("base", "cabin", "checked"), "base")
    val returnDeparture = choice("return_departure", listOf("same", "group"), "same")
    val seat = choice("seat", listOf("economy", "premium-economy", "business", "first"), "economy")
    val selfTransfer = bool("allow_self_transfer")
    val airportChange = bool("allow_airport_change")
    val excludeBasic = bool("exclude_basic_economy")
    val schengen = bool("schengen_transfers_only")
    val durations = HashMap<String, Int>()
    for (prefix in listOf("departure_duration", "return_duration")) {
        durations["${prefix}_min"] = int("${prefix}_min", 0, 0, MAX_DURATION_MINUTES, prefix)
        durations["${prefix}_max"] = int("${prefix}_max", MAX_DURATION_MINUTES, 0, MAX_DURATION_MINUTES, prefix)
        if (durations.getValue("${prefix}_min") > durations.getValue("${prefix}_max")) {
            throw SearchValidationError("La durata minima supera quella massima.")
        }
    }
    val minLayover = int("min_layover_minutes", 0, 0, MAX_DURATION_MINUTES, "Durata scalo")
    val maxLayover = int("max_layover_minutes", MAX_DURATION_MINUTES, 0, MAX_DURATION_MINUTES, "Durata scalo")
    if (minLayover > maxLayover) throw SearchValidationError("Lo scalo minimo supera quello massimo.")
    val airlinesRaw = if ("airlines" in data) data["airlines"] else JsonArray(emptyList())
    val airlineList: List<JsonElement>? = when {
        airlinesRaw is JsonPrimitive && airlinesRaw.isString ->
            airlinesRaw.content.trim().let { t -> if (t.isEmpty()) emptyList() else t.split(SEPARATORS).map(::JsonPrimitive) }
        airlinesRaw is JsonArray -> airlinesRaw
        else -> null
    }
    if (airlineList == null || airlineList.any { !(it is JsonPrimitive && it.isString) || !Regex("[A-Z0-9]{2}").matches(it.content.uppercase()) }) {
        throw SearchValidationError("Compagnie: usa codici IATA di due caratteri, es. FR, V7.")
    }
    val airlines = airlineList.map { (it as JsonPrimitive).content.uppercase() }.distinct()
    val finalRaw = data["final_arrival_max"]
    // Python: `data.get("final_arrival_max") or None`, so every falsy value means none.
    val falsy = finalRaw == null || finalRaw is JsonNull ||
        (finalRaw is JsonArray && finalRaw.isEmpty()) || (finalRaw is JsonObject && finalRaw.isEmpty()) ||
        (finalRaw is JsonPrimitive && (if (finalRaw.isString) finalRaw.content.isEmpty() else finalRaw.content in setOf("false", "0", "0.0")))
    val final = if (falsy) null else clock(finalRaw)
    val destinations = airportCodes(if ("destinations" in data) data["destinations"] else JsonArray(emptyList()), "Destinazioni", MAX_DESTINATIONS)
    val destinationSet = (if ("destination_set" in data) str("destination_set") else "real")
    if (destinationSet == null || (destinationSet != "real" && destinationSet !in Airports.sets)) {
        throw SearchValidationError("Gruppo di destinazioni non riconosciuto.")
    }
    if (returnDeparture == "group" && destinations.isEmpty()) {
        throw SearchValidationError("Per ripartire da un altro aeroporto scegli un gruppo di destinazioni esplicito.")
    }
    val request = FlexibleRequest(
        origins = origins, startDate = start, endDate = end, departureDays = departureDays, returnDays = returnDays,
        departureMin = clocks.getValue("departure_min"), departureMax = clocks.getValue("departure_max"),
        returnMin = clocks.getValue("return_min"), returnMax = clocks.getValue("return_max"),
        minNights = minNights, maxNights = maxNights, maxStops = maxStops, allowSelfTransfer = selfTransfer,
        returnAirport = returnAirport, budget = budget, adults = adults, children = children, airlineMode = airlineMode,
        baggage = baggage, finalArrivalMax = final, minDestinationHours = minHours, destinations = destinations,
        currency = "EUR", tripType = tripType, destinationSet = destinationSet, dateMode = dateMode,
        returnDeparture = returnDeparture, seat = seat, infants = infants, airlines = airlines,
        excludeBasicEconomy = excludeBasic, allowAirportChange = airportChange, schengenTransfersOnly = schengen,
        departureArrivalMin = clocks.getValue("departure_arrival_min"), departureArrivalMax = clocks.getValue("departure_arrival_max"),
        returnArrivalMin = clocks.getValue("return_arrival_min"), returnArrivalMax = clocks.getValue("return_arrival_max"),
        minLayoverMinutes = minLayover, maxLayoverMinutes = maxLayover,
        departureDurationMin = durations.getValue("departure_duration_min"), departureDurationMax = durations.getValue("departure_duration_max"),
        returnDurationMin = durations.getValue("return_duration_min"), returnDurationMax = durations.getValue("return_duration_max"),
    )
    if (datePairs(request).isEmpty()) throw SearchValidationError("Nessuna coppia di date rispetta giorni e notti scelti.")
    return request
}

typealias DatePair = Pair<String, String?>

fun datePairs(r: FlexibleRequest): List<DatePair> {
    if (r.dateMode == "fixed") return listOf(r.startDate to (if (r.roundTrip) r.endDate else null))
    val pairs = ArrayList<DatePair>()
    var day = r.startDate
    while (day <= r.endDate) {
        if (weekday(day) in r.departureDays) {
            if (r.tripType == "one_way") {
                pairs.add(day to null)
            } else {
                for (nights in r.minNights..r.maxNights) {
                    val back = addDays(day, nights)
                    if (back <= r.endDate && weekday(back) in r.returnDays) pairs.add(day to back)
                }
            }
        }
        day = addDays(day, 1)
    }
    return pairs
}

/** json.dumps of the segment identities, hashed: the same ids as the Python engine. */
fun fingerprint(segments: List<Segment>): String {
    fun q(s: String) = jsonString(s)
    val parts = segments.joinToString(", ") {
        "[" + listOf(it.fromCode, it.toCode, it.departure, it.arrival, it.airlineCode, it.flightNumber).joinToString(", ") { s -> q(s) } + "]"
    }
    return "[$parts]".encodeUtf8().sha256().hex().substring(0, 24)
}

fun fingerprint(quote: Quote) = fingerprint(quote.segments)

/** A JSON string literal escaped like Python's json.dumps (ASCII only). */
fun jsonString(s: String): String = buildString {
    append('"')
    for (ch in s) {
        when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch.code < 0x20 || ch.code > 0x7E -> append("\\u" + ch.code.toString(16).padStart(4, '0'))
            else -> append(ch)
        }
    }
    append('"')
}

/** Validate every constraint again against returned facts, down to the minute. */
fun matches(quote: Quote, r: FlexibleRequest, origin: String, destination: String, day: String, inbound: Boolean = false): Boolean {
    if (quote.verification != "google_live" || quote.segments.isEmpty()) return false
    val segments = quote.segments
    if (segments.first().fromCode != origin || segments.last().toCode != destination) return false
    if (segments.size - 1 > r.maxStops) return false
    if (!r.allowSelfTransfer && segments.size > 1 && quote.selfTransfer == true) return false
    val links = segments.zipWithNext()
    if (!r.allowAirportChange && links.any { (a, b) -> a.toCode != b.fromCode }) return false
    if (r.schengenTransfersOnly && links.any { (a, b) -> listOf(a.toCode, b.fromCode).any { Airports.country(it) !in SCHENGEN_COUNTRIES } }) {
        return false
    }
    if (quote.currency != r.currency || quote.baggage != r.baggage) return false
    if (!quote.price.isFinite() || quote.price <= 0) return false
    if (quote.travelers != r.travelers) return false
    var previous: String? = null
    for (seg in segments) {
        if (seg.flightNumber.isEmpty() || seg.airlineCode.isEmpty()) return false
        if (r.airlineMode == "low_cost" && seg.airlineCode !in LOW_COST) return false
        if (r.airlines.isNotEmpty() && seg.airlineCode !in r.airlines) return false
        if (!hasOffset(seg.departure) || !hasOffset(seg.arrival)) return false
        val dep = instant(seg.departure)
        val arr = instant(seg.arrival)
        if (arr <= dep || (previous != null && dep < instant(previous))) return false
        if (previous != null) {
            val layover = (dep - instant(previous)) / 60_000.0
            if (!(r.minLayoverMinutes <= layover && layover <= r.maxLayoverMinutes)) return false
        }
        previous = seg.arrival
    }
    val departure = segments.first().departure
    if (localDate(departure) != day) return false
    val (lo, hi) = r.window(inbound)
    val depClock = localClock(departure)
    if (!(clockSeconds(lo) <= depClock && depClock <= clockSeconds(hi))) return false
    val arrival = segments.last().arrival
    val elapsed = (instant(arrival) - instant(departure)) / 60_000.0
    val (dmin, dmax) = r.durations(inbound)
    if (!(dmin <= elapsed && elapsed <= dmax)) return false
    val (amin, amax) = r.arrivals(inbound)
    val arrClock = localClock(arrival)
    if (!(clockSeconds(amin) <= arrClock && arrClock <= clockSeconds(amax))) return false
    val final = r.finalArrivalMax
    if ((inbound || r.tripType == "one_way") && final != null) {
        val zone = Airports.zone(destination) ?: throw IllegalArgumentException("Fuso orario sconosciuto per $destination")
        val deadline = Calendar.localInstantIn("${day}T$final:00", zone)
        if (instant(arrival) > instant(deadline)) return false
    }
    return true
}

fun singleTrip(outbound: Quote, r: FlexibleRequest): Trip? {
    if (outbound.price > r.budget) return null
    return Trip(
        id = fingerprint(outbound), tripType = "one_way", price = outbound.price, currency = r.currency,
        outbound = outbound, inbound = null, usefulMinutes = null, nights = null, ticketing = "one_way",
        checkedAt = outbound.checkedAt,
    )
}

/** Python round(x, 2) for prices. */
fun round2(x: Double): Double = kotlin.math.round(x * 100) / 100

/** Two live one-way prices, never an estimated round-trip lead price. */
fun combine(outbound: Quote, inbound: Quote, r: FlexibleRequest): Trip? {
    val total = round2(outbound.price + inbound.price)
    if (total > r.budget) return null
    val useful = ((instant(inbound.first.departure) - instant(outbound.last.arrival)) / 60_000.0).toLong().toInt()
    if (useful <= 0 || useful < r.minDestinationHours * 60) return null
    return Trip(
        tripType = "round_trip",
        id = fingerprint(outbound) + fingerprint(inbound),
        price = total,
        currency = r.currency,
        outbound = outbound,
        inbound = inbound,
        usefulMinutes = useful,
        nights = daysBetween(localDate(outbound.first.departure), localDate(inbound.first.departure)),
        ticketing = "separate_one_way",
        checkedAt = minOf(outbound.checkedAt, inbound.checkedAt),
        destinationAirportChange = outbound.last.toCode != inbound.first.fromCode,
    )
}

typealias Route = Pair<String, String>

fun returnRoutes(r: FlexibleRequest, origin: String, destination: String): List<Route> {
    if (r.tripType == "one_way") return emptyList()
    val departures = if (r.returnDeparture == "group") r.destinations else listOf(destination)
    val homes = if (r.returnAirport == "group") r.origins else listOf(origin)
    return departures.flatMap { away -> homes.filter { it != away }.map { away to it } }
}

fun airportsPerSearch(r: FlexibleRequest) = if (r.maxStops == 0) MAX_AIRPORTS_PER_SEARCH else 1

typealias Batch = Pair<List<String>, List<String>>

/** Cover exactly the (from, to) pairs with searches of up to `size` airports per side. */
fun pairBatches(input: List<Route>, size: Int): List<Batch> {
    val pairs = input.distinct()
    if (size <= 1) return pairs.map { listOf(it.first) to listOf(it.second) }
    fun grouped(swap: Boolean): List<Batch> {
        val partners = LinkedHashMap<String, MutableList<String>>()
        for ((a, b) in pairs) {
            val (key, other) = if (swap) b to a else a to b
            partners.getOrPut(key) { ArrayList() }.add(other)
        }
        val groups = LinkedHashMap<List<String>, MutableList<String>>()
        for ((key, others) in partners) groups.getOrPut(others) { ArrayList() }.add(key)
        val batches = ArrayList<Batch>()
        for ((others, keys) in groups) {
            for (i in keys.indices step size) {
                for (j in others.indices step size) {
                    val part = keys.subList(i, minOf(i + size, keys.size)).toList() to others.subList(j, minOf(j + size, others.size)).toList()
                    batches.add(if (swap) part.second to part.first else part)
                }
            }
        }
        return batches
    }
    val plain = grouped(false)
    val swapped = grouped(true)
    return if (swapped.size < plain.size) swapped else plain
}

/** Day-by-day work for one request: each flight leg is searched once (Schedule in Python). */
class Schedule(val request: FlexibleRequest, destinations: List<String>, today: String = Calendar.today()) {
    val size = airportsPerSearch(request)
    val horizon = addDays(today, GOOGLE_HORIZON_DAYS)
    val datePairs: List<DatePair>
    val unsoldFrom: String?
    val backDays: List<String>
    val days: List<String>
    val outDays: Set<String>
    val outsFor = LinkedHashMap<String, MutableList<String>>()
    val lastBack = HashMap<String, String>()
    val routes: List<Route> = destinations.flatMap { d -> request.origins.filter { it != d }.map { it to d } }
    val outBatches: List<Batch> = pairBatches(routes, size)
    val returns: List<Route> = routes.flatMap { (o, d) -> returnRoutes(request, o, d) }.distinct()
    val backBound: Int = pairBatches(returns, size).size
    val total: Int

    init {
        val pairs = datePairs(request)
        datePairs = pairs.filter { (out, back) -> (back ?: out) <= horizon }
        unsoldFrom = pairs.filter { it !in datePairs }.map { (out, back) -> back ?: out }.minOrNull()
        val out = datePairs.map { it.first }.toSortedSet().toList()
        backDays = datePairs.mapNotNull { it.second }.toSortedSet().toList()
        days = (out + backDays).toSortedSet().toList()
        outDays = out.toSet()
        for ((o, back) in datePairs) if (back != null) outsFor.getOrPut(back) { ArrayList() }.add(o)
        for ((o, back) in datePairs) {
            val prev = lastBack[o] ?: ""
            val b = back ?: ""
            lastBack[o] = if (prev > b) prev else b
        }
        total = out.size * outBatches.size + backDays.size * backBound
    }

    fun returnBatches(wanted: List<Route>): List<Batch> {
        if (size == 1) return pairBatches(wanted, 1)
        val aways = wanted.map { it.first }.toSet()
        return pairBatches(returns.filter { it.first in aways }, size)
    }

    fun remaining(cursor: List<Int>, returnShare: Double = 1.0): Double {
        val (index, stage, batch) = cursor
        var outs = 0
        var rets = 0
        for (i in index until days.size) {
            val day = days[i]
            val current = i == index
            if (day in outDays && !(current && stage != 0)) outs += outBatches.size - (if (current) batch else 0)
            rets += backBound * (if (day in outsFor) 1 else 0)
        }
        return outs + rets * returnShare
    }

    fun unitsBefore(dayIndex: Int): Int = days.take(dayIndex).sumOf { day ->
        outBatches.size * (if (day in outDays) 1 else 0) + backBound * (if (day in outsFor) 1 else 0)
    }
}

fun searchPasses(r: FlexibleRequest, destinations: List<String>, today: String = Calendar.today()): List<Schedule> {
    val full = Schedule(r, destinations, today)
    if (r.maxStops == 0 || full.routes.size <= MAX_AIRPORTS_PER_SEARCH) return listOf(full)
    return listOf(Schedule(r.copy(maxStops = 0), destinations, today), full)
}

@Serializable
data class Plan(
    @SerialName("date_pairs") val datePairs: List<List<String?>>,
    val destinations: List<String>,
    @SerialName("candidate_count") val candidateCount: Int,
    val combinations: Int,
    @SerialName("distinct_searches") val distinctSearches: Int,
    @SerialName("searches_upper_bound") val searchesUpperBound: Int,
    @SerialName("airports_per_search") val airportsPerSearch: Int,
    @SerialName("direct_first") val directFirst: Boolean,
    @SerialName("unsold_from") val unsoldFrom: String?,
    val coverage: String,
    @SerialName("origin_route_counts") val originRouteCounts: Map<String, Int>,
    val notice: String,
)

private fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** Present work and coverage before starting; never truncate silently. */
fun buildPlan(r: FlexibleRequest, routeMap: Map<String, List<String>>, today: String = Calendar.today()): Plan {
    var candidates: List<String>
    var source: String
    if (r.destinations.isNotEmpty()) {
        candidates = r.destinations
        source = "Destinazioni indicate da te"
    } else {
        candidates = routeMap.values.flatten().toSortedSet(PY_STRING).toList()
        source = "Destinazioni candidate dai cataloghi delle rotte; copertura non esaustiva"
        if (r.destinationSet != "real") source = "Gruppo di destinazioni: ${Airports.sets.getValue(r.destinationSet).label}"
    }
    candidates = candidates.filter { c -> r.origins.any { it != c } }
    if (candidates.isEmpty()) throw SearchValidationError("Nessuna destinazione candidata disponibile.")
    val pairs = datePairs(r)
    val taskCount = pairs.size * candidates.sumOf { c -> r.origins.count { it != c } }
    if (taskCount > MAX_TASKS) {
        throw SearchValidationError(
            "La ricerca genera ${thousands(taskCount)} combinazioni: massimo ${thousands(MAX_TASKS)}. " +
                "Riduci il periodo, le destinazioni o l'intervallo di notti.",
        )
    }
    val passes = searchPasses(r, candidates, today)
    val schedule = passes.last()
    var legs = 0
    for ((_, back) in pairs) {
        for ((origin, dest) in schedule.routes) {
            legs += 1 + (if (back != null) returnRoutes(r, origin, dest).size else 0)
            if (legs > MAX_TASKS * 2) throw SearchValidationError("Riduci gli aeroporti o le date: troppi controlli di ritorno.")
        }
    }
    val returns = schedule.routes.flatMap { (o, d) -> returnRoutes(r, o, d) }.toSet()
    return Plan(
        datePairs = pairs.map { listOf(it.first, it.second) },
        destinations = candidates,
        candidateCount = candidates.size,
        combinations = taskCount,
        distinctSearches = schedule.outDays.size * schedule.routes.size + schedule.backDays.size * returns.size,
        searchesUpperBound = passes.sumOf { it.total },
        airportsPerSearch = passes.first().size,
        directFirst = passes.size > 1,
        unsoldFrom = schedule.unsoldFrom,
        coverage = source,
        originRouteCounts = r.origins.associateWith { (routeMap[it] ?: emptyList()).size },
        notice = "Gli orari da catalogo non confermano un volo. Solo offerte live complete nei risultati.",
    )
}
