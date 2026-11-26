package it.gzanee.flightfinder.ui.screens.search

import it.gzanee.flightfinder.ui.i18n.countryName
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import androidx.annotation.StringRes
import it.gzanee.flightfinder.ui.i18n.UiText
import it.gzanee.flightfinder.R
import it.gzanee.flightfinder.domain.model.AirlineMode
import it.gzanee.flightfinder.domain.model.AirportChoice
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.NO_BUDGET
import it.gzanee.flightfinder.domain.model.NO_LIMIT_MINUTES
import it.gzanee.flightfinder.domain.model.REAL_ROUTES
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.Seat
import it.gzanee.flightfinder.domain.model.TripType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Una partenza o destinazione scelta: un aeroporto, o un paese intero con i suoi aeroporti. */
sealed interface PlaceChoice {
    val label: String
    val codes: List<String>

    data class Airport(val code: String, override val label: String) : PlaceChoice {
        override val codes get() = listOf(code)
    }

    data class Country(val country: String, override val label: String, override val codes: List<String>) : PlaceChoice
}

/** Un campo del modulo che non si può leggere; il testo si traduce nella schermata. */
class FormError(val text: UiText) : IllegalArgumentException(text.toString())

/** Il modulo di ricerca come lo compila l'utente: i campi di testo restano testo finché si cerca. */
data class SearchFormState(
    val tripType: TripType = TripType.ROUND_TRIP,
    val dateMode: DateMode = DateMode.FLEXIBLE,
    val origins: List<PlaceChoice> = listOf(PlaceChoice.Airport("VCE", "Venezia"), PlaceChoice.Airport("TSF", "Treviso")),
    val anywhere: Boolean = true,
    val destinationSet: String = REAL_ROUTES,
    val destinations: List<PlaceChoice> = emptyList(),
    val startDate: LocalDate = LocalDate.now().plusDays(1),
    val endDate: LocalDate = LocalDate.now().plusDays(31),
    val departureDays: List<DayOfWeek> = listOf(DayOfWeek.FRIDAY),
    val returnDays: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY),
    val minNights: Int = 2,
    val maxNights: Int = 2,
    val departureMin: LocalTime = LocalTime.MIDNIGHT,
    val departureMax: LocalTime = LocalTime.of(23, 59),
    val returnMin: LocalTime = LocalTime.MIDNIGHT,
    val returnMax: LocalTime = LocalTime.of(23, 59),
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    val seat: Seat = Seat.ECONOMY,
    val baggage: Baggage = Baggage.BASE,
    val budget: String = "",
    val maxStops: Int = 1,
    val departureArrivalMin: LocalTime = LocalTime.MIDNIGHT,
    val departureArrivalMax: LocalTime = LocalTime.of(23, 59),
    val returnArrivalMin: LocalTime = LocalTime.MIDNIGHT,
    val returnArrivalMax: LocalTime = LocalTime.of(23, 59),
    val finalArrival: LocalTime? = null,
    val minHours: Int = 0,
    val departureDurationMin: String = "0",
    val departureDurationMax: String = "",
    val returnDurationMin: String = "0",
    val returnDurationMax: String = "",
    val minLayover: String = "0",
    val maxLayover: String = "",
    val airlineMode: AirlineMode = AirlineMode.ALL,
    val airlines: String = "",
    val excludeBasicEconomy: Boolean = false,
    val selfTransfer: Boolean = false,
    val airportChange: Boolean = false,
    val schengenTransfers: Boolean = false,
    val returnAirport: AirportChoice = AirportChoice.SAME,
    val returnDeparture: AirportChoice = AirportChoice.SAME,
) {
    val isRoundTrip get() = tripType == TripType.ROUND_TRIP
    val isFlexible get() = dateMode == DateMode.FLEXIBLE

    /** "Un weekend": venerdì dopo le 17, ritorno domenica dopo le 17, due notti. */
    fun weekend() = copy(
        tripType = TripType.ROUND_TRIP, dateMode = DateMode.FLEXIBLE,
        departureDays = listOf(DayOfWeek.FRIDAY), returnDays = listOf(DayOfWeek.SUNDAY), minNights = 2, maxNights = 2,
        departureMin = LocalTime.of(17, 0), departureMax = LocalTime.of(23, 59), returnMin = LocalTime.of(17, 0), returnMax = LocalTime.of(23, 59),
    )

    fun anyDay() = copy(
        departureDays = DayOfWeek.entries.toList(), returnDays = DayOfWeek.entries.toList(),
        departureMin = LocalTime.MIDNIGHT, departureMax = LocalTime.of(23, 59), returnMin = LocalTime.MIDNIGHT, returnMax = LocalTime.of(23, 59),
    )

    /** Passando alle date esatte, un periodo lungo diventa un ritorno sensato. */
    fun withDateMode(mode: DateMode) =
        if (mode == DateMode.FIXED && endDate > startDate.plusDays(14)) copy(dateMode = mode, endDate = startDate.plusDays(3)) else copy(dateMode = mode)

    fun withStartDate(date: LocalDate) = copy(startDate = date, endDate = if (endDate < date) date.plusDays(if (isFlexible) 30 else 2) else endDate)

    fun destinationCodes() = destinations.flatMap { it.codes }.distinct()

    /** Quanti filtri avanzati sono cambiati rispetto ai valori di partenza (per la riga "Altri filtri"). */
    fun advancedCount(): Int {
        val d = SearchFormState()
        return listOf(
            departureArrivalMin != d.departureArrivalMin || departureArrivalMax != d.departureArrivalMax,
            isRoundTrip && (returnArrivalMin != d.returnArrivalMin || returnArrivalMax != d.returnArrivalMax),
            finalArrival != d.finalArrival,
            isRoundTrip && minHours != d.minHours,
            departureDurationMin != d.departureDurationMin || departureDurationMax != d.departureDurationMax,
            isRoundTrip && (returnDurationMin != d.returnDurationMin || returnDurationMax != d.returnDurationMax),
            minLayover != d.minLayover || maxLayover != d.maxLayover,
            airlineMode != d.airlineMode,
            airlines.isNotBlank(),
            excludeBasicEconomy != d.excludeBasicEconomy,
            selfTransfer != d.selfTransfer,
            airportChange != d.airportChange,
            schengenTransfers != d.schengenTransfers,
            isRoundTrip && returnAirport != d.returnAirport,
            isRoundTrip && !anywhere && returnDeparture != d.returnDeparture,
        ).count { it }
    }

    /** Le etichette dei luoghi scelti nella lingua dell'app (i predefiniti nascono in italiano). */
    fun relabeled(names: PlaceNames): SearchFormState {
        fun PlaceChoice.relabel(): PlaceChoice = when (this) {
            is PlaceChoice.Airport -> copy(label = names.city(code))
            is PlaceChoice.Country -> copy(label = countryName(country))
        }
        return copy(origins = origins.map { it.relabel() }, destinations = destinations.map { it.relabel() })
    }

    /** La richiesta per il motore, che poi la valida. Lancia [FormError] per i campi di testo illeggibili. */
    fun toRequest(): SearchRequest {
        if (origins.isEmpty()) throw FormError(UiText.of(R.string.err_no_origins))
        val destinations = if (anywhere) emptyList() else destinationCodes()
        if (!anywhere && destinations.isEmpty()) throw FormError(UiText.of(R.string.err_no_destinations))
        return SearchRequest(
            origins = origins.flatMap { it.codes },
            destinations = destinations,
            tripType = tripType,
            dateMode = dateMode,
            destinationSet = destinationSet,
            startDate = startDate,
            endDate = if (isRoundTrip || isFlexible) endDate else startDate,
            departureDays = departureDays,
            returnDays = returnDays,
            minNights = minNights,
            maxNights = maxNights,
            budget = number(budget, R.string.field_budget) ?: NO_BUDGET,
            adults = adults,
            children = children,
            infants = infants,
            maxStops = maxStops,
            returnAirport = if (isRoundTrip) returnAirport else AirportChoice.SAME,
            returnDeparture = if (isRoundTrip && !anywhere) returnDeparture else AirportChoice.SAME,
            baggage = baggage,
            seat = seat,
            airlineMode = airlineMode,
            airlines = airlines.split(Regex("""[\s,;]+""")).filter { it.isNotBlank() }.map { it.uppercase() },
            allowSelfTransfer = selfTransfer,
            allowAirportChange = airportChange,
            excludeBasicEconomy = excludeBasicEconomy,
            schengenTransfersOnly = schengenTransfers,
            finalArrivalMax = finalArrival,
            minDestinationHours = if (isRoundTrip) minHours else 0,
            minLayoverMinutes = number(minLayover, R.string.field_min_layover)?.roundToInt() ?: 0,
            maxLayoverMinutes = number(maxLayover, R.string.field_max_layover)?.roundToInt() ?: NO_LIMIT_MINUTES,
            departureMin = departureMin,
            departureMax = departureMax,
            returnMin = if (isRoundTrip) returnMin else LocalTime.MIDNIGHT,
            returnMax = if (isRoundTrip) returnMax else LocalTime.of(23, 59),
            departureArrivalMin = departureArrivalMin,
            departureArrivalMax = departureArrivalMax,
            returnArrivalMin = if (isRoundTrip) returnArrivalMin else LocalTime.MIDNIGHT,
            returnArrivalMax = if (isRoundTrip) returnArrivalMax else LocalTime.of(23, 59),
            departureDurationMin = hours(departureDurationMin, 0.0, R.string.field_outbound_duration),
            departureDurationMax = hours(departureDurationMax, 72.0, R.string.field_outbound_duration),
            returnDurationMin = if (isRoundTrip) hours(returnDurationMin, 0.0, R.string.field_return_duration) else 0,
            returnDurationMax = if (isRoundTrip) hours(returnDurationMax, 72.0, R.string.field_return_duration) else NO_LIMIT_MINUTES,
        )
    }

    companion object {
        private fun number(text: String, @StringRes label: Int): Double? {
            val t = text.trim().replace(',', '.')
            if (t.isEmpty()) return null
            return t.toDoubleOrNull() ?: throw FormError(UiText.of(R.string.err_not_number, UiText.of(label)))
        }

        private fun hours(text: String, fallback: Double, @StringRes label: Int) = ((number(text, label) ?: fallback) * 60).roundToInt()

        private fun trim(x: Double) = if (x % 1 == 0.0) x.toLong().toString() else x.toString()

        /** Il modulo di una ricerca salvata, per rifarla o modificarla; `city` dà il nome nella lingua dell'app. */
        fun from(request: SearchRequest, city: (String) -> String) = SearchFormState(
            tripType = request.tripType,
            dateMode = request.dateMode,
            origins = request.origins.map { PlaceChoice.Airport(it, city(it)) },
            anywhere = request.destinations.isEmpty(),
            destinationSet = request.destinationSet,
            destinations = request.destinations.map { PlaceChoice.Airport(it, city(it)) },
            startDate = request.startDate,
            endDate = request.endDate,
            departureDays = request.departureDays,
            returnDays = request.returnDays,
            minNights = request.minNights,
            maxNights = request.maxNights,
            departureMin = request.departureMin,
            departureMax = request.departureMax,
            returnMin = request.returnMin,
            returnMax = request.returnMax,
            adults = request.adults,
            children = request.children,
            infants = request.infants,
            seat = request.seat,
            baggage = request.baggage,
            budget = if (request.budget >= NO_BUDGET) "" else trim(request.budget),
            maxStops = request.maxStops,
            departureArrivalMin = request.departureArrivalMin,
            departureArrivalMax = request.departureArrivalMax,
            returnArrivalMin = request.returnArrivalMin,
            returnArrivalMax = request.returnArrivalMax,
            finalArrival = request.finalArrivalMax,
            minHours = request.minDestinationHours,
            departureDurationMin = trim(request.departureDurationMin / 60.0),
            departureDurationMax = if (request.departureDurationMax >= NO_LIMIT_MINUTES) "" else trim(request.departureDurationMax / 60.0),
            returnDurationMin = trim(request.returnDurationMin / 60.0),
            returnDurationMax = if (request.returnDurationMax >= NO_LIMIT_MINUTES) "" else trim(request.returnDurationMax / 60.0),
            minLayover = request.minLayoverMinutes.toString(),
            maxLayover = if (request.maxLayoverMinutes >= NO_LIMIT_MINUTES) "" else request.maxLayoverMinutes.toString(),
            airlineMode = request.airlineMode,
            airlines = request.airlines.joinToString(", "),
            excludeBasicEconomy = request.excludeBasicEconomy,
            selfTransfer = request.allowSelfTransfer,
            airportChange = request.allowAirportChange,
            schengenTransfers = request.schengenTransfersOnly,
            returnAirport = request.returnAirport,
            returnDeparture = request.returnDeparture,
        )
    }
}
