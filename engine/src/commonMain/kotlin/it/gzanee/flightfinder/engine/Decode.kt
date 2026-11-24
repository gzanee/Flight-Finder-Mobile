package it.gzanee.flightfinder.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * Google Flights page -> live quotes: port of live_quotes.decode() and of the
 * parts of fli's parse_flight_row (and its pydantic validation) that decide
 * whether a row is accepted. A schema mismatch is an error, never an empty route.
 */

open class LiveUnavailable(message: String) : Exception(message)
class LiveBlocked(message: String) : LiveUnavailable(message)
class LiveTransient(message: String) : LiveUnavailable(message)

private class RowError(message: String) : Exception(message)

private fun JsonElement?.isNull() = this == null || this is JsonNull
private fun JsonElement?.list(): JsonArray? = this as? JsonArray
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonElement?.nonEmptyStr(): String? = str()?.ifEmpty { null }
private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
private fun JsonElement?.isNumber() = this is JsonPrimitive && !isString && this !is JsonNull && booleanOrNull == null

/** Python truthiness of a JSON value. */
private fun JsonElement?.truthy(): Boolean = when (this) {
    null, is JsonNull -> false
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
    is JsonPrimitive -> if (isString) content.isNotEmpty() else booleanOrNull ?: (doubleOrNull != 0.0)
}

private fun safeGet(seq: JsonElement?, i: Int): JsonElement? = (seq as? JsonArray)?.let { if (i in it.indices) it[i] else null }

/** Python indexing: negative indexes count from the end, out of range raises. */
private fun at(seq: JsonElement?, i: Int): JsonElement {
    val list = seq as? JsonArray ?: throw RowError("not a list")
    val index = if (i < 0) list.size + i else i
    if (index !in list.indices) throw RowError("index")
    return list[index]
}

/** fli's Airline enum member name: digits-first codes are prefixed with "_". */
private fun airlineName(code: JsonElement?): String? {
    val c = code.str() ?: return null
    if (c.isEmpty() || c == "multi" || c !in Airports.airlineCodes) return null
    return if (c[0].isDigit()) "_$c" else c
}

private fun readVarint(data: List<Int>, offset: Int): Pair<Long, Int> {
    var result = 0L
    var shift = 0
    var pos = offset
    while (true) {
        if (pos >= data.size) throw IllegalArgumentException("Truncated varint")
        val byte = data[pos++]
        result += (byte and 0x7F).toLong() shl shift
        if (byte and 0x80 == 0) return result to pos
        shift += 7
        if (shift > 63) throw IllegalArgumentException("Varint too long")
    }
}

private fun readDelimited(data: List<Int>, offset: Int): Pair<List<Int>, Int> {
    val (length, start) = readVarint(data, offset)
    val end = start + length
    if (end > data.size) throw IllegalArgumentException("Length-delimited field exceeds payload size")
    return data.subList(start, end.toInt()) to end.toInt()
}

private fun skipField(data: List<Int>, offset: Int, wire: Int): Int = when (wire) {
    0 -> readVarint(data, offset).second
    1, 5 -> (offset + if (wire == 1) 8 else 4).also { if (it > data.size) throw IllegalArgumentException("Fixed field exceeds payload size") }
    2 -> readDelimited(data, offset).second
    else -> throw IllegalArgumentException("Unsupported wire type: $wire")
}

/** fli.core.extract_currency_from_price_token. */
fun currencyFromToken(token: JsonElement?): String? {
    val t = token.str() ?: return null
    if (t.isEmpty()) return null
    return try {
        val data = base64Decode(t)
        var offset = 0
        while (offset < data.size) {
            val (tag, next) = readVarint(data, offset)
            offset = next
            val field = (tag / 8).toInt()
            val wire = (tag and 7).toInt()
            if (field == 3 && wire == 2) {
                val (nested, after) = readDelimited(data, offset)
                offset = after
                var pos = 0
                while (pos < nested.size) {
                    val (ntag, npos) = readVarint(nested, pos)
                    pos = npos
                    if ((ntag / 8).toInt() == 3 && (ntag and 7).toInt() == 2) {
                        val bytes = readDelimited(nested, pos).first
                        return ByteArray(bytes.size) { bytes[it].toByte() }.decodeToString(throwOnInvalidSequence = true).uppercase()
                    }
                    pos = skipField(nested, pos, (ntag and 7).toInt())
                }
                continue
            }
            offset = skipField(data, offset, wire)
        }
        null
    } catch (e: Exception) {
        null
    }
}

