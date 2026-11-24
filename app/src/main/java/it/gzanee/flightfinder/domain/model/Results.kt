@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package it.gzanee.flightfinder.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalTime

enum class SortOrder(val label: String) {
    PRICE("Prezzo più basso"),
    USEFUL("Più tempo a destinazione"),
    DURATION("Voli più brevi"),
    DEPARTURE("Partenza più vicina"),
}

/** Filtri sui viaggi salvati: lavorano senza nuove richieste a Google. */
data class ResultFilters(
    val maxPrice: Double? = null,
    val minHours: Double? = null,
    val origin: String? = null,
    /** Aeroporti di una città (es. tutti quelli di Londra). */
    val destinations: Set<String> = emptySet(),
    val airline: String? = null,
    val homeBefore: LocalTime? = null,
    val excluded: Set<String> = emptySet(),
) {
    val activeCount get() = listOf(maxPrice != null, (minHours ?: 0.0) > 0, origin != null, destinations.isNotEmpty(), airline != null, homeBefore != null, excluded.isNotEmpty()).count { it }
}

@Serializable
data class TripSummary(
    val id: String,
    val origin: String,
    val destination: String,
    val date: LocalDate,
    val returnDate: LocalDate?,
    val price: Double,
    val usefulMinutes: Int?,
    val destinationAirportChange: Boolean,
    val needsRecheck: Boolean,
    val extraPrice: Double? = null,
    val extraMinutes: Int? = null,
)

@Serializable
data class AirlineFacet(val code: String, val name: String)

@Serializable
data class Facets(val origins: List<String>, val destinations: List<String>, val airlines: List<AirlineFacet>)

/** I tre suggerimenti: più economico, più tempo a destinazione, miglior compromesso. */
@Serializable
data class Recommendations(val cheapest: TripSummary? = null, val longest: TripSummary? = null, val balanced: TripSummary? = null)

/** Una città di destinazione: Londra raggruppa STN, LTN, LGW e LHR. */
@Serializable
data class DestinationGroup(
    val city: String,
    val destinations: List<String>,
    val count: Int,
    val cheapest: TripSummary,
    val featured: TripSummary,
)

@Serializable
data class CalendarPrice(val date: LocalDate, val returnDate: LocalDate?, val price: Double)

/** Una pagina di risultati esplorati, con riepiloghi calcolati su tutti i viaggi filtrati. */
@Serializable
data class ResultPage(
    val resultCount: Int,
    val filteredCount: Int,
    val facets: Facets,
    val groups: List<DestinationGroup>,
    val recommendations: Recommendations,
    val calendar: List<CalendarPrice>,
    val results: List<Trip>,
    val offset: Int,
    val hasMore: Boolean,
    val focusedId: String?,
)

data class AirportDetails(val code: String, val city: String, val name: String, val country: String)
