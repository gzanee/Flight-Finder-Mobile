package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Un'azione nell'intestazione (storico, modifica, condividi...). */
data class HeaderAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/**
 * Intestazione sobria: freccia indietro, titolo allineato a sinistra con un sottotitolo
 * opzionale, azioni a destra e il tema chiaro/scuro in un cerchio pieno.
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .heightIn(min = 64.dp)
                .padding(start = if (onBack != null) 4.dp else 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", onBack)
            Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)) {
                Text(title, style = FlightFinderType.title.copy(fontSize = 20.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (action != null) IconAction(action.icon, action.description, action.onClick)
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.brandFill)
                        .clickable(role = Role.Button, onClickLabel = if (colors.isDark) "Modalità chiara" else "Modalità scura", onClick = onToggleTheme),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (colors.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = if (colors.isDark) "Modalità chiara" else "Modalità scura",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        content()
    }
}

/** Icona toccabile senza contorno, area di 48 dp. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = colors.textPrimary) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@DevicePreview
@Composable
fun FlightFinderScaffoldPreview() {
    FlightFinderTheme {
        FlightFinderScaffold(title = "Venezia → Londra", subtitle = "Completata", onBack = {}) {}
    }
}