private class Leg(
    val airline: String, val flightNumber: String, val from: String, val to: String,
    val departure: String, val arrival: String, val duration: Int,
)

/** Python datetime(*date, *time) from Google's [y,m,d] + [h,m] arrays. */
private fun naiveDatetime(dateArr: JsonElement?, timeArr: JsonElement?): String {
    val d = dateArr.list() ?: throw RowError("date not a list")
    val t = timeArr.list() ?: throw RowError("time not a list")
    if (d.none { !it.isNull() } || t.none { !it.isNull() }) throw RowError("empty date")
    val parts = (d + t).map { if (it.truthy()) it else JsonPrimitive(0) }
    if (parts.size < 3 || parts.size > 8) throw RowError("bad datetime")
    val ints = parts.map { p ->
        if (p !is JsonPrimitive || p.isString) throw RowError("not an int")
        when (p.booleanOrNull) {
            true -> 1
            false -> 0
            null -> p.content.toIntOrNull() ?: throw RowError("not an int")
        }
    }
    val (y, mo, day) = ints
    val h = ints.getOrElse(3) { 0 }
    val mi = ints.getOrElse(4) { 0 }
    val s = ints.getOrElse(5) { 0 }
    if (y !in 1..9999 || mo !in 1..12 || h !in 0..23 || mi !in 0..59 || s !in 0..59) throw RowError("datetime out of range")
    val epoch = Calendar.epochDay(y, mo, day)
    if (day < 1 || Calendar.formatDay(epoch) != "${Calendar.pad(y, 4)}-${Calendar.pad(mo)}-${Calendar.pad(day)}") throw RowError("bad day")
    return "${Calendar.pad(y, 4)}-${Calendar.pad(mo)}-${Calendar.pad(day)}T${Calendar.pad(h)}:${Calendar.pad(mi)}:${Calendar.pad(s)}"
}

/** PositiveInt in pydantic's lax mode: ints, integral floats, digit strings. */
private fun positiveInt(v: JsonElement?): Int {
    val p = v as? JsonPrimitive ?: throw RowError("not a number")
    val n: Long? = when {
        p is JsonNull -> null
        p.isString -> p.content.trim().takeIf { Regex("""[+-]?\d+""").matches(it) }?.toLongOrNull()
        p.booleanOrNull != null -> if (p.booleanOrNull == true) 1 else 0
        else -> p.content.toDoubleOrNull()?.takeIf { it == kotlin.math.floor(it) && it.isFinite() }?.toLong()
    }
    if (n == null || n <= 0) throw RowError("not a positive int")
    return n.toInt()
}

private fun parseLeg(fl: JsonElement): Leg {
    val info = at(fl, 22).let { if (it.truthy()) it else JsonArray(emptyList()) }
    val airline = airlineName(safeGet(info, 0)) ?: throw RowError("unknown airline")
    val flightNumber = safeGet(info, 1).nonEmptyStr() ?: ""
    val from = at(fl, 3).str()?.takeIf { Airports.knownToDecoder(it) } ?: throw RowError("unknown airport")
    val to = at(fl, 6).str()?.takeIf { Airports.knownToDecoder(it) } ?: throw RowError("unknown airport")
    return Leg(
        airline, flightNumber, from, to,
        naiveDatetime(at(fl, 20), at(fl, 8)), naiveDatetime(at(fl, 21), at(fl, 10)), positiveInt(at(fl, 11)),
    )
}

private class FlightRow(val price: Double?, val currency: String?, val duration: Int, val legs: List<Leg>, val selfTransfer: Boolean?, val token: String?)

