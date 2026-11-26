package it.gzanee.flightfinder.ui.format

import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** La lingua dell'app (quella del telefono): date e prezzi la seguono. */
fun appLocale(): Locale = Locale.getDefault()

/** Vero se l'app è in italiano; altrimenti è in inglese. */
fun isItalian(): Boolean = appLocale().language == "it"

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** "63 €", "54,99 €" in italiano; "€63", "€54.99" in inglese. */
fun Double.asEuro(): String {
    val format = NumberFormat.getCurrencyInstance(appLocale()).apply {
        currency = Currency.getInstance("EUR")
        val cents = this@asEuro % 1 != 0.0
        minimumFractionDigits = if (cents) 2 else 0
        maximumFractionDigits = if (cents) 2 else 0
    }
    return format.format(this)
}

/** "ven 12 dic" / "Fri 12 Dec". */
fun LocalDate.short(): String = format(DateTimeFormatter.ofPattern("EEE d MMM", appLocale())).replace(".", "")

fun LocalDate.long(): String = format(DateTimeFormatter.ofPattern("EEE d MMMM yyyy", appLocale())).replace(".", "")

fun OffsetDateTime.shortDay(): String = toLocalDate().short()

fun OffsetDateTime.clock(): String = format(CLOCK)

/** "2h 05m". */
fun Int.asDuration() = "${this / 60}h ${(this % 60).toString().padStart(2, '0')}m"

/** Bandiera emoji da un codice paese ISO ("it" → 🇮🇹). */
fun flagOf(country: String): String {
    if (!country.matches(Regex("[a-zA-Z]{2}"))) return ""
    return country.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** Si aprono solo link a Google Flights. */
fun safeGoogleLink(url: String?): String? = url?.takeIf { it.startsWith("https://www.google.com/") }
