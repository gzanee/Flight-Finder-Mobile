package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.AirportChoice
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.NO_LIMIT_MINUTES
import it.gzanee.flightfinder.domain.model.REAL_ROUTES
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SearchValidationError
import it.gzanee.flightfinder.domain.model.TripType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val MAX_ORIGINS = 10
const val MAX_DESTINATIONS = 200
const val MAX_STOPS = 3
const val MAX_TRAVELERS = 9
const val MAX_BUDGET = 100_000.0
private const val MAX_PERIOD_DAYS = 366

private val AIRLINE_CODE = Regex("[A-Z0-9]{2}")

private fun fail(message: String): Nothing = throw SearchValidationError(message)

private fun Int.checkIn(label: String, range: IntRange): Int =
    if (this in range) this else fail("$label: scegli un valore fra ${range.first} e ${range.last}.")

private fun AirportCatalog.codes(raw: List<String>, label: String, maximum: Int, required: Boolean): List<String> {
    if (raw.size > maximum || (required && raw.isEmpty())) fail("$label: scegli da ${if (required) 1 else 0} a $maximum aeroporti.")
    if (raw.any { !exists(it.trim()) }) fail("$label: uno dei codici aeroporto non esiste.")
    return raw.map { it.trim().uppercase() }.distinct()
}

/**
 * La richiesta controllata e normalizzata, con gli stessi controlli e messaggi
 * di FlexibleRequest.parse del portale. Lancia [SearchValidationError].
 */
fun SearchRequest.validated(catalog: AirportCatalog, today: LocalDate = LocalDate.now()): SearchRequest {
    val fixed = dateMode == DateMode.FIXED
    val oneWay = tripType == TripType.ONE_WAY
    val end = if (fixed && oneWay) startDate else endDate
    if (startDate < today || end < startDate || ChronoUnit.DAYS.between(startDate, end) > MAX_PERIOD_DAYS) {
        fail("Periodo non valido: date future, massimo un anno.")
    }
    val origins = catalog.codes(origins, "Partenza", MAX_ORIGINS, required = true)
    val allDays = DayOfWeek.entries.toList()
    val departureDays = if (fixed) allDays else departureDays.ifEmpty { fail("Seleziona almeno un giorno per departure_days.") }.distinct().sorted()
    val returnDays = if (fixed || oneWay) allDays else returnDays.ifEmpty { fail("Seleziona almeno un giorno per return_days.") }.distinct().sorted()
    for (inbound in listOf(false, true)) {
        if (departureWindow(inbound).isEmpty() || arrivalWindow(inbound).isEmpty()) fail("La fascia oraria deve stare nello stesso giorno.")
    }
    val nightsIgnored = fixed || oneWay
    val minNights = if (nightsIgnored) 2 else minNights.checkIn("min_nights", 0..30)
    val maxNights = if (nightsIgnored) 2 else maxNights.checkIn("max_nights", 0..30)
    maxStops.checkIn("max_stops", 0..MAX_STOPS)
    adults.checkIn("adults", 1..9)
    children.checkIn("children", 0..8)
    infants.checkIn("infants", 0..8)
    minDestinationHours.checkIn("min_destination_hours", 0..720)
    if (minNights > maxNights) fail("Il minimo di notti supera il massimo.")
    if (travelers > MAX_TRAVELERS) fail("Massimo 9 viaggiatori.")
    if (infants > adults) fail("Serve un adulto per ogni neonato in braccio.")
    if (!budget.isFinite() || budget <= 0 || budget > MAX_BUDGET) fail("Il budget totale deve essere positivo e finito.")
    for ((label, min, max) in listOf(
        Triple("departure_duration", departureDurationMin, departureDurationMax),
        Triple("return_duration", returnDurationMin, returnDurationMax),
    )) {
        min.checkIn(label, 0..NO_LIMIT_MINUTES)
        max.checkIn(label, 0..NO_LIMIT_MINUTES)
        if (min > max) fail("La durata minima supera quella massima.")
    }
    minLayoverMinutes.checkIn("Durata scalo", 0..NO_LIMIT_MINUTES)
    maxLayoverMinutes.checkIn("Durata scalo", 0..NO_LIMIT_MINUTES)
    if (minLayoverMinutes > maxLayoverMinutes) fail("Lo scalo minimo supera quello massimo.")
    if (airlines.any { !AIRLINE_CODE.matches(it.uppercase()) }) fail("Compagnie: usa codici IATA di due caratteri, es. FR, V7.")
    val destinations = catalog.codes(destinations, "Destinazioni", MAX_DESTINATIONS, required = false)
    if (destinationSet != REAL_ROUTES && destinationSet !in catalog.destinationSets) fail("Gruppo di destinazioni non riconosciuto.")
    if (returnDeparture == AirportChoice.GROUP && destinations.isEmpty()) {
        fail("Per ripartire da un altro aeroporto scegli un gruppo di destinazioni esplicito.")
    }
    val request = copy(
        origins = origins, endDate = end, departureDays = departureDays, returnDays = returnDays,
        minNights = minNights, maxNights = maxNights, airlines = airlines.map { it.uppercase() }.distinct(),
        destinations = destinations, currency = "EUR",
    )
    if (request.datePairs().isEmpty()) fail("Nessuna coppia di date rispetta giorni e notti scelti.")
    return request
}
