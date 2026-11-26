package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Un'azione nell'intestazione (storico, modifica, condividi...). */
data class HeaderAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/** Tela calda, più chiara in alto. */
@Composable
fun canvasBrush() = Brush.verticalGradient(0f to colors.canvasTop, 0.55f to colors.background, 1f to colors.canvasBottom)

/** Bordo chiaro che fa da riflesso sulle superfici "di vetro". */
@Composable
fun glassEdge(): Color = if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

/** Barra a pillola che galleggia sulla tela, con un'ombra morbida. */
@Composable
fun GlassBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(18.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x30000000))
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = 0.94f))
            .border(1.dp, glassEdge(), CircleShape),
    ) { content() }
}

/**
 * Intestazione a pillola che galleggia in alto: indietro, titolo, azioni rotonde e
 * il tema chiaro/scuro nel cerchio pieno. Sotto, la tela con la sua sfumatura.
 */
@Composable
fun FlightFinderScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    action: HeaderAction? = null,
    onToggleTheme: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(canvasBrush()).statusBarsPadding()) {
        GlassBar(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = if (onBack != null) 8.dp else 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", onBack)
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(title, style = FlightFinderType.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (action != null) RoundIconButton(action.icon, action.description, action.onClick, tinted = false)
                RoundIconButton(
                    if (colors.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    if (colors.isDark) "Modalità chiara" else "Modalità scura",
                    onToggleTheme, filled = true,
                )
            }
        }
        content()
    }
}

/** Bottone tondo: tenue, pieno (l'azione principale) o solo icona. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tinted: Boolean = true,
    size: Dp = 42.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (filled) colors.brandFill else if (tinted) colors.surfaceVariant else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = description, modifier = Modifier.size(size * 0.45f),
            tint = (if (filled) colors.onBrandFill else colors.textPrimary).copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

@DevicePreview
@Composable
fun FlightFinderScaffoldPreview() {
    FlightFinderTheme {
        FlightFinderScaffold(title = "Venezia → Londra", subtitle = "Completata", onBack = {}) {}
    }
}
