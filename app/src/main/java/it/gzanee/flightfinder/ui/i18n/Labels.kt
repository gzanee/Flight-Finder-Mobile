package it.gzanee.flightfinder.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.domain.model.Seat
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.search.AirportCatalog
import it.gzanee.flightfinder.ui.format.appLocale
import it.gzanee.flightfinder.ui.format.flagOf
import it.gzanee.flightfinder.ui.format.isItalian
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import java.util.Locale

// Le etichette dei tipi del motore restano in italiano nel motore (come nel portale):
// la schermata usa queste, nella lingua dell'app.

@Composable
fun Seat.text(): String = stringResource(
    when (this) {
        Seat.ECONOMY -> R.string.seat_economy
        Seat.PREMIUM_ECONOMY -> R.string.seat_premium
        Seat.BUSINESS -> R.string.seat_business
        Seat.FIRST -> R.string.seat_first
    },
)

@Composable
fun Baggage.text(): String = stringResource(
    when (this) {
        Baggage.BASE -> R.string.baggage_base
        Baggage.CABIN -> R.string.baggage_cabin
        Baggage.CHECKED -> R.string.baggage_checked
    },
)

@Composable
fun JobStatus.text(): String = stringResource(
    when (this) {
        JobStatus.RUNNING -> R.string.status_running
        JobStatus.CANCELLING -> R.string.status_cancelling
        JobStatus.CANCELLED -> R.string.status_cancelled
        JobStatus.BLOCKED -> R.string.status_blocked
        JobStatus.INTERRUPTED -> R.string.status_interrupted
        JobStatus.COMPLETE -> R.string.status_complete
    },
)

@Composable
fun SortOrder.text(): String = stringResource(
    when (this) {
        SortOrder.PRICE -> R.string.sort_price
        SortOrder.USEFUL -> R.string.sort_useful
        SortOrder.DURATION -> R.string.sort_duration
        SortOrder.DEPARTURE -> R.string.sort_departure
    },
)

/** I gruppi di destinazioni del catalogo ("Mare e sole"), per chiave. */
@Composable
fun destinationSetLabel(key: String, fallback: String): String = when (key) {
    "europe-short" -> stringResource(R.string.set_europe_short)
    "europe-wide" -> stringResource(R.string.set_europe_wide)
    "sun" -> stringResource(R.string.set_sun)
    "city-break" -> stringResource(R.string.set_city_break)
    "long-haul" -> stringResource(R.string.set_long_haul)
    else -> fallback
}

/** Nome del paese nella lingua dell'app, dal codice ISO ("pl" → "Polonia" / "Poland"). */
fun countryName(iso: String): String =
    if (iso.length == 2) Locale("", iso.uppercase()).getDisplayCountry(appLocale()).ifEmpty { iso.uppercase() } else iso

/** Nomi di città, bandiere, paesi e aeroporti dal catalogo, nella lingua dell'app. */
fun AirportCatalog?.names(): PlaceNames = object : PlaceNames {
    private val english = !isItalian()
    override fun city(code: String): String {
        val catalog = this@names ?: return code
        return (if (english) catalog.englishCities[code.uppercase()] else null) ?: catalog.city(code)
    }
    override fun flag(code: String) = this@names?.details(code)?.country?.let(::flagOf).orEmpty()
    override fun country(code: String) = this@names?.details(code)?.country?.takeIf { it.isNotEmpty() }?.let(::countryName).orEmpty()
    override fun airport(code: String): String {
        val catalog = this@names ?: return code
        // In inglese il nome del catalogo ("Lisbon Portela Airport"), in italiano quello da Wikidata.
        return (if (english) catalog.name(code) else null) ?: catalog.details(code).name
    }
}
