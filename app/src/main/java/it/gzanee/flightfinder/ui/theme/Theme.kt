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
    /** Tela: sfumatura dall'alto (più chiara) al basso. */
    val canvasTop: Color,
    val background: Color,
    val canvasBottom: Color,
    /** Card. */
    val surface: Color,
    /** Superficie annidata in una card: più chiara della card, con un bordo di luce. */
    val nested: Color,
    val nestedEdge: Color,
    /** Tinta d'inchiostro: chip, binari dei selettori, cerchi delle icone, campi. */
    val surfaceVariant: Color,
    val surfaceStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Righe di separazione e bordi, appena visibili. */
    val border: Color,
    /** Inchiostro delle azioni: lo stesso del testo, niente blu. */
    val brand: Color,
    /** L'elemento attivo pieno (pillola scelta, pulsante principale) e il testo sopra. */
    val brandFill: Color,
    val onBrandFill: Color,
    val brandSoft: Color,
    /** Il prezzo migliore: verde "confermato". */
    val accent: Color,
    val accentSoft: Color,
    val good: Color,
    val goodSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    val info: Color,
    /** Vetro della barra in alto e del dock (più coprente). */
    val glass: Color,
    val dock: Color,
    val isDark: Boolean,
)

val DarkFlightFinderColors = FlightFinderColors(
    canvasTop = CanvasTopDark,
    background = CanvasDark,
    canvasBottom = CanvasBottomDark,
    surface = CardDark,
    nested = NestedDark,
    nestedEdge = NestedEdgeDark,
    surfaceVariant = InnerDark,
    surfaceStrong = InnerStrongDark,
    textPrimary = InkDark,
    textSecondary = MutedDark,
    textTertiary = FaintDark,
    border = HairlineDark,
    brand = InkDark,
    brandFill = ActDark,
    onBrandFill = OnActDark,
    brandSoft = InnerStrongDark,
    accent = Confirmed,
    accentSoft = Confirmed.copy(alpha = 0.16f),
    good = Confirmed,
    goodSoft = Confirmed.copy(alpha = 0.16f),
    warn = Pending,
    warnSoft = Pending.copy(alpha = 0.16f),
    bad = Color(0xFFFF6B7A),
    badSoft = Danger.copy(alpha = 0.18f),
    info = Info,
    glass = GlassDark,
    dock = DockDark,
    isDark = true,
)

val LightFlightFinderColors = FlightFinderColors(
    canvasTop = CanvasTopLight,
    background = CanvasLight,
    canvasBottom = CanvasBottomLight,
    surface = CardLight,
    nested = NestedLight,
    nestedEdge = NestedEdgeLight,
    surfaceVariant = InnerLight,
    surfaceStrong = InnerStrongLight,
    textPrimary = InkLight,
    textSecondary = MutedLight,
    textTertiary = FaintLight,
    border = HairlineLight,
    brand = InkLight,
    brandFill = ActLight,
    onBrandFill = OnActLight,
    brandSoft = InnerStrongLight,
    accent = Confirmed,
    accentSoft = Confirmed.copy(alpha = 0.12f),
    good = Confirmed,
    goodSoft = Confirmed.copy(alpha = 0.12f),
    warn = PendingInk,
    warnSoft = Pending.copy(alpha = 0.14f),
    bad = Danger,
    badSoft = Danger.copy(alpha = 0.10f),
    info = Info,
    glass = GlassLight,
    dock = DockLight,
    isDark = false,
)

val LocalFlightFinderColors = staticCompositionLocalOf { LightFlightFinderColors }

object FlightFinderColorScheme {
    val colors: FlightFinderColors
        @Composable
        get() = LocalFlightFinderColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = InkDark,
    onPrimary = OnActDark,
    primaryContainer = InnerStrongDark,
    onPrimaryContainer = InkDark,
    secondaryContainer = InnerStrongDark,
    background = CanvasDark,
    surface = CardDark,
    surfaceVariant = InnerDark,
    surfaceContainer = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = InnerDark,
    onBackground = InkDark,
    onSurface = InkDark,
    onSurfaceVariant = MutedDark,
    outline = InnerStrongDark,
    outlineVariant = InnerDark,
    error = Danger,
)

private val LightColorScheme = lightColorScheme(
    primary = InkLight,
    onPrimary = OnActLight,
    primaryContainer = InnerStrongLight,
    onPrimaryContainer = InkLight,
    secondaryContainer = InnerStrongLight,
    background = CanvasLight,
    surface = CardLight,
    surfaceVariant = InnerLight,
    surfaceContainer = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = InnerLight,
    onBackground = InkLight,
    onSurface = InkLight,
    onSurfaceVariant = MutedLight,
    outline = InnerStrongLight,
    outlineVariant = InnerLight,
    error = Danger,
)

private const val THEME_ANIM_DURATION = 400

@Composable
private fun animateColor(target: Color) =
    animateColorAsState(target, tween(THEME_ANIM_DURATION), label = "").value

@Composable
private fun animateFlightFinderColors(target: FlightFinderColors) = target.copy(
    canvasTop = animateColor(target.canvasTop),
    nested = animateColor(target.nested),
    canvasBottom = animateColor(target.canvasBottom),
    onBrandFill = animateColor(target.onBrandFill),
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
