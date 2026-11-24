@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, DayOfWeekSerializer::class)

package it.gzanee.flightfinder.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Serializable
enum class TripType {
    @SerialName("round_trip") ROUND_TRIP,
    @SerialName("one_way") ONE_WAY,
}

@Serializable
enum class DateMode {
    @SerialName("fixed") FIXED,
    @SerialName("flexible") FLEXIBLE,
}

/** Classe di viaggio; `code` è il valore della richiesta a Google. */
@Serializable
enum class Seat(val code: Int, val label: String) {
    @SerialName("economy") ECONOMY(1, "Economy"),
    @SerialName("premium-economy") PREMIUM_ECONOMY(2, "Premium economy"),
    @SerialName("business") BUSINESS(3, "Business"),
    @SerialName("first") FIRST(4, "Prima"),
}

@Serializable
enum class Baggage(val label: String) {
    @SerialName("base") BASE("Solo bagaglio piccolo"),
    @SerialName("cabin") CABIN("Con trolley in cabina"),
    @SerialName("checked") CHECKED("Con valigia in stiva"),
}

@Serializable
enum class AirlineMode {
    @SerialName("all") ALL,
    @SerialName("low_cost") LOW_COST,
}

/** Rientro (o ripartenza) dallo stesso aeroporto o da uno qualsiasi del gruppo. */
@Serializable
enum class AirportChoice {
    @SerialName("same") SAME,
    @SerialName("group") GROUP,
}

/** Gruppo di destinazioni di "Ovunque": le rotte reali o un elenco curato. */
const val REAL_ROUTES = "real"
const val NO_BUDGET = 100_000.0
const val NO_LIMIT_MINUTES = 4320

/**
 * Una ricerca, con i valori predefiniti del portale. Va validata con
 * [it.gzanee.flightfinder.domain.search.validated] prima di usarla.
 */
@Serializable
data class SearchRequest(
    val origins: List<String>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val departureDays: List<DayOfWeek> = listOf(DayOfWeek.FRIDAY),
    val returnDays: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY),
    val departureMin: LocalTime = LocalTime.of(18, 0),
    val departureMax: LocalTime = LocalTime.of(23, 59),
    val returnMin: LocalTime = LocalTime.of(18, 0),
    val returnMax: LocalTime = LocalTime.of(23, 59),
    val minNights: Int = 2,
    val maxNights: Int = 2,
    val maxStops: Int = 0,
    val allowSelfTransfer: Boolean = false,
    val returnAirport: AirportChoice = AirportChoice.SAME,
    val budget: Double = 100.0,
    val adults: Int = 1,
    val children: Int = 0,
    val airlineMode: AirlineMode = AirlineMode.ALL,
    val baggage: Baggage = Baggage.BASE,
    val finalArrivalMax: LocalTime? = null,
    val minDestinationHours: Int = 0,
    val destinations: List<String> = emptyList(),
    val currency: String = "EUR",
    val tripType: TripType = TripType.ROUND_TRIP,
    val destinationSet: String = REAL_ROUTES,
    val dateMode: DateMode = DateMode.FLEXIBLE,
    val returnDeparture: AirportChoice = AirportChoice.SAME,
    val seat: Seat = Seat.ECONOMY,
    val infants: Int = 0,
    val airlines: List<String> = emptyList(),
    val excludeBasicEconomy: Boolean = false,
    val allowAirportChange: Boolean = false,
    val schengenTransfersOnly: Boolean = false,
    val departureArrivalMin: LocalTime = LocalTime.MIDNIGHT,
    val departureArrivalMax: LocalTime = LocalTime.of(23, 59),
    val returnArrivalMin: LocalTime = LocalTime.MIDNIGHT,
    val returnArrivalMax: LocalTime = LocalTime.of(23, 59),
    val minLayoverMinutes: Int = 0,
    val maxLayoverMinutes: Int = NO_LIMIT_MINUTES,
    val departureDurationMin: Int = 0,
    val departureDurationMax: Int = NO_LIMIT_MINUTES,
    val returnDurationMin: Int = 0,
    val returnDurationMax: Int = NO_LIMIT_MINUTES,
) {
    val isRoundTrip get() = tripType == TripType.ROUND_TRIP
    val travelers get() = adults + children + infants

    /** Finestra di partenza, durate e arrivi dell'andata o del ritorno. */
    fun departureWindow(inbound: Boolean) = if (inbound) returnMin..returnMax else departureMin..departureMax
    fun durationRange(inbound: Boolean) = if (inbound) returnDurationMin..returnDurationMax else departureDurationMin..departureDurationMax
    fun arrivalWindow(inbound: Boolean) = if (inbound) returnArrivalMin..returnArrivalMax else departureArrivalMin..departureArrivalMax
}

/** Data di andata e, per l'andata e ritorno, di ritorno. */
@Serializable(with = DatePairSerializer::class)
data class DatePair(val departure: LocalDate, val back: LocalDate?)

class SearchValidationError(message: String) : IllegalArgumentException(message)
