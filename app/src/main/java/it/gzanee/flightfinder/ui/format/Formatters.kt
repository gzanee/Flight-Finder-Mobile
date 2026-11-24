package it.gzanee.flightfinder.ui.format

import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val ITALIAN = Locale.ITALIAN
private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM", ITALIAN)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", ITALIAN)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** "63 €", "54,99 €", "1.234 €". */
fun Double.asEuro(): String {
    val format = NumberFormat.getCurrencyInstance(Locale.ITALY).apply {
        val cents = this@asEuro % 1 != 0.0
        minimumFractionDigits = if (cents) 2 else 0
        maximumFractionDigits = if (cents) 2 else 0
    }
    return format.format(this)
}

/** "ven 12 dic". */
fun LocalDate.short(): String = format(SHORT_DAY).replace(".", "")

fun LocalDate.long(): String = format(LONG_DAY).replace(".", "")

fun OffsetDateTime.shortDay(): String = toLocalDate().short()

fun OffsetDateTime.clock(): String = format(CLOCK)

/** "2h 05m". */
fun Int.asDuration() = "${this / 60}h ${(this % 60).toString().padStart(2, '0')}m"

fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** Bandiera emoji da un codice paese ISO ("it" → 🇮🇹). */
fun flagOf(country: String): String {
    if (!country.matches(Regex("[a-zA-Z]{2}"))) return ""
    return country.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** Si aprono solo link a Google Flights. */
fun safeGoogleLink(url: String?): String? = url?.takeIf { it.startsWith("https://www.google.com/") }

fun remaining(etaSeconds: Int?): String {
    if (etaSeconds == null) return "qualche minuto"
    val minutes = ceil(etaSeconds / 60.0).toInt()
    return when {
        minutes <= 1 -> "meno di un minuto"
        minutes < 90 -> "circa $minutes minuti"
        else -> "circa ${"%.1f".format(ITALIAN, minutes / 60.0)} ore"
    }
}

fun estimate(minutes: Int): String = if (minutes >= 90) "${"%.1f".format(ITALIAN, minutes / 60.0)} ore" else "$minutes minuti"

