package it.gzanee.flightfinder.engine

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Calendar dates ("YYYY-MM-DD") and airport-local timestamps written as Python's
 * datetime.isoformat() does ("2026-10-02T18:00:00+02:00"). Wall-clock parts are
 * read from the string, elapsed times from the instant, as in flightcore.
 */
object Calendar {
    private fun floorDiv(a: Long, b: Long) = a.floorDiv(b)

    /** Days since 1970-01-01 of a proleptic Gregorian date. */
    fun epochDay(y0: Int, m: Int, d: Int): Long {
        val y = (if (m <= 2) y0 - 1 else y0).toLong()
        val era = floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** (year, month, day) of an epoch day. */
    fun civil(day: Long): Triple<Int, Int, Int> {
        val z = day + 719468
        val era = floorDiv(z, 146097)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
        return Triple(y, m, d)
    }

    private val DAY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun parseDay(day: String): Long {
        val m = DAY.matchEntire(day) ?: throw IllegalArgumentException("Data non valida: $day")
        val (y, mo, d) = m.destructured
        val epoch = epochDay(y.toInt(), mo.toInt(), d.toInt())
        if (formatDay(epoch) != day) throw IllegalArgumentException("Data non valida: $day")
        return epoch
    }

    fun isDay(day: String) = runCatching { parseDay(day) }.isSuccess

    fun formatDay(epoch: Long): String {
        val (y, m, d) = civil(epoch)
        return "${pad(y, 4)}-${pad(m)}-${pad(d)}"
    }

    fun addDays(day: String, n: Int) = formatDay(parseDay(day) + n)

    fun daysBetween(from: String, to: String) = (parseDay(to) - parseDay(from)).toInt()

    /** Python weekday(): Monday 0 ... Sunday 6. */
    fun weekday(day: String): Int = (parseDay(day) + 3).mod(7L).toInt()

    fun pad(n: Int, width: Int = 2) = n.toString().padStart(width, '0')

    // ---- Instants ----

    private val ISO = Regex(
        """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?(Z|[+-]\d{2}:\d{2})?$""",
    )

    fun hasOffset(iso: String): Boolean = ISO.matchEntire(iso)?.groupValues?.get(8)?.isNotEmpty() == true

    /** Instant of an ISO timestamp with offset, in milliseconds; null without offset. */
    fun instantMs(iso: String): Long? {
        val g = ISO.matchEntire(iso)?.groupValues ?: return null
        if (g[8].isEmpty()) return null
        val wall = wallMs(g)
        val offset = if (g[8] == "Z") 0 else {
            val sign = if (g[8][0] == '-') -1 else 1
            sign * (g[8].substring(1, 3).toInt() * 60 + g[8].substring(4, 6).toInt())
        }
        return wall - offset * 60_000L
    }

    fun instant(iso: String): Long = instantMs(iso) ?: throw IllegalArgumentException("Orario senza fuso: $iso")

    private fun wallMs(g: List<String>): Long {
        val day = epochDay(g[1].toInt(), g[2].toInt(), g[3].toInt())
        val seconds = g[4].toInt() * 3600 + g[5].toInt() * 60 + (g[6].ifEmpty { "0" }).toInt()
        val millis = if (g[7].isEmpty()) 0 else g[7].padEnd(3, '0').substring(0, 3).toInt()
        return (day * 86400 + seconds) * 1000 + millis
    }

    fun minutesBetween(from: String, to: String): Double = (instant(to) - instant(from)) / 60_000.0

    /** Local calendar date and clock ("HH:MM:SS") of a timestamp, as written. */
    fun localDate(iso: String) = iso.substring(0, 10)

    fun localClock(iso: String) = iso.substring(11, 19)

    fun clockSeconds(hhmm: String) = if (hhmm.length == 5) "$hhmm:00" else hhmm

    // ---- Time zones ----

    private val zones = HashMap<String, TimeZone>()

    private fun zone(id: String): TimeZone = zones.getOrPut(id) { TimeZone.of(id) }

    /** Offset of a zone from UTC at an instant (ms), in minutes. */
    fun zoneOffset(zone: String, utcMs: Long): Int =
        zone(zone).offsetAt(Instant.fromEpochMilliseconds(utcMs.floorDiv(1000L) * 1000)).totalSeconds / 60

    fun formatOffset(minutes: Int): String {
        val sign = if (minutes < 0) "-" else "+"
        val abs = kotlin.math.abs(minutes)
        return "$sign${pad(abs / 60)}:${pad(abs % 60)}"
    }

    private val NAIVE = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?""")

    /**
     * Local wall time in a zone as an ISO string with its offset. Like
     * flexible.local_instant: ambiguous or non-existent times (DST change) are
     * refused, never guessed.
     */
    fun localInstantIn(naive: String, zoneId: String): String {
        val g = NAIVE.find(naive)?.groupValues ?: throw IllegalArgumentException("Orario non valido: $naive")
        val day = epochDay(g[1].toInt(), g[2].toInt(), g[3].toInt())
        val secs = g[4].toInt() * 3600 + g[5].toInt() * 60 + g[6].ifEmpty { "0" }.toInt()
        val wall = (day * 86400 + secs) * 1000
        val candidates = setOf(zoneOffset(zoneId, wall - 86_400_000), zoneOffset(zoneId, wall + 86_400_000))
        val valid = candidates.filter { zoneOffset(zoneId, wall - it * 60_000L) == it }
        if (valid.isEmpty()) throw IllegalArgumentException("Orario locale inesistente.")
        if (valid.size > 1) throw IllegalArgumentException("Orario ambiguo o inesistente al cambio di ora.")
        val (y, m, d) = civil(day)
        return "${pad(y, 4)}-${pad(m)}-${pad(d)}T${pad(secs / 3600)}:${pad(secs % 3600 / 60)}:${pad(secs % 60)}" +
            formatOffset(valid[0])
    }

    // ---- Now ----

    fun today(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** "HH:MM" of a timestamp in the device's time zone (e.g. "Prezzo delle 12:05"). */
    fun deviceClock(iso: String): String {
        val ms = instantMs(iso) ?: return ""
        val local = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${pad(local.hour)}:${pad(local.minute)}"
    }

    fun nowSeconds(): Double = Clock.System.now().toEpochMilliseconds() / 1000.0

    /** UTC now as Python's isoformat writes it (milliseconds). */
    fun utcNow(): String {
        val ms = Clock.System.now().toEpochMilliseconds()
        val day = ms.floorDiv(86_400_000L)
        val rest = ms - day * 86_400_000L
        val (y, m, d) = civil(day)
        val s = (rest / 1000).toInt()
        return "${pad(y, 4)}-${pad(m)}-${pad(d)}T${pad(s / 3600)}:${pad(s % 3600 / 60)}:${pad(s % 60)}.${pad((rest % 1000).toInt(), 3)}+00:00"
    }
}
