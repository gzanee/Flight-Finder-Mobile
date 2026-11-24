package it.gzanee.flightfinder.data.google

import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.Segment
import it.gzanee.flightfinder.domain.search.AirportCatalog
import it.gzanee.flightfinder.domain.search.QuotesBlockedException
import it.gzanee.flightfinder.domain.search.QuotesUnavailableException
import it.gzanee.flightfinder.domain.search.fingerprint
import it.gzanee.flightfinder.domain.search.localInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

/** Google non ha risposto con dati utilizzabili: non equivale a "nessun volo". */
open class GoogleUnavailableException(message: String) : QuotesUnavailableException(message)

/** Google limita le richieste (403/429, "traffico insolito"): la ricerca si ferma. */
class GoogleBlockedException(message: String) : QuotesBlockedException(message)

/** Errore temporaneo di Google (ErrorResponse): si riprova dopo una pausa. */
class GoogleTransientException(message: String) : GoogleUnavailableException(message)

/** Le offerte lette da una pagina e le righe scartate perché non verificabili. */
data class GooglePage(val quotes: List<Quote>, val rejected: Int)

/**
 * Legge la pagina di Google Flights: port di live_quotes.decode() del portale e delle
 * parti di fli.parse_flight_row (con la sua validazione pydantic) che decidono se una
 * riga è accettata. Un formato inatteso è un errore, mai una rotta senza voli.
 */
class GooglePageDecoder(private val catalog: AirportCatalog) {

    private class RowError(message: String) : Exception(message)

    fun decode(html: String, request: SearchRequest, url: String, expectedRoute: Pair<List<String>, List<String>>? = null): GooglePage {
        val script = shoppingScript(html)
            ?: throw GoogleUnavailableException("Pagina Google senza dati voli: consenso, blocco o formato cambiato.")
        if ("travel.frontend.flights.ErrorResponse" in script) {
            throw GoogleTransientException("Google ha restituito un errore temporaneo anche dopo vari tentativi. Puoi premere Riprendi.")
        }
        val (payload, rows) = try {
            val data = script.substringAfter("data:", missingDelimiterValue = "").ifEmpty { throw IllegalArgumentException("no data") }
            val payload = Json.parseToJsonElement(if (',' in data) data.substringBeforeLast(',') else data)
            val rows = ArrayList<JsonElement>()
            var found = false
            for (slot in listOf(2, 3)) {
                val block = payload.at(slot)
                if (block.isNull()) continue
                if (block is JsonArray && block.isNotEmpty() && (block[0].isNull() || block[0] is JsonArray)) {
                    found = true
                    (block[0] as? JsonArray)?.let(rows::addAll)
                } else {
                    throw IllegalArgumentException("Malformed shopping group")
                }
            }
            if (!found && !isEmptyShoppingResponse(payload, expectedRoute)) throw IllegalArgumentException("No shopping rows")
            payload to rows
        } catch (e: Exception) {
            throw GoogleUnavailableException("Risposta Google non riconosciuta: non equivale a zero voli.")
        }
        // I codici IATA vengono riassegnati: valgono i nomi di questa risposta.
        val names: Map<String, JsonElement> = runCatching {
            (payload.at(7).at(1).at(1) as? JsonArray ?: throw RowError("names")).associate { keyOf(it.at(0)) to it.at(1) }
        }.getOrDefault(emptyMap())
        val checkedAt = Instant.now()
        val quotes = LinkedHashMap<String, Quote>()
        var rejected = 0
        for (raw in rows) {
            try {
                val quote = toQuote(raw, names, request, url, checkedAt)
                quotes.putIfAbsent(quote.fingerprint(), quote)
            } catch (e: Exception) {
                rejected += 1
            }
        }
        return GooglePage(quotes.values.toList(), rejected)
    }

