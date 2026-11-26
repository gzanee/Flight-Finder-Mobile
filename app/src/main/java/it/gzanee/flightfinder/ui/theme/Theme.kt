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
    /** Fondo tenue dei campi, dei controlli segmentati e delle righe in evidenza. */
    val surfaceVariant: Color,
    /** Fondo più deciso: chip selezionate, badge dei codici IATA, pulsanti dei contatori. */
    val surfaceStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    /** Blu delle azioni e delle selezioni, per testi e icone. */
    val brand: Color,
    /** Riempimento del pulsante primario e degli elementi selezionati (testo bianco sopra). */
    val brandFill: Color,
    val brandSoft: Color,
    /** Arancio riservato al prezzo migliore. */
    val accent: Color,
    val accentSoft: Color,
    val good: Color,
    val goodSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    val isDark: Boolean,
)

val DarkFlightFinderColors = FlightFinderColors(
    background = CanvasDark,
    surface = SurfaceDark,
    surfaceVariant = TintDark,
    surfaceStrong = TintStrongDark,
    textPrimary = InkDark,
    textSecondary = InkMutedDark,
    textTertiary = InkFaintDark,
    border = BorderDark,
    brand = BrandDark,
    brandFill = BrandFillDark,
    brandSoft = TintStrongDark,
    accent = AccentDark,
    accentSoft = AccentSoftDark,
    good = GoodDark,
    goodSoft = GoodSoftDark,
    warn = WarnDark,
    warnSoft = WarnSoftDark,
    bad = BadDark,
    badSoft = BadSoftDark,
    isDark = true,
)

val LightFlightFinderColors = FlightFinderColors(
    background = CanvasLight,
    surface = SurfaceLight,
    surfaceVariant = TintLight,
    surfaceStrong = TintStrongLight,
    textPrimary = InkLight,
    textSecondary = InkMutedLight,
    textTertiary = InkFaintLight,
    border = BorderLight,
    brand = BrandLight,
    brandFill = BrandFillLight,
    brandSoft = TintStrongLight,
    accent = AccentLight,
    accentSoft = AccentSoftLight,
    good = GoodLight,
    goodSoft = GoodSoftLight,
    warn = WarnLight,
    warnSoft = WarnSoftLight,
    bad = BadLight,
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
    primary = BrandDark,
    onPrimary = CanvasDark,
    primaryContainer = TintStrongDark,
    onPrimaryContainer = InkDark,
    background = CanvasDark,
    surface = SurfaceDark,
    surfaceVariant = TintDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    onBackground = InkDark,
    onSurface = InkDark,
    onSurfaceVariant = InkMutedDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    secondaryContainer = TintStrongDark,
    error = BadDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandLight,
    onPrimary = Color.White,
    primaryContainer = TintStrongLight,
    onPrimaryContainer = InkLight,
    background = CanvasLight,
    surface = SurfaceLight,
    surfaceVariant = TintLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    onBackground = InkLight,
    onSurface = InkLight,
    onSurfaceVariant = InkMutedLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
    secondaryContainer = TintStrongLight,
    error = BadLight,
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
    surfaceStrong = animateColor(target.surfaceStrong),
    textPrimary = animateColor(target.textPrimary),
    textSecondary = animateColor(target.textSecondary),
    textTertiary = animateColor(target.textTertiary),
    border = animateColor(target.border),
    brand = animateColor(target.brand),
    brandFill = animateColor(target.brandFill),
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
