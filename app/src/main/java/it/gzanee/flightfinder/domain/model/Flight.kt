@file:UseSerializers(OffsetDateTimeSerializer::class, InstantSerializer::class)

package it.gzanee.flightfinder.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.OffsetDateTime

/** Un volo: aeroporti, orari locali con fuso, compagnia e numero. */
@Serializable
data class Segment(
    val fromCode: String,
    val toCode: String,
    val departure: OffsetDateTime,
    val arrival: OffsetDateTime,
    val airlineCode: String,
    val airlineName: String,
    val flightNumber: String,
    val durationMin: Int,
)

/**
 * Un'offerta di sola andata vista su Google: uno o più voli e il prezzo totale per
 * tutti i viaggiatori. Non è una conferma della compagnia.
 */
@Serializable
data class Quote(
    val segments: List<Segment>,
    val price: Double = 0.0,
    val currency: String = "EUR",
    val durationMin: Int,
    val selfTransfer: Boolean? = null,
    val checkedAt: Instant = Instant.EPOCH,
    val baggage: Baggage = Baggage.BASE,
    val travelers: Int = 1,
    val seat: Seat = Seat.ECONOMY,
    val bookingUrl: String = "",
) {
    val first get() = segments.first()
    val last get() = segments.last()
    val stops get() = segments.size - 1
}

/** Un viaggio trovato: sola andata, oppure due biglietti di sola andata (A/R). */
@Serializable
data class Trip(
    val id: String,
    val tripType: TripType = TripType.ROUND_TRIP,
    val price: Double,
    val currency: String = "EUR",
    val outbound: Quote,
    val inbound: Quote? = null,
    val usefulMinutes: Int? = null,
    val nights: Int? = null,
    val checkedAt: Instant = Instant.EPOCH,
    val destinationAirportChange: Boolean? = null,
    // Aggiunti quando i risultati vengono mostrati.
    val needsRecheck: Boolean? = null,
    val roundTripFrom: Double? = null,
    val roundTripUrl: String? = null,
) {
    val origin get() = outbound.first.fromCode
    val destination get() = outbound.last.toCode
    val segments get() = outbound.segments + (inbound?.segments ?: emptyList())
}
