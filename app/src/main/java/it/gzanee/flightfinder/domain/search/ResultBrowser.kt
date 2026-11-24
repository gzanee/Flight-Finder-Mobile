package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.AirlineFacet
import it.gzanee.flightfinder.domain.model.CalendarPrice
import it.gzanee.flightfinder.domain.model.DestinationGroup
import it.gzanee.flightfinder.domain.model.Facets
import it.gzanee.flightfinder.domain.model.ISO_SECONDS
import it.gzanee.flightfinder.domain.model.Recommendations
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.ResultPage
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.model.TripSummary

/*
 * Esplorazione dei viaggi salvati, senza nuove richieste a Google:
 * port di flightcore/result_browser.py.
 */

const val PAGE_SIZE = 50

/** Il miglior compromesso: al massimo 20 € (o il 50% del prezzo) e 3 € per ogni ora guadagnata. */
private const val MAX_EXTRA_EUROS = 20.0
private const val MAX_EXTRA_SHARE = 0.5
private const val MAX_EUROS_PER_HOUR = 3.0

fun ResultFilters.matches(trip: Trip): Boolean {
    if (maxPrice != null && trip.price > maxPrice) return false
    if ((minHours ?: 0.0) > 0 && (trip.usefulMinutes ?: 0) < minHours!! * 60) return false
    if (origin != null && trip.origin != origin) return false
    if (destinations.isNotEmpty() && trip.destination !in destinations) return false
    if (trip.destination in excluded) return false
    if (airline != null && trip.segments.any { it.airlineCode != airline }) return false
    val deadline = homeBefore ?: return true
    // Data e ora locali: l'01:00 del giorno dopo non rispetta un rientro entro le 23:00.
    val back = trip.inbound ?: return false
    val arrival = back.last.arrival.toLocalDateTime().withSecond(0).withNano(0)
    return !arrival.isAfter(back.first.departure.toLocalDate().atTime(deadline))
}

fun SortOrder.comparator(): Comparator<Trip> = when (this) {
    SortOrder.USEFUL -> compareBy<Trip>({ -(it.usefulMinutes ?: 0) }, { it.price }, { it.id })
    SortOrder.DURATION -> compareBy({ it.outbound.durationMin + (it.inbound?.durationMin ?: 0) }, { it.price }, { it.id })
    // Come il portale: l'orario locale scritto con il suo fuso, confrontato come testo.
    SortOrder.DEPARTURE -> compareBy({ it.outbound.first.departure.format(ISO_SECONDS) }, { it.price }, { it.id })
    SortOrder.PRICE -> compareBy({ it.price }, { -(it.usefulMinutes ?: 0) }, { it.id })
}

fun Trip.summary() = TripSummary(
    id = id,
    origin = origin,
    destination = destination,
    date = outbound.first.departure.toLocalDate(),
    returnDate = inbound?.first?.departure?.toLocalDate(),
    price = price,
    usefulMinutes = usefulMinutes,
    destinationAirportChange = destinationAirportChange ?: false,
    needsRecheck = needsRecheck ?: false,
)

fun facets(trips: List<Trip>): Facets {
    val carriers = LinkedHashMap<String, String>()
    for (trip in trips) for (s in trip.segments) carriers[s.airlineCode] = s.airlineName.ifEmpty { s.airlineCode }
    return Facets(
        origins = trips.map { it.origin }.distinct().sorted(),
        destinations = trips.map { it.destination }.distinct().sorted(),
        airlines = carriers.entries.sortedBy { it.value }.map { AirlineFacet(it.key, it.value) },
    )
}

fun recommendations(trips: List<Trip>): Recommendations {
    if (trips.isEmpty()) return Recommendations()
    val cheapest = trips.minWith(SortOrder.PRICE.comparator())
    val comparable = trips.filter { it.inbound != null && (it.usefulMinutes ?: 0) != 0 && it.destinationAirportChange != true }
    if (comparable.isEmpty()) return Recommendations(cheapest = cheapest.summary())
    val longest = comparable.minWith(SortOrder.USEFUL.comparator()).summary()
    if (cheapest !in comparable) return Recommendations(cheapest.summary(), longest)
    val base = cheapest.usefulMinutes!!
    val allowance = maxOf(MAX_EXTRA_EUROS, cheapest.price * MAX_EXTRA_SHARE)
    val balanced = comparable.filter {
        val gained = it.usefulMinutes!! - base
        gained > 0 && it.price <= cheapest.price + allowance && it.price - cheapest.price <= gained / 60.0 * MAX_EUROS_PER_HOUR
    }.minWithOrNull(compareBy({ it.price / it.usefulMinutes!! }, { it.price }, { it.id }))
        ?: return Recommendations(cheapest.summary(), longest)
    return Recommendations(
        cheapest.summary(), longest,
        balanced.summary().copy(extraPrice = roundCents(balanced.price - cheapest.price), extraMinutes = balanced.usefulMinutes!! - base),
    )
}

fun groups(trips: List<Trip>, catalog: AirportCatalog): List<DestinationGroup> {
    val byPrice = SortOrder.PRICE.comparator()
    return trips.groupBy { catalog.city(it.destination) }.map { (city, list) ->
        DestinationGroup(
            city = city,
            destinations = list.map { it.destination }.distinct().sorted(),
            count = list.size,
            cheapest = list.minWith(byPrice).summary(),
            featured = list.first().summary(),
        )
    }
}

fun calendarPrices(trips: List<Trip>): List<CalendarPrice> = trips.map { it.summary() }
    .groupBy { it.date to it.returnDate }
    .map { (key, list) -> CalendarPrice(key.first, key.second, list.minOf { it.price }) }
    .sortedWith(compareBy({ it.date }, { it.returnDate?.toString() ?: "" }))

/** Una pagina di risultati; filtri, gruppi, suggerimenti e calendario valgono su tutti i viaggi filtrati. */
fun browseResults(
    trips: List<Trip>, catalog: AirportCatalog, sort: SortOrder = SortOrder.PRICE, offset: Int = 0,
    filters: ResultFilters = ResultFilters(), focus: String? = null,
): ResultPage {
    val filtered = trips.filter(filters::matches).sortedWith(sort.comparator())
    val focused = filtered.indexOfFirst { it.id == focus }
    val start = (if (focused >= 0) focused / PAGE_SIZE * PAGE_SIZE else offset)
        .coerceAtMost(maxOf(0, Math.floorDiv(filtered.size - 1, PAGE_SIZE) * PAGE_SIZE))
    return ResultPage(
        resultCount = trips.size,
        filteredCount = filtered.size,
        facets = facets(trips),
        groups = groups(filtered, catalog),
        recommendations = recommendations(filtered),
        calendar = calendarPrices(filtered),
        results = filtered.drop(start).take(PAGE_SIZE),
        offset = start,
        hasMore = start + PAGE_SIZE < filtered.size,
        focusedId = focus.takeIf { focused >= 0 },
    )
}