private fun parseFlightRow(row: JsonElement): FlightRow {
    val detail = at(row, 0)
    val block = safeGet(row, 1).list() ?: throw RowError("price block missing")
    val head = at(block, 0).list() ?: throw RowError("price head is not a list")
    var price: Double? = null
    if (head.isNotEmpty()) {
        val raw = head.last()
        if (!raw.isNumber()) throw RowError("price field is not numeric")
        price = (raw as JsonPrimitive).content.toDouble()
        if (price < 0) throw RowError("negative price")
    }
    val currency = if (block.size > 1) currencyFromToken(block[1]) else null
    val rawLegs = at(detail, 2).let { if (it.truthy()) it else JsonArray(emptyList()) }
    val legs = rawLegs.list()?.map(::parseLeg) ?: throw RowError("legs not a list")
    return FlightRow(price, currency, positiveInt(at(detail, 9)), legs, safeGet(detail, 12).bool(), safeGet(row, 8).nonEmptyStr())
}

private val SCRIPT = Regex("""<script\b([^>]*)>([\s\S]*?)</script\s*>""", RegexOption.IGNORE_CASE)
private val CLASS = Regex("""\bclass\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

private fun shoppingScript(html: String): String? {
    for (m in SCRIPT.findAll(html)) {
        val c = CLASS.find(m.groupValues[1])?.groupValues
        val classes = (c?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: "").split(Regex("""\s+"""))
        if ("ds:1" in classes) return m.groupValues[2]
    }
    return null
}

private val IATA = Regex("[A-Z0-9]{3}")

typealias ExpectedRoute = Pair<List<String>, List<String>>?

/** Null result groups count as "no flights" only inside the observed envelope. */
private fun isEmptyShoppingResponse(payload: JsonElement, expected: ExpectedRoute): Boolean = try {
    val header = at(payload, 0)
    val locations = at(payload, 1)
    val filters = at(payload, 7)
    val context = at(payload, 12)
    val first = at(locations, 0).list()
    if (first == null || first.size != 2) {
        false
    } else {
        val departure = first[0].list()
        val arrival = first[1].list()
        if (departure == null || arrival == null) {
            false
        } else {
            val origins = departure.map { at(at(it, 0), 0) }
            val dests = arrival.map { at(at(it, 0), 0) }
            val codes = (origins + dests)
            locations.list()!!.size == 1 && origins.isNotEmpty() && dests.isNotEmpty() &&
                codes.all { it.str()?.let(IATA::matches) == true } &&
                (expected == null || (origins.map { it.str() }.toSet() == expected.first.toSet() && dests.map { it.str() }.toSet() == expected.second.toSet())) &&
                header is JsonArray && at(header, 2).let { it.isNumber() && (it as JsonPrimitive).content == "0" } &&
                at(header, 1).let { it is JsonArray && it.isNotEmpty() } &&
                filters is JsonArray && filters.isNotEmpty() &&
                context is JsonArray && context.size == 2 && context[0].nonEmptyStr() != null &&
                context[1].let { it.isNumber() && (it as JsonPrimitive).content == "0" } &&
                at(payload, 23).let { it.isNumber() && (it as JsonPrimitive).content == "1" }
        }
    }
} catch (e: Exception) {
    false
}

private val PAGE_JSON = Json { isLenient = false }

/** Quotes on the page and the number of rows that could not be trusted. */
fun decode(html: String, r: FlexibleRequest, url: String, expected: ExpectedRoute = null): Pair<List<Quote>, Int> {
    val script = shoppingScript(html) ?: throw LiveUnavailable("Pagina Google senza dati voli: consenso, blocco o formato cambiato.")
    if ("travel.frontend.flights.ErrorResponse" in script) {
        throw LiveTransient("Google ha restituito un errore temporaneo anche dopo vari tentativi. Puoi premere Riprendi.")
    }
    val payload: JsonElement
    val rows = ArrayList<JsonElement>()
    try {
        val after = script.indexOf("data:")
        if (after < 0) throw IllegalArgumentException("no data")
        val rest = script.substring(after + 5)
        val cut = rest.lastIndexOf(',')
        payload = PAGE_JSON.parseToJsonElement(if (cut < 0) rest else rest.substring(0, cut))
        var found = false
        for (slot in listOf(2, 3)) {
            val block = at(payload, slot)
            if (block.isNull()) continue
            if (block is JsonArray && block.isNotEmpty() && (block[0].isNull() || block[0] is JsonArray)) {
                found = true
                (block[0] as? JsonArray)?.let(rows::addAll)
            } else {
                throw IllegalArgumentException("Malformed shopping group")
            }
        }
        if (!found && !isEmptyShoppingResponse(payload, expected)) throw IllegalArgumentException("No shopping rows")
    } catch (e: LiveUnavailable) {
        throw e
    } catch (e: Exception) {
        throw LiveUnavailable("Risposta Google non riconosciuta: non equivale a zero voli.")
    }
    // IATA codes can be reassigned: use the names of this live response.
    val names: Map<String, JsonElement> = try {
        val list = at(at(at(payload, 7), 1), 1).list() ?: throw RowError("names")
        list.associate { item -> keyOf(at(item, 0)) to at(item, 1) }
    } catch (e: Exception) {
        emptyMap()
    }
    val quotes = ArrayList<Quote>()
    val seen = HashSet<String>()
    var rejected = 0
    val checked = Calendar.utcNow()
    for (raw in rows) {
        try {
            val row = normalizeTimes(raw)
            val result = parseFlightRow(row)
            val price = result.price
            if (price == null || price <= 0 || result.currency != r.currency) throw RowError("Missing live price or currency")
            if (result.token == null || result.legs.isEmpty()) throw RowError("Missing shopping evidence")
            val rawNames = at(at(raw, 0), 1)
            val single = if (rawNames is JsonArray && rawNames.size == 1) rawNames[0] else null
            val segments = result.legs.map { leg ->
                if (leg.flightNumber.isEmpty()) throw RowError("Missing flight identity")
                val zoneFrom = Airports.zone(leg.from) ?: throw RowError("zone")
                val zoneTo = Airports.zone(leg.to) ?: throw RowError("zone")
                val named = names[leg.airline]?.takeIf { it.truthy() } ?: single?.takeIf { it.truthy() }
                Segment(
                    fromCode = leg.from,
                    toCode = leg.to,
                    departure = Calendar.localInstantIn(leg.departure, zoneFrom),
                    arrival = Calendar.localInstantIn(leg.arrival, zoneTo),
                    airlineCode = leg.airline,
                    airlineName = named?.let { (it as? JsonPrimitive)?.content } ?: leg.airline,
                    flightNumber = leg.flightNumber,
                    durationMin = leg.duration,
                )
            }
            val quote = Quote(
                segments = segments, price = price, currency = result.currency, durationMin = result.duration,
                selfTransfer = result.selfTransfer, checkedAt = checked, baggage = r.baggage,
                travelers = r.travelers, seat = r.seat, bookingUrl = url,
            )
            if (seen.add(fingerprint(quote))) quotes.add(quote)
        } catch (e: Exception) {
            rejected += 1
        }
    }
    return quotes to rejected
}

private fun keyOf(e: JsonElement): String = (e as? JsonPrimitive)?.content ?: e.toString()

/** Google omits zero minute components: [h] or [null, m] become [h, m] before decoding. */
private fun normalizeTimes(raw: JsonElement): JsonElement {
    val row = raw.list() ?: throw RowError("row")
    val detail = at(row, 0).list() ?: throw RowError("detail")
    val segs = at(detail, 2).list() ?: throw RowError("segments")
    val fixed = segs.map { seg ->
        val s = seg.list() ?: throw RowError("segment")
        val copy = s.toMutableList()
        for (slot in listOf(8, 10)) {
            val t = at(s, slot).list() ?: throw RowError("Missing local time")
            copy[slot] = JsonArray((t + JsonPrimitive(0) + JsonPrimitive(0)).take(2).map { if (it.truthy()) it else JsonPrimitive(0) })
        }
        JsonArray(copy)
    }
    val newDetail = detail.toMutableList().also { it[2] = JsonArray(fixed) }
    return JsonArray(row.toMutableList().also { it[0] = JsonArray(newDetail) })
}
