package it.gzanee.flightfinder.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class FlightFinderColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val brand: Color,
    val brandSoft: Color,
    val good: Color,
    val goodSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    val isDark: Boolean,
)

val DarkFlightFinderColors = FlightFinderColors(
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    border = BorderDark,
    brand = AccentBlue,
    brandSoft = AccentSoftDark,
    good = AccentGreenDark,
    goodSoft = GoodSoftDark,
    warn = AccentOrangeDark,
    warnSoft = WarnSoftDark,
    bad = AccentRedDark,
    badSoft = BadSoftDark,
    isDark = true,
)

val LightFlightFinderColors = FlightFinderColors(
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    border = BorderLight,
    brand = AccentBlueLight,
    brandSoft = AccentSoftLight,
    good = AccentGreen,
    goodSoft = GoodSoftLight,
    warn = AccentOrange,
    warnSoft = WarnSoftLight,
    bad = AccentRed,
    badSoft = BadSoftLight,
    isDark = false,
)

val LocalFlightFinderColors = staticCompositionLocalOf { LightFlightFinderColors }

object FlightFinderColorScheme {
    val colors: FlightFinderColors
        @Composable
        get() = LocalFlightFinderColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = AccentRedDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlueLight,
    onPrimary = Color.White,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    error = AccentRed,
)

private const val THEME_ANIM_DURATION = 400

@Composable
private fun animateColor(target: Color) =
    animateColorAsState(target, tween(THEME_ANIM_DURATION), label = "").value

@Composable
private fun animateFlightFinderColors(target: FlightFinderColors) = target.copy(
    background = animateColor(target.background),
    surface = animateColor(target.surface),
    surfaceVariant = animateColor(target.surfaceVariant),
    textPrimary = animateColor(target.textPrimary),
    textSecondary = animateColor(target.textSecondary),
    textTertiary = animateColor(target.textTertiary),
    border = animateColor(target.border),
    brand = animateColor(target.brand),
    brandSoft = animateColor(target.brandSoft),
)

@Composable
fun FlightFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val colors = animateFlightFinderColors(if (darkTheme) DarkFlightFinderColors else LightFlightFinderColors)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalFlightFinderColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
