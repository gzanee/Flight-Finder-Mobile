package it.gzanee.flightfinder.app.core

import it.gzanee.flightfinder.engine.Calendar
import it.gzanee.flightfinder.engine.FlexibleRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val NO_BUDGET = 100000.0
const val NO_LIMIT_MINUTES = 4320

/** A destination chip: one airport, or a whole country holding its airports. */
sealed interface Place {
    val label: String
    data class Airport(val code: String, override val label: String) : Place
    data class Country(val country: String, override val label: String, val codes: List<String>) : Place
}

class FormError(message: String) : Exception(message)

/** Search form state: port of values()/restoreForm() of the portal's static/weekends.js. */
data class FormState(
    val tripType: String = "round_trip",
    val dateMode: String = "flexible",
    val origins: List<Place> = listOf(Place.Airport("VCE", "Venezia"), Place.Airport("TSF", "Treviso")),
    val anywhere: Boolean = true,
    val destinationSet: String = "real",
    val destinations: List<Place> = emptyList(),
    val startDate: String,
    val endDate: String,
    val departureDays: List<Int> = listOf(4),
    val returnDays: List<Int> = listOf(6),
    val minNights: Int = 2,
    val maxNights: Int = 2,
    val departureMin: String = "00:00",
    val departureMax: String = "23:59",
    val returnMin: String = "00:00",
    val returnMax: String = "23:59",
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    val seat: String = "economy",
    val baggage: String = "base",
    val budget: String = "",
    val maxStops: Int = 1,
    val departureArrivalMin: String = "00:00",
    val departureArrivalMax: String = "23:59",
    val returnArrivalMin: String = "00:00",
    val returnArrivalMax: String = "23:59",
    val finalArrival: String = "",
    val minHours: Int = 0,
    val departureDurationMin: String = "0",
    val departureDurationMax: String = "",
    val returnDurationMin: String = "0",
    val returnDurationMax: String = "",
    val minLayover: String = "0",
    val maxLayover: String = "",
    val airlineMode: String = "all",
    val airlines: String = "",
    val excludeBasicEconomy: Boolean = false,
    val selfTransfer: Boolean = false,
    val airportChange: Boolean = false,
    val schengenTransfers: Boolean = false,
    val returnAirport: String = "same",
    val returnDeparture: String = "same",
) {
    val round get() = tripType == "round_trip"
    val flexible get() = dateMode == "flexible"

    /** "Un weekend": Friday after 17, back on Sunday after 17, two nights. */
    fun weekend() = copy(
        tripType = "round_trip", dateMode = "flexible", departureDays = listOf(4), returnDays = listOf(6), minNights = 2, maxNights = 2,
        departureMin = "17:00", departureMax = "23:59", returnMin = "17:00", returnMax = "23:59",
    )

    fun anytime() = copy(
        departureDays = (0..6).toList(), returnDays = (0..6).toList(),
        departureMin = "00:00", departureMax = "23:59", returnMin = "00:00", returnMax = "23:59",
    )

    /** Switching to exact dates keeps a sensible return, as on the web page. */
    fun withDateMode(mode: String) =
        if (mode == "fixed" && endDate > Calendar.addDays(startDate, 14)) copy(dateMode = mode, endDate = Calendar.addDays(startDate, 3)) else copy(dateMode = mode)

    fun destinationCodes(): List<String> = destinations.flatMap { if (it is Place.Country) it.codes else listOf((it as Place.Airport).code) }.distinct()

    /** The request sent to the engine, which validates it. */
    fun toSpec(): JsonObject {
        if (origins.isEmpty()) throw FormError("Da dove parti? Aggiungi almeno un aeroporto.")
        val dests = if (anywhere) emptyList() else destinationCodes()
        if (!anywhere && dests.isEmpty()) throw FormError("Dove vuoi andare? Aggiungi una destinazione oppure scegli Ovunque.")
        fun num(value: String, fallback: Double) = value.trim().replace(',', '.').let { if (it.isEmpty()) fallback else it.toDoubleOrNull() ?: Double.NaN }
        fun s(v: String) = JsonPrimitive(v)
        fun n(v: Number) = JsonPrimitive(v)
        fun list(v: List<Any>) = JsonArray(v.map { if (it is Number) JsonPrimitive(it) else JsonPrimitive(it.toString()) })
        val budgetValue = num(budget, NO_BUDGET)
        val spec = linkedMapOf<String, JsonElement>(
            "origins" to list(origins.map { (it as Place.Airport).code }),
            "destinations" to list(dests),
            "trip_type" to s(tripType),
            "date_mode" to s(dateMode),
            "destination_set" to s(destinationSet),
            "start_date" to s(startDate),
            "end_date" to s(if (round || flexible) endDate else startDate),
            "departure_days" to list(departureDays),
            "return_days" to list(returnDays),
            "min_nights" to n(if (round && flexible) minNights else 2),
            "max_nights" to n(if (round && flexible) maxNights else 2),
            "budget" to if (budgetValue.isNaN()) s(budget) else n(budgetValue),
            "adults" to n(adults), "children" to n(children), "infants" to n(infants),
            "max_stops" to n(maxStops),
            "return_airport" to s(if (round) returnAirport else "same"),
            "return_departure" to s(if (round && !anywhere) returnDeparture else "same"),
            "baggage" to s(baggage), "seat" to s(seat), "airline_mode" to s(airlineMode), "airlines" to s(airlines),
            "allow_self_transfer" to JsonPrimitive(selfTransfer), "allow_airport_change" to JsonPrimitive(airportChange),
            "exclude_basic_economy" to JsonPrimitive(excludeBasicEconomy), "schengen_transfers_only" to JsonPrimitive(schengenTransfers),
            "final_arrival_max" to if (finalArrival.isEmpty()) JsonNull else s(finalArrival),
            "min_destination_hours" to n(if (round) minHours else 0),
            "min_layover_minutes" to minutes(minLayover, 0.0),
            "max_layover_minutes" to minutes(maxLayover, NO_LIMIT_MINUTES.toDouble()),
            "departure_min" to s(departureMin), "departure_max" to s(departureMax),
            "return_min" to s(if (round) returnMin else "00:00"), "return_max" to s(if (round) returnMax else "23:59"),
            "departure_arrival_min" to s(departureArrivalMin), "departure_arrival_max" to s(departureArrivalMax),
            "return_arrival_min" to s(if (round) returnArrivalMin else "00:00"), "return_arrival_max" to s(if (round) returnArrivalMax else "23:59"),
            "departure_duration_min" to hours(departureDurationMin, 0.0), "departure_duration_max" to hours(departureDurationMax, 72.0),
            "return_duration_min" to if (round) hours(returnDurationMin, 0.0) else n(0),
            "return_duration_max" to if (round) hours(returnDurationMax, 72.0) else n(NO_LIMIT_MINUTES),
        )
        return JsonObject(spec)
    }

    private fun hours(value: String, fallback: Double): JsonElement {
        val t = value.trim().replace(',', '.')
        val h = if (t.isEmpty()) fallback else t.toDoubleOrNull() ?: return JsonPrimitive(value)
        return JsonPrimitive(kotlin.math.round(h * 60).toLong())
    }

    private fun minutes(value: String, fallback: Double): JsonElement {
        val t = value.trim()
        if (t.isEmpty()) return JsonPrimitive(fallback.toLong())
        return t.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
    }

    companion object {
        fun initial(today: String = Calendar.today()) = FormState(startDate = Calendar.addDays(today, 1), endDate = Calendar.addDays(today, 31))

        /** The form of a saved search, to search again or change it. */
        fun from(r: FlexibleRequest, label: (String) -> String): FormState {
            fun hours(minutes: Int, max: Boolean) = if (max && minutes >= NO_LIMIT_MINUTES) "" else trim(minutes / 60.0)
            return FormState(
                tripType = r.tripType, dateMode = r.dateMode,
                origins = r.origins.map { Place.Airport(it, label(it)) },
                anywhere = r.destinations.isEmpty(), destinationSet = r.destinationSet,
                destinations = r.destinations.map { Place.Airport(it, label(it)) },
                startDate = r.startDate, endDate = r.endDate, departureDays = r.departureDays, returnDays = r.returnDays,
                minNights = r.minNights, maxNights = r.maxNights,
                departureMin = r.departureMin, departureMax = r.departureMax, returnMin = r.returnMin, returnMax = r.returnMax,
                adults = r.adults, children = r.children, infants = r.infants, seat = r.seat, baggage = r.baggage,
                budget = if (r.budget >= NO_BUDGET) "" else trim(r.budget), maxStops = r.maxStops,
                departureArrivalMin = r.departureArrivalMin, departureArrivalMax = r.departureArrivalMax,
                returnArrivalMin = r.returnArrivalMin, returnArrivalMax = r.returnArrivalMax,
                finalArrival = r.finalArrivalMax ?: "", minHours = r.minDestinationHours,
                departureDurationMin = hours(r.departureDurationMin, false), departureDurationMax = hours(r.departureDurationMax, true),
                returnDurationMin = hours(r.returnDurationMin, false), returnDurationMax = hours(r.returnDurationMax, true),
                minLayover = r.minLayoverMinutes.toString(), maxLayover = if (r.maxLayoverMinutes >= NO_LIMIT_MINUTES) "" else r.maxLayoverMinutes.toString(),
                airlineMode = r.airlineMode, airlines = r.airlines.joinToString(", "),
                excludeBasicEconomy = r.excludeBasicEconomy, selfTransfer = r.allowSelfTransfer, airportChange = r.allowAirportChange,
                schengenTransfers = r.schengenTransfersOnly, returnAirport = r.returnAirport, returnDeparture = r.returnDeparture,
            )
        }

        private fun trim(x: Double) = if (x == kotlin.math.floor(x)) x.toLong().toString() else x.toString()
    }
}
