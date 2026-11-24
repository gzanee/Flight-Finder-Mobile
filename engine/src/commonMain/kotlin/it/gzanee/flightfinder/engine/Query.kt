package it.gzanee.flightfinder.engine

/**
 * Google Flights `tfs` query: the protobuf message fast-flights builds, written
 * by hand. Field numbers and order follow fast_flights/pb/flights.proto and
 * Python protobuf's serializer (ascending field number, packed repeated enums,
 * proto3 `optional` fields written whenever set, plain scalars only when non-zero).
 *
 * Several airports per side repeat the Airport message, as AirportGroupQuery in
 * flightcore/live_quotes.py does: with one airport the bytes are identical.
 */
data class QueryLeg(
    val date: String,
    val from: List<String>,
    val to: List<String>,
    val maxStops: Int? = null,
    val airlines: List<String>? = null,
    val earliestDepartureHour: Int? = null,
    val latestDepartureHour: Int? = null,
)

data class QueryOptions(
    val legs: List<QueryLeg>,
    val trip: String, // "one-way" | "round-trip"
    val seat: String,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val language: String,
    val currency: String,
    val carryOnBags: Int = 0,
    val checkedBags: Int = 0,
    val hideSeparateAndSelfTransfer: Boolean = false,
    val excludeBasicEconomy: Boolean = false,
)

private val SEAT = mapOf("economy" to 1, "premium-economy" to 2, "business" to 3, "first" to 4)
private val TRIP = mapOf("round-trip" to 1, "one-way" to 2)

private class ProtoWriter {
    val bytes = ArrayList<Int>()

    fun varint(value: Long): ProtoWriter {
        require(value >= 0) { "negative varint" }
        var n = value
        do {
            var low = (n and 0x7F).toInt()
            n = n ushr 7
            if (n != 0L) low = low or 0x80
            bytes.add(low)
        } while (n != 0L)
        return this
    }

    fun tag(field: Int, wire: Int) = varint((field * 8 + wire).toLong())

    fun int(field: Int, value: Int?): ProtoWriter = if (value == null) this else tag(field, 0).varint(value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = if (!value) this else tag(field, 0).varint(1)

    fun bytesField(field: Int, payload: List<Int>): ProtoWriter {
        tag(field, 2).varint(payload.size.toLong())
        bytes.addAll(payload)
        return this
    }

    fun string(field: Int, value: String?): ProtoWriter =
        if (value.isNullOrEmpty()) this else bytesField(field, value.encodeToByteArray().map { it.toInt() and 0xFF })

    fun packed(field: Int, values: List<Int>): ProtoWriter {
        if (values.isEmpty()) return this
        val inner = ProtoWriter()
        values.forEach { inner.varint(it.toLong()) }
        return bytesField(field, inner.bytes)
    }
}

private fun encodeLeg(leg: QueryLeg): List<Int> {
    val w = ProtoWriter().string(2, leg.date).int(5, leg.maxStops)
    leg.airlines?.forEach { w.string(6, it) }
    w.int(8, leg.earliestDepartureHour).int(9, leg.latestDepartureHour)
    leg.from.forEach { w.bytesField(13, ProtoWriter().string(2, it).bytes) }
    leg.to.forEach { w.bytesField(14, ProtoWriter().string(2, it).bytes) }
    return w.bytes
}

fun encodeQuery(q: QueryOptions): List<Int> {
    val w = ProtoWriter()
    q.legs.forEach { w.bytesField(3, encodeLeg(it)) }
    w.packed(8, List(q.adults) { 1 } + List(q.children) { 2 } + List(q.infants) { 4 })
    w.int(9, SEAT.getValue(q.seat))
    if (q.carryOnBags != 0 || q.checkedBags != 0) {
        w.bytesField(13, ProtoWriter().int(2, q.carryOnBags).int(3, q.checkedBags).bytes)
    }
    w.bool(17, q.hideSeparateAndSelfTransfer)
    w.int(19, TRIP.getValue(q.trip))
    w.bool(25, q.excludeBasicEconomy)
    return w.bytes
}

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

fun base64(bytes: List<Int>): String {
    val out = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val a = bytes[i]
        val b = bytes.getOrNull(i + 1)
        val c = bytes.getOrNull(i + 2)
        val n = (a shl 16) or ((b ?: 0) shl 8) or (c ?: 0)
        out.append(B64[(n shr 18) and 63]).append(B64[(n shr 12) and 63])
        out.append(if (b == null) '=' else B64[(n shr 6) and 63])
        out.append(if (c == null) '=' else B64[n and 63])
        i += 3
    }
    return out.toString()
}

