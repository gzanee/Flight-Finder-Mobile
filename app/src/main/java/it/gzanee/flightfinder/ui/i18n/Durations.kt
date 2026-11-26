package it.gzanee.flightfinder.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import it.gzanee.flightfinder.ui.format.appLocale
import kotlin.math.ceil

/** Quanto può durare una ricerca: "25 minuti", "1,5 ore". */
@Composable
fun estimate(minutes: Int): String =
    if (minutes >= 90) stringResource(R.string.hours_approx, "%.1f".format(appLocale(), minutes / 60.0)) else plural(R.plurals.minutes, minutes)

/** Il tempo che manca alla fine della ricerca. */
@Composable
fun remaining(etaSeconds: Int?): String {
    if (etaSeconds == null) return stringResource(R.string.eta_unknown)
    val minutes = ceil(etaSeconds / 60.0).toInt()
    return when {
        minutes <= 1 -> stringResource(R.string.eta_under_minute)
        minutes < 90 -> stringResource(R.string.eta_about, plural(R.plurals.minutes, minutes))
        else -> stringResource(R.string.eta_about, stringResource(R.string.hours_approx, "%.1f".format(appLocale(), minutes / 60.0)))
    }
}
