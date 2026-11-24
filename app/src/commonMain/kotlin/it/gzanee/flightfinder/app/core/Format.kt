package it.gzanee.flightfinder.app.core

import it.gzanee.flightfinder.engine.Airports
import it.gzanee.flightfinder.engine.Calendar
import kotlin.math.abs
import kotlin.math.roundToLong

/** Italian labels for prices, days and durations, as on the web page. */
object Fmt {
    private val WEEKDAYS = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
    private val MONTHS = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    val MONTHS_LONG = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
    val DAY_LETTERS = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

    private fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString(".").reversed()

    /** "63 €", "54,99 €", "1.234 €". */
    fun money(x: Double): String {
        val cents = (abs(x) * 100).roundToLong()
        val sign = if (x < 0) "-" else ""
        val units = group(cents / 100)
        return if (cents % 100 == 0L) "$sign$units €" else "$sign$units,${(cents % 100).toString().padStart(2, '0')} €"
    }

    /** "ven 12 dic" from the local date of an ISO string. */
    fun shortDay(iso: String): String {
        val d = iso.take(10)
        return "${WEEKDAYS[Calendar.weekday(d)]} ${d.substring(8, 10).toInt()} ${MONTHS[d.substring(5, 7).toInt() - 1]}"
    }

    fun longDay(iso: String): String {
        val d = iso.take(10)
        return "${WEEKDAYS[Calendar.weekday(d)]} ${d.substring(8, 10).toInt()} ${MONTHS_LONG[d.substring(5, 7).toInt() - 1]} ${d.take(4)}"
    }

    fun time(iso: String) = iso.substring(11, 16)

    fun duration(minutes: Int) = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

    fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    fun city(code: String) = Airports.details(code).city

    fun airportName(code: String) = Airports.details(code).name

    /** Flag emoji from an ISO country code. */
    fun countryFlag(country: String): String {
        if (!Regex("[a-zA-Z]{2}").matches(country)) return ""
        return country.uppercase().map { 0x1F1E6 + (it - 'A') }.joinToString("") { codePoint(it) }
    }

    fun flag(code: String) = countryFlag(Airports.details(code).country)

    private fun codePoint(cp: Int): String {
        val high = ((cp - 0x10000) shr 10) + 0xD800
        val low = ((cp - 0x10000) and 0x3FF) + 0xDC00
        return charArrayOf(high.toChar(), low.toChar()).concatToString()
    }

    /** Only links to Google Flights are opened. */
    fun safeLink(url: String?): String? = url?.takeIf { it.startsWith("https://www.google.com/") }

    fun remaining(eta: Int?): String {
        if (eta == null) return "qualche minuto"
        val minutes = (eta + 59) / 60
        return when {
            minutes <= 1 -> "meno di un minuto"
            minutes < 90 -> "circa $minutes minuti"
            else -> "circa ${(minutes / 6.0).roundToLong() / 10.0} ore".replace('.', ',')
        }
    }
}
