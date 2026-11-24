package it.gzanee.flightfinder.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Browse saved trips without provider calls: port of flightcore/result_browser.py. */

const val PAGE_SIZE = 50
private const val MAX_EXTRA_EUROS = 20.0
private const val MAX_EXTRA_SHARE = 0.5
private const val MAX_EUROS_PER_HOUR = 3.0

private fun number(value: String?, label: String): Double? {
    if (value.isNullOrEmpty()) return null
    val result = pythonFloat(value)
    if (result == null || !result.isFinite() || result < 0) throw SearchValidationError("$label: indica un numero positivo.")
    return result
}

private fun code(value: String?, length: Int): String {
    val v = (value ?: "").uppercase()
    if (v.isNotEmpty() && !Regex("[A-Z0-9]{$length}").matches(v)) throw SearchValidationError("Codice aeroporto o compagnia non valido.")
    return v
}

private fun codes(value: String?): List<String> = (value ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { code(it, 3) }

data class ResultFilters(
    val maxPrice: Double? = null,
    val minHours: Double? = null,
    val origin: String = "",
    val destination: String = "",
    val airline: String = "",
    val homeBefore: String = "",
    val excluded: List<String> = emptyList(),
) {
    companion object {
        /** Keys as in the web page: max_price, min_hours, origin, destination, airline, home_before, exclude. */
        fun parse(args: Map<String, String?> = emptyMap()): ResultFilters {
            val homeBefore = args["home_before"] ?: ""
            if (homeBefore.isNotEmpty() && !Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(homeBefore)) {
                throw SearchValidationError("Orario di rientro non valido.")
            }
            return ResultFilters(
                maxPrice = number(args["max_price"], "Prezzo massimo"),
                minHours = number(args["min_hours"], "Ore minime"),
                origin = code(args["origin"], 3),
                destination = codes(args["destination"]).joinToString(","),
                airline = code(args["airline"], 2),
                homeBefore = homeBefore,
                excluded = codes(args["exclude"]),
            )
        }
    }

    fun matches(trip: Trip): Boolean {
        val out = trip.outbound.segments
        val destination = out.last().toCode
        return (maxPrice == null || trip.price <= maxPrice) &&
            (minHours == null || minHours == 0.0 || (trip.usefulMinutes ?: 0) >= minHours * 60) &&
            (origin.isEmpty() || out.first().fromCode == origin) &&
            (this.destination.isEmpty() || destination in this.destination.split(",")) &&
            destination !in excluded &&
            (airline.isEmpty() || segments(trip).all { it.airlineCode == airline }) &&
            matchesReturn(trip)
    }

    private fun matchesReturn(trip: Trip): Boolean {
        if (homeBefore.isEmpty()) return true
        val back = trip.inbound?.segments ?: return false
        // Compare local calendar dates as well as the clock.
        return back.last().arrival.take(16) <= back.first().departure.take(10) + "T" + homeBefore
    }

    val active get() = listOf(maxPrice != null, minHours != null && minHours > 0, origin.isNotEmpty(), destination.isNotEmpty(), airline.isNotEmpty(), homeBefore.isNotEmpty(), excluded.isNotEmpty()).count { it }
}

fun segments(trip: Trip): List<Segment> = listOfNotNull(trip.outbound, trip.inbound).flatMap { it.segments }

/** sort_key as a comparator, ties broken by id like the Python tuples. */
fun sortComparator(sort: String): Comparator<Trip> = when (sort) {
    "useful" -> compareBy<Trip>({ -(it.usefulMinutes ?: 0) }, { it.price }).thenBy(PY_STRING) { it.id }
    "duration" -> compareBy<Trip>({ it.outbound.durationMin + (it.inbound?.durationMin ?: 0) }, { it.price }).thenBy(PY_STRING) { it.id }
    "departure" -> compareBy<Trip, String>(PY_STRING) { it.outbound.first.departure }.thenBy { it.price }.thenBy(PY_STRING) { it.id }
    else -> compareBy<Trip>({ it.price }, { -(it.usefulMinutes ?: 0) }).thenBy(PY_STRING) { it.id }
}

@Serializable
data class TripSummary(
    val id: String,
    val origin: String,
    val destination: String,
    val date: String,
    @SerialName("return_date") val returnDate: String?,
    val price: Double,
    @SerialName("useful_minutes") val usefulMinutes: Int?,
    @SerialName("destination_airport_change") val destinationAirportChange: Boolean,
    @SerialName("needs_recheck") val needsRecheck: Boolean,
    @SerialName("extra_price") val extraPrice: Double? = null,
    @SerialName("extra_minutes") val extraMinutes: Int? = null,
)

fun summary(trip: Trip) = TripSummary(
    id = trip.id,
    origin = trip.outbound.first.fromCode,
    destination = trip.outbound.last.toCode,
    date = trip.outbound.first.departure.take(10),
    returnDate = trip.inbound?.first?.departure?.take(10),
    price = trip.price,
    usefulMinutes = trip.usefulMinutes,
    destinationAirportChange = trip.destinationAirportChange ?: false,
    needsRecheck = trip.needsRecheck ?: false,
)

@Serializable
data class Airline(val code: String, val name: String)

@Serializable
data class Facets(val origins: List<String>, val destinations: List<String>, val airlines: List<Airline>)

fun facets(trips: List<Trip>): Facets {
    val carriers = LinkedHashMap<String, String>()
    for (t in trips) for (s in segments(t)) carriers[s.airlineCode] = s.airlineName.ifEmpty { s.airlineCode }
    return Facets(
        origins = trips.map { it.outbound.first.fromCode }.distinct().sortedWith(PY_STRING),
        destinations = trips.map { it.outbound.last.toCode }.distinct().sortedWith(PY_STRING),
        airlines = carriers.entries.sortedWith(compareBy(PY_STRING) { it.value }).map { Airline(it.key, it.value) },
    )
}

@Serializable
data class Recommendations(val cheapest: TripSummary? = null, val longest: TripSummary? = null, val balanced: TripSummary? = null)

fun recommendations(trips: List<Trip>): Recommendations {
    if (trips.isEmpty()) return Recommendations()
    val cheapest = trips.minWith(sortComparator("price"))
    val comparable = trips.filter { it.inbound != null && (it.usefulMinutes ?: 0) != 0 && it.destinationAirportChange != true }
    if (comparable.isEmpty()) return Recommendations(cheapest = summary(cheapest))
    val longest = summary(comparable.minWith(sortComparator("useful")))
    if (cheapest !in comparable) return Recommendations(summary(cheapest), longest)
    val base = cheapest.usefulMinutes!!
    val allowance = maxOf(MAX_EXTRA_EUROS, cheapest.price * MAX_EXTRA_SHARE)
    val better = comparable.filter {
        val u = it.usefulMinutes!!
        u > base && it.price <= cheapest.price + allowance && it.price - cheapest.price <= (u - base) / 60.0 * MAX_EUROS_PER_HOUR
    }
    if (better.isEmpty()) return Recommendations(summary(cheapest), longest)
    val best = better.minWith(compareBy<Trip>({ it.price / it.usefulMinutes!! }, { it.price }).thenBy(PY_STRING) { it.id })
    return Recommendations(
        summary(cheapest), longest,
        summary(best).copy(extraPrice = round2(best.price - cheapest.price), extraMinutes = best.usefulMinutes!! - base),
    )
}

@Serializable
data class Group(
    val city: String,
    val destinations: List<String>,
    val destination: String,
    val count: Int,
    val cheapest: TripSummary,
    val featured: TripSummary,
)

/** One entry per destination city: London groups STN, LTN, LGW and LHR. */
fun groups(trips: List<Trip>): List<Group> {
    class G(val featured: TripSummary, var cheapest: Trip) {
        val destinations = HashSet<String>()
        var count = 0
    }
    val grouped = LinkedHashMap<String, G>()
    val byPrice = sortComparator("price")
    for (trip in trips) {
        val destination = trip.outbound.last.toCode
        val g = grouped.getOrPut(Airports.details(destination).city) { G(summary(trip), trip) }
        g.destinations.add(destination)
        g.count += 1
        if (byPrice.compare(trip, g.cheapest) < 0) g.cheapest = trip
    }
    return grouped.map { (city, g) ->
        val destinations = g.destinations.sortedWith(PY_STRING)
        Group(city, destinations, destinations.joinToString(","), g.count, summary(g.cheapest), g.featured)
    }
}

@Serializable
data class CalendarPrice(val date: String, @SerialName("return_date") val returnDate: String?, val price: Double)

fun calendarPrices(trips: List<Trip>): List<CalendarPrice> {
    val calendar = LinkedHashMap<Pair<String, String?>, CalendarPrice>()
    for (trip in trips) {
        val s = summary(trip)
        val key = s.date to s.returnDate
        val prev = calendar[key]
        if (prev == null || s.price < prev.price) calendar[key] = CalendarPrice(s.date, s.returnDate, s.price)
    }
    return calendar.values.sortedWith(compareBy<CalendarPrice, String>(PY_STRING) { it.date }.thenBy(PY_STRING) { it.returnDate ?: "" })
}

@Serializable
data class Browse(
    @SerialName("result_count") val resultCount: Int,
    @SerialName("filtered_count") val filteredCount: Int,
    val facets: Facets,
    val groups: List<Group>,
    val recommendations: Recommendations,
    val calendar: List<CalendarPrice>,
    val results: List<Trip>,
    val offset: Int,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("focused_id") val focusedId: String?,
)

fun browseResults(trips: List<Trip>, sort: String = "price", offset: Int = 0, filters: ResultFilters = ResultFilters(), focus: String = ""): Browse {
    val filtered = trips.filter(filters::matches).sortedWith(sortComparator(sort))
    val focused = filtered.indexOfFirst { it.id == focus }
    var start = if (focused >= 0) focused / PAGE_SIZE * PAGE_SIZE else offset
    start = minOf(start, maxOf(0, Math.floorDivCompat(filtered.size - 1, PAGE_SIZE) * PAGE_SIZE))
    return Browse(
        resultCount = trips.size,
        filteredCount = filtered.size,
        facets = facets(trips),
        groups = groups(filtered),
        recommendations = recommendations(filtered),
        calendar = calendarPrices(filtered),
        results = filtered.drop(start).take(PAGE_SIZE),
        offset = start,
        hasMore = start + PAGE_SIZE < filtered.size,
        focusedId = if (focused >= 0) focus else null,
    )
}

private object Math {
    fun floorDivCompat(a: Int, b: Int) = a.floorDiv(b)
}
