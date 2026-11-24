package it.gzanee.flightfinder.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * JSON delle ricerche salvate. Nomi in snake_case come il portale Python, così i
 * casi di riferimento generati dal portale si leggono e si confrontano direttamente.
 */
@OptIn(ExperimentalSerializationApi::class)
val FlightFinderJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private inline fun <T> parsing(text: String, parse: (String) -> T): T = try {
    parse(text)
} catch (e: DateTimeParseException) {
    throw SerializationException("Valore non valido: $text")
}

/** "2026-12-24". */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = decoder.decodeString().let { parsing(it, LocalDate::parse) }
}

/** "18:00", sempre con due cifre come nel portale. */
object LocalTimeSerializer : KSerializer<LocalTime> {
    private val pattern = Regex("""\d{2}:\d{2}""")
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.format(HH_MM))
    override fun deserialize(decoder: Decoder): LocalTime {
        val text = decoder.decodeString()
        if (!pattern.matches(text)) throw SerializationException("Orario non valido: $text")
        return parsing(text, LocalTime::parse)
    }
}

val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Orario locale di un aeroporto con il suo fuso, scritto come isoformat() di Python:
 * "2026-10-02T18:00:00+02:00". Il testo entra nell'identificativo dei voli.
 */
val ISO_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.format(ISO_SECONDS))
    override fun deserialize(decoder: Decoder): OffsetDateTime = decoder.decodeString().let { parsing(it, OffsetDateTime::parse) }
}

/** Istante UTC con i millisecondi, es. quando Google ha mostrato un prezzo. */
object InstantSerializer : KSerializer<Instant> {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx").withZone(ZoneOffset.UTC)
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(format.format(value))
    override fun deserialize(decoder: Decoder): Instant = decoder.decodeString().let { parsing(it) { s -> OffsetDateTime.parse(s).toInstant() } }
}

/** Giorno della settimana come weekday() di Python: lunedì 0, domenica 6. */
object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor = PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeInt(value.value - 1)
    override fun deserialize(decoder: Decoder): DayOfWeek {
        val n = decoder.decodeInt()
        if (n !in 0..6) throw SerializationException("Giorno non valido: $n")
        return DayOfWeek.of(n + 1)
    }
}

/** Coppia di date (andata, ritorno) scritta come [andata, ritorno] dal portale. */
object DatePairSerializer : KSerializer<DatePair> {
    private val list = ListSerializer(LocalDateSerializer.nullable)
    override val descriptor: SerialDescriptor = list.descriptor
    override fun serialize(encoder: Encoder, value: DatePair) = list.serialize(encoder, listOf(value.departure, value.back))
    override fun deserialize(decoder: Decoder): DatePair {
        val (out, back) = list.deserialize(decoder)
        return DatePair(out ?: throw SerializationException("Data di andata mancante"), back)
    }
}