    private fun toQuote(raw: JsonElement, names: Map<String, JsonElement>, request: SearchRequest, url: String, checkedAt: Instant): Quote {
        val row = normalizeTimes(raw)
        val detail = row.at(0)
        val priceBlock = row.safeGet(1) as? JsonArray ?: throw RowError("price block missing")
        val head = priceBlock.at(0) as? JsonArray ?: throw RowError("price head is not a list")
        val price = if (head.isEmpty()) null else head.last().let {
            if (!it.isNumber()) throw RowError("price field is not numeric")
            (it as JsonPrimitive).content.toDouble().also { p -> if (p < 0) throw RowError("negative price") }
        }
        val currency = if (priceBlock.size > 1) currencyFromToken(priceBlock[1]) else null
        val rawLegs = detail.at(2).let { if (it.truthy()) it else JsonArray(emptyList()) } as? JsonArray ?: throw RowError("legs")
        val legs = rawLegs.map(::parseLeg)
        val duration = positiveInt(detail.at(9))
        val selfTransfer = (detail.safeGet(12) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        val token = row.safeGet(8).nonEmptyString()
        if (price == null || price <= 0 || currency != request.currency) throw RowError("Missing live price or currency")
        if (token == null || legs.isEmpty()) throw RowError("Missing shopping evidence")
        val rawNames = raw.at(0).at(1)
        val singleName = if (rawNames is JsonArray && rawNames.size == 1) rawNames[0] else null
        val segments = legs.map { leg ->
            if (leg.flightNumber.isEmpty()) throw RowError("Missing flight identity")
            Segment(
                fromCode = leg.from,
                toCode = leg.to,
                departure = localInstant(leg.departure, zoneOf(leg.from)),
                arrival = localInstant(leg.arrival, zoneOf(leg.to)),
                airlineCode = leg.airline,
                airlineName = ((names[leg.airline]?.takeIf { it.truthy() } ?: singleName?.takeIf { it.truthy() }) as? JsonPrimitive)?.content ?: leg.airline,
                flightNumber = leg.flightNumber,
                durationMin = leg.duration,
            )
        }
        return Quote(
            segments = segments, price = price, currency = currency, durationMin = duration, selfTransfer = selfTransfer,
            checkedAt = checkedAt, baggage = request.baggage, travelers = request.travelers, seat = request.seat, bookingUrl = url,
        )
    }

    private fun zoneOf(code: String): ZoneId = ZoneId.of(catalog.zone(code) ?: throw RowError("zone"))

    private class Leg(
        val airline: String, val flightNumber: String, val from: String, val to: String,
        val departure: LocalDateTime, val arrival: LocalDateTime, val duration: Int,
    )

    private fun parseLeg(fl: JsonElement): Leg {
        val info = fl.at(22).let { if (it.truthy()) it else JsonArray(emptyList()) }
        val airline = airlineName(info.safeGet(0)) ?: throw RowError("unknown airline") // FlightLeg.airline è obbligatoria
        val from = fl.at(3).string()?.takeIf(catalog::knownToDecoder) ?: throw RowError("unknown airport")
        val to = fl.at(6).string()?.takeIf(catalog::knownToDecoder) ?: throw RowError("unknown airport")
        return Leg(
            airline = airline,
            flightNumber = info.safeGet(1).nonEmptyString() ?: "",
            from = from,
            to = to,
            departure = naiveDateTime(fl.at(20), fl.at(8)),
            arrival = naiveDateTime(fl.at(21), fl.at(10)),
            duration = positiveInt(fl.at(11)),
        )
    }

    /** Nome del membro dell'enum Airline di fli: i codici che iniziano con una cifra hanno "_" davanti. */
    private fun airlineName(code: JsonElement?): String? {
        val c = code.string()?.takeIf { it.isNotEmpty() && it != "multi" && it in catalog.airlineCodes } ?: return null
        return if (c[0].isDigit()) "_$c" else c
    }

    /** datetime(*data, *ora) di Python dagli array [a,m,g] e [h,m] di Google. */
    private fun naiveDateTime(date: JsonElement, time: JsonElement): LocalDateTime {
        if (date !is JsonArray || time !is JsonArray) throw RowError("date/time not a list")
        if (date.all { it.isNull() } || time.all { it.isNull() }) throw RowError("empty date")
        val parts = (date + time).map { if (it.truthy()) it else JsonPrimitive(0) }
        if (parts.size !in 3..8) throw RowError("bad datetime")
        val ints = parts.map { p ->
            if (p !is JsonPrimitive || p.isString) throw RowError("not an int")
            p.booleanOrNull?.let { if (it) 1 else 0 } ?: p.content.toIntOrNull() ?: throw RowError("not an int")
        }
        return try {
            LocalDateTime.of(ints[0], ints[1], ints[2], ints.getOrElse(3) { 0 }, ints.getOrElse(4) { 0 }, ints.getOrElse(5) { 0 })
        } catch (e: java.time.DateTimeException) {
            throw RowError("datetime out of range")
        }
    }

    /** PositiveInt di pydantic in modalità lax: interi, float interi, stringhe di cifre. */
    private fun positiveInt(v: JsonElement): Int {
        val p = v as? JsonPrimitive ?: throw RowError("not a number")
        val n: Long? = when {
            p is JsonNull -> null
            p.isString -> p.content.trim().takeIf { Regex("""[+-]?\d+""").matches(it) }?.toLongOrNull()
            p.booleanOrNull != null -> if (p.booleanOrNull == true) 1 else 0
            else -> p.doubleOrNull?.takeIf { it.isFinite() && it == Math.floor(it) }?.toLong()
        }
        if (n == null || n <= 0) throw RowError("not a positive int")
        return n.toInt()
    }

    /** Google omette i minuti a zero: [h] o [null, m] diventano [h, m]. */
    private fun normalizeTimes(raw: JsonElement): JsonArray {
        val row = raw as? JsonArray ?: throw RowError("row")
        val detail = row.at(0) as? JsonArray ?: throw RowError("detail")
        val segments = detail.at(2) as? JsonArray ?: throw RowError("segments")
        val fixed = segments.map { seg ->
            val s = (seg as? JsonArray ?: throw RowError("segment")).toMutableList()
            for (slot in listOf(8, 10)) {
                val t = s.getOrNull(slot) as? JsonArray ?: throw RowError("Missing local time")
                s[slot] = JsonArray((t + JsonPrimitive(0) + JsonPrimitive(0)).take(2).map { if (it.truthy()) it else JsonPrimitive(0) })
            }
            JsonArray(s)
        }
        return JsonArray(row.toMutableList().also { it[0] = JsonArray(detail.toMutableList().also { d -> d[2] = JsonArray(fixed) }) })
    }

    /** I gruppi vuoti valgono "nessun volo" solo dentro l'involucro osservato di una risposta normale. */
    private fun isEmptyShoppingResponse(payload: JsonElement, expected: Pair<List<String>, List<String>>?): Boolean = try {
        val header = payload.at(0)
        val locations = payload.at(1) as JsonArray
        val filters = payload.at(7)
        val context = payload.at(12)
        val ends = locations.at(0) as JsonArray
        val origins = (ends.at(0) as JsonArray).map { it.at(0).at(0) }
        val destinations = (ends.at(1) as JsonArray).map { it.at(0).at(0) }
        fun JsonElement.isZero() = isNumber() && (this as JsonPrimitive).content == "0"
        ends.size == 2 && locations.size == 1 && origins.isNotEmpty() && destinations.isNotEmpty() &&
            (origins + destinations).all { it.string()?.matches(Regex("[A-Z0-9]{3}")) == true } &&
            (expected == null || (origins.map { it.string() }.toSet() == expected.first.toSet() && destinations.map { it.string() }.toSet() == expected.second.toSet())) &&
            header is JsonArray && header.at(2).isZero() && (header.at(1) as? JsonArray)?.isNotEmpty() == true &&
            filters is JsonArray && filters.isNotEmpty() &&
            context is JsonArray && context.size == 2 && context[0].nonEmptyString() != null && context[1].isZero() &&
            payload.at(23).let { it.isNumber() && (it as JsonPrimitive).content == "1" }
    } catch (e: Exception) {
        false
    }

    companion object {
        private val SCRIPT = Regex("""<script\b([^>]*)>([\s\S]*?)</script\s*>""", RegexOption.IGNORE_CASE)
        private val CLASS = Regex("""\bclass\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

        private fun shoppingScript(html: String): String? = SCRIPT.findAll(html).firstOrNull { m ->
            val classes = CLASS.find(m.groupValues[1])?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }.orEmpty()
            "ds:1" in classes.split(Regex("""\s+"""))
        }?.groupValues?.get(2)

        /** fli.core.extract_currency_from_price_token: la valuta dentro il token protobuf del prezzo. */
        fun currencyFromToken(token: JsonElement?): String? {
            val text = token.string()?.takeIf { it.isNotEmpty() } ?: return null
            return try {
                val data = lenientBase64(text)
                ProtoReader(data).fields().firstNotNullOfOrNull { (field, value) ->
                    if (field != 3 || value !is ByteArray) return@firstNotNullOfOrNull null
                    ProtoReader(value).fields().firstOrNull { it.first == 3 && it.second is ByteArray }?.let { (_, v) ->
                        Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(v as ByteArray)).toString().uppercase()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        /** base64.urlsafe_b64decode di Python: accetta anche + e /, ignora i caratteri estranei. */
        private fun lenientBase64(text: String): ByteArray {
            val cleaned = text.replace('-', '+').replace('_', '/').takeWhile { it != '=' }.filter { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '/' }
            if (cleaned.length % 4 == 1) throw IllegalArgumentException("Incorrect padding")
            return Base64.getDecoder().decode(cleaned.padEnd((cleaned.length + 3) / 4 * 4, '='))
        }
    }
}

/** Lettura protobuf minima: (campo, valore) dove il valore è Long o ByteArray. */
private class ProtoReader(private val data: ByteArray) {
    private var pos = 0

    private fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= data.size) throw IllegalArgumentException("Truncated varint")
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw IllegalArgumentException("Varint too long")
        }
    }

    private fun skip(n: Int) {
        if (pos + n > data.size) throw IllegalArgumentException("Field exceeds payload size")
        pos += n
    }

    fun fields(): Sequence<Pair<Int, Any>> = sequence {
        while (pos < data.size) {
            val tag = varint()
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> yield(field to varint())
                1 -> skip(8)
                5 -> skip(4)
                2 -> {
                    val length = varint().toInt()
                    if (length < 0 || pos + length > data.size) throw IllegalArgumentException("Length exceeds payload size")
                    yield(field to data.copyOfRange(pos, pos + length))
                    pos += length
                }
                else -> throw IllegalArgumentException("Unsupported wire type")
            }
        }
    }
}

// ---- Accesso agli array JSON con la semantica di Python ----

private fun JsonElement?.isNull() = this == null || this is JsonNull

private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.nonEmptyString(): String? = string()?.ifEmpty { null }

private fun JsonElement?.isNumber() = this is JsonPrimitive && !isString && this !is JsonNull && booleanOrNull == null

/** Verità di un valore come in Python. */
private fun JsonElement?.truthy(): Boolean = when (this) {
    null, is JsonNull -> false
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
    is JsonPrimitive -> if (isString) content.isNotEmpty() else booleanOrNull ?: (doubleOrNull != 0.0)
}

/** Indice come in Python: negativo dalla fine, fuori dai limiti è un errore. */
private fun JsonElement?.at(i: Int): JsonElement {
    val list = this as? JsonArray ?: throw IllegalArgumentException("not a list")
    val index = if (i < 0) list.size + i else i
    if (index !in list.indices) throw IllegalArgumentException("index")
    return list[index]
}

private fun JsonElement?.safeGet(i: Int): JsonElement? = (this as? JsonArray)?.getOrNull(i)

private fun keyOf(e: JsonElement): String = (e as? JsonPrimitive)?.content ?: e.toString()
