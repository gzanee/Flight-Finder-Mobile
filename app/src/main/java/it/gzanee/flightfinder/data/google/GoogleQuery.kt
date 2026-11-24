package it.gzanee.flightfinder.data.google

import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.Seat
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

/**
 * La richiesta `tfs` di Google Flights: il messaggio protobuf che costruisce
 * fast-flights, scritto a mano. Numeri e ordine dei campi seguono flights.proto e
 * il serializzatore protobuf di Python, quindi i byte sono identici a quelli del portale.
 * Più aeroporti per lato ripetono il messaggio Airport (AirportGroupQuery del portale).
 */
data class GoogleQuery(
    val legs: List<Leg>,
    val trip: Trip,
    val seat: Seat,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val currency: String,
    val carryOnBags: Int = 0,
    val checkedBags: Int = 0,
    val hideSeparateAndSelfTransfer: Boolean = false,
    val excludeBasicEconomy: Boolean = false,
    val language: String = "it",
) {
    enum class Trip(val code: Int) { ROUND_TRIP(1), ONE_WAY(2) }

    data class Leg(
        val date: LocalDate,
        val from: List<String>,
        val to: List<String>,
        val maxStops: Int? = null,
        val airlines: List<String> = emptyList(),
        val earliestDepartureHour: Int? = null,
        val latestDepartureHour: Int? = null,
    )

    fun encode(): ByteArray {
        val w = ProtoWriter()
        legs.forEach { w.message(3, it.encode()) }
        w.packed(8, List(adults) { 1 } + List(children) { 2 } + List(infants) { 4 })
        w.int(9, seat.code)
        if (carryOnBags != 0 || checkedBags != 0) w.message(13, ProtoWriter().int(2, carryOnBags).int(3, checkedBags))
        w.bool(17, hideSeparateAndSelfTransfer)
        w.int(19, trip.code)
        w.bool(25, excludeBasicEconomy)
        return w.toByteArray()
    }

    private fun Leg.encode() = ProtoWriter().apply {
        string(2, date.toString())
        int(5, maxStops)
        airlines.forEach { string(6, it) }
        int(8, earliestDepartureHour)
        int(9, latestDepartureHour)
        from.forEach { message(13, ProtoWriter().string(2, it)) }
        to.forEach { message(14, ProtoWriter().string(2, it)) }
    }

    val tfs: String get() = Base64.getEncoder().encodeToString(encode())

    /** La pagina che l'app scarica. */
    val searchUrl get() = "https://www.google.com/travel/flights?tfs=${URLEncoder.encode(tfs, "UTF-8")}&hl=$language&curr=$currency"

    /** Il link che si apre per prenotare (Query.url() di fast-flights). */
    val bookingUrl get() = "https://www.google.com/travel/flights/search?tfs=$tfs&hl=$language&curr=$currency"

    companion object {
        /** LiveQuotes._leg: Google accetta ore intere, quindi il limite superiore si allarga all'ora successiva. */
        fun leg(origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest, inbound: Boolean): Leg {
            val window = request.departureWindow(inbound)
            val upper = window.endInclusive.hour + if (window.endInclusive.minute != 0) 1 else 0
            return Leg(
                date = day, from = origins, to = destinations, maxStops = request.maxStops, airlines = request.airlines,
                earliestDepartureHour = window.start.hour, latestDepartureHour = upper.takeIf { it < 24 },
            )
        }

        /** LiveQuotes._query. */
        fun of(request: SearchRequest, legs: List<Leg>, trip: Trip): GoogleQuery {
            val bags = request.adults + request.children
            return GoogleQuery(
                legs = legs, trip = trip, seat = request.seat, adults = request.adults, children = request.children,
                infants = request.infants, currency = request.currency,
                carryOnBags = if (request.baggage == Baggage.CABIN) bags else 0,
                checkedBags = if (request.baggage == Baggage.CHECKED) bags else 0,
                hideSeparateAndSelfTransfer = !request.allowSelfTransfer,
                excludeBasicEconomy = request.excludeBasicEconomy,
            )
        }

        fun oneWay(origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest, inbound: Boolean = false) =
            of(request, listOf(leg(origins, destinations, day, request, inbound)), Trip.ONE_WAY)

        fun roundTrip(origin: String, destination: String, out: LocalDate, back: LocalDate, request: SearchRequest) = of(
            request,
            listOf(leg(listOf(origin), listOf(destination), out, request, false), leg(listOf(destination), listOf(origin), back, request, true)),
            Trip.ROUND_TRIP,
        )
    }
}

/** Scrittura protobuf minima: varint, stringhe, messaggi annidati, enum impacchettati. */
private class ProtoWriter {
    private val out = java.io.ByteArrayOutputStream()

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun varint(value: Long) {
        var n = value
        do {
            var low = (n and 0x7F).toInt()
            n = n ushr 7
            if (n != 0L) low = low or 0x80
            out.write(low)
        } while (n != 0L)
    }

    private fun tag(field: Int, wire: Int) = varint((field shl 3 or wire).toLong())

    fun int(field: Int, value: Int?) = apply { if (value != null) { tag(field, 0); varint(value.toLong()) } }

    fun bool(field: Int, value: Boolean) = apply { if (value) { tag(field, 0); varint(1) } }

    private fun bytes(field: Int, payload: ByteArray) = apply {
        tag(field, 2)
        varint(payload.size.toLong())
        out.write(payload)
    }

    fun string(field: Int, value: String) = apply { if (value.isNotEmpty()) bytes(field, value.toByteArray()) }

    fun message(field: Int, message: ProtoWriter) = bytes(field, message.toByteArray())

    fun packed(field: Int, values: List<Int>) = apply {
        if (values.isNotEmpty()) bytes(field, ProtoWriter().apply { values.forEach { varint(it.toLong()) } }.toByteArray())
    }
}
