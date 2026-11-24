package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.AirlineMode
import it.gzanee.flightfinder.domain.model.ISO_SECONDS
import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.Segment
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.model.TripType
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/*
 * Verifica dei voli e composizione dei viaggi: port di flexible.py. Ogni vincolo
 * si ricontrolla sui dati restituiti da Google, al minuto.
 */

/** Area Schengen secondo il Consiglio dell'UE (Bulgaria e Romania incluse dal 2025). */
val SCHENGEN_COUNTRIES = setOf(
    "AT", "BE", "BG", "HR", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IT", "LV", "LI", "LT",
    "LU", "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "CH",
)

/** Selezione di compagnie low cost, modificabile. */
val LOW_COST = setOf("FR", "RK", "W4", "W6", "W9", "U2", "EC", "DS", "VY", "V7", "HV", "TO", "PC", "EW")

/** Ora locale di un aeroporto: un orario ambiguo o inesistente al cambio dell'ora viene rifiutato, mai indovinato. */
fun localInstant(wallClock: LocalDateTime, zone: ZoneId): OffsetDateTime {
    val offsets = zone.rules.getValidOffsets(wallClock)
    if (offsets.isEmpty()) throw IllegalArgumentException("Orario locale inesistente.")
    if (offsets.size > 1) throw IllegalArgumentException("Orario ambiguo o inesistente al cambio di ora.")
    return wallClock.atOffset(offsets.single())
}

/**
 * Identificativo di un volo o di una combinazione di voli: lo stesso del portale
 * (sha256 del json.dumps Python degli identificativi dei segmenti).
 */
fun fingerprint(segments: List<Segment>): String {
    val parts = segments.joinToString(", ") { s ->
        listOf(s.fromCode, s.toCode, s.departure.format(ISO_SECONDS), s.arrival.format(ISO_SECONDS), s.airlineCode, s.flightNumber)
            .joinToString(", ", "[", "]") { pythonJsonString(it) }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest("[$parts]".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(24)
}

fun Quote.fingerprint() = fingerprint(segments)

/** Stringa JSON come json.dumps di Python (solo ASCII). */
private fun pythonJsonString(s: String) = buildString {
    append('"')
    for (ch in s) {
        when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch.code < 0x20 || ch.code > 0x7E -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
    }
    append('"')
}

private fun minutes(from: OffsetDateTime, to: OffsetDateTime) = Duration.between(from, to).seconds / 60.0

/** L'offerta rispetta la richiesta per questa tratta e questo giorno? */
fun Quote.matches(request: SearchRequest, catalog: AirportCatalog, origin: String, destination: String, day: java.time.LocalDate, inbound: Boolean = false): Boolean {
    if (segments.isEmpty()) return false
    if (first.fromCode != origin || last.toCode != destination) return false
    if (stops > request.maxStops) return false
    // Google segna i self-transfer e lascia senza segno ogni altra coincidenza, biglietti unici compresi.
    if (!request.allowSelfTransfer && stops > 0 && selfTransfer == true) return false
    val connections = segments.zipWithNext()
    if (!request.allowAirportChange && connections.any { (a, b) -> a.toCode != b.fromCode }) return false
    if (request.schengenTransfersOnly && connections.any { (a, b) -> listOf(a.toCode, b.fromCode).any { catalog.country(it) !in SCHENGEN_COUNTRIES } }) {
        return false
    }
    if (currency != request.currency || baggage != request.baggage) return false
    if (!price.isFinite() || price <= 0) return false
    if (travelers != request.travelers) return false
    var previous: OffsetDateTime? = null
    for (segment in segments) {
        if (segment.flightNumber.isEmpty() || segment.airlineCode.isEmpty()) return false
        if (request.airlineMode == AirlineMode.LOW_COST && segment.airlineCode !in LOW_COST) return false
        if (request.airlines.isNotEmpty() && segment.airlineCode !in request.airlines) return false
        if (!segment.arrival.isAfter(segment.departure) || (previous != null && segment.departure.isBefore(previous))) return false
        if (previous != null && minutes(previous, segment.departure) !in request.minLayoverMinutes.toDouble()..request.maxLayoverMinutes.toDouble()) {
            return false
        }
        previous = segment.arrival
    }
    val departure = first.departure
    val arrival = last.arrival
    if (departure.toLocalDate() != day) return false
    if (departure.toLocalTime() !in request.departureWindow(inbound).let { it.start..it.endInclusive }) return false
    val durations = request.durationRange(inbound)
    if (minutes(departure, arrival) !in durations.first.toDouble()..durations.last.toDouble()) return false
    if (arrival.toLocalTime() !in request.arrivalWindow(inbound)) return false
    val deadline = request.finalArrivalMax
    if ((inbound || request.tripType == TripType.ONE_WAY) && deadline != null) {
        val zone = ZoneId.of(catalog.zone(destination) ?: throw IllegalArgumentException("Fuso orario sconosciuto per $destination"))
        if (arrival.isAfter(localInstant(day.atTime(deadline), zone))) return false
    }
    return true
}

/** Viaggio di sola andata, se rientra nel budget. */
fun singleTrip(outbound: Quote, request: SearchRequest): Trip? {
    if (outbound.price > request.budget) return null
    return Trip(
        id = outbound.fingerprint(), tripType = TripType.ONE_WAY, price = outbound.price, currency = request.currency,
        outbound = outbound, checkedAt = outbound.checkedAt,
    )
}

/** Prezzo arrotondato al centesimo, come round(x, 2) di Python. */
fun roundCents(x: Double) = (x * 100).roundToLong() / 100.0

/** Due prezzi live di sola andata: mai un prezzo A/R stimato. */
fun combine(outbound: Quote, inbound: Quote, request: SearchRequest): Trip? {
    val total = roundCents(outbound.price + inbound.price)
    if (total > request.budget) return null
    val useful = minutes(outbound.last.arrival, inbound.first.departure).toLong().toInt()
    if (useful <= 0 || useful < request.minDestinationHours * 60) return null
    return Trip(
        id = outbound.fingerprint() + inbound.fingerprint(),
        tripType = TripType.ROUND_TRIP,
        price = total,
        currency = request.currency,
        outbound = outbound,
        inbound = inbound,
        usefulMinutes = useful,
        nights = ChronoUnit.DAYS.between(outbound.first.departure.toLocalDate(), inbound.first.departure.toLocalDate()).toInt(),
        checkedAt = minOf(outbound.checkedAt, inbound.checkedAt),
        destinationAirportChange = outbound.last.toCode != inbound.first.fromCode,
    )
}