/** Python's lenient base64.urlsafe_b64decode (also accepts + and /). */
fun base64Decode(token: String): List<Int> {
    val values = ArrayList<Int>()
    for (ch in token.replace('-', '+').replace('_', '/')) {
        if (ch == '=') break
        val v = B64.indexOf(ch)
        if (v >= 0) values.add(v)
    }
    if (values.size % 4 == 1) throw IllegalArgumentException("Incorrect padding")
    val out = ArrayList<Int>()
    var i = 0
    while (i < values.size) {
        val chunk = values.subList(i, minOf(i + 4, values.size))
        var n = 0
        chunk.forEachIndexed { k, v -> n = n or (v shl (18 - 6 * k)) }
        out.add((n shr 16) and 255)
        if (chunk.size > 2) out.add((n shr 8) and 255)
        if (chunk.size > 3) out.add(n and 255)
        i += 4
    }
    return out
}

fun tfs(q: QueryOptions): String = base64(encodeQuery(q))

private fun urlEncode(s: String) = buildString {
    for (b in s.encodeToByteArray()) {
        val c = (b.toInt() and 0xFF)
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch) else append("%" + c.toString(16).uppercase().padStart(2, '0'))
    }
}

/** The page the app downloads. */
fun searchUrl(q: QueryOptions): String =
    "https://www.google.com/travel/flights?tfs=${urlEncode(tfs(q))}&hl=${q.language}&curr=${q.currency}"

/** fast-flights Query.url(): the link shown to the user (tfs not escaped). */
fun bookingUrl(q: QueryOptions): String =
    "https://www.google.com/travel/flights/search?tfs=${tfs(q)}&hl=${q.language}&curr=${q.currency}"

/** LiveQuotes._leg: Google accepts whole hours; the upper bound is widened. */
fun queryLeg(origins: List<String>, destinations: List<String>, day: String, r: FlexibleRequest, inbound: Boolean): QueryLeg {
    val lo = if (inbound) r.returnMin else r.departureMin
    val hi = if (inbound) r.returnMax else r.departureMax
    val upper = hi.substring(0, 2).toInt() + if (hi.substring(3) != "00") 1 else 0
    return QueryLeg(
        date = day,
        from = origins,
        to = destinations,
        maxStops = r.maxStops,
        airlines = r.airlines.ifEmpty { null },
        earliestDepartureHour = lo.substring(0, 2).toInt(),
        latestDepartureHour = if (upper < 24) upper else null,
    )
}

/** LiveQuotes._query. */
fun queryFor(r: FlexibleRequest, legs: List<QueryLeg>, trip: String): QueryOptions {
    val bags = r.adults + r.children
    return QueryOptions(
        legs = legs,
        trip = trip,
        seat = r.seat,
        adults = r.adults,
        children = r.children,
        infants = r.infants,
        language = "it",
        currency = r.currency,
        carryOnBags = if (r.baggage == "cabin") bags else 0,
        checkedBags = if (r.baggage == "checked") bags else 0,
        hideSeparateAndSelfTransfer = !r.allowSelfTransfer,
        excludeBasicEconomy = r.excludeBasicEconomy,
    )
}

fun oneWayQuery(origins: List<String>, destinations: List<String>, day: String, r: FlexibleRequest, inbound: Boolean = false) =
    queryFor(r, listOf(queryLeg(origins, destinations, day, r, inbound)), "one-way")

fun roundTripQuery(origin: String, destination: String, out: String, back: String, r: FlexibleRequest) = queryFor(
    r,
    listOf(queryLeg(listOf(origin), listOf(destination), out, r, false), queryLeg(listOf(destination), listOf(origin), back, r, true)),
    "round-trip",
)
