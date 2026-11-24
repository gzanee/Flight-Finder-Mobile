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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.BuildConfig
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme

/** Un'azione tonda nell'intestazione (indietro, storico, modifica...). */
data class HeaderAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/**
 * Intestazione con titolo centrato, sottotitolo (di default la versione) e pulsanti
 * tondi ai lati: indietro a sinistra, tema chiaro/scuro e un'azione a destra.
 */
@Composable
fun FlightFinderScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = "v${BuildConfig.VERSION_NAME}",
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            // Il titolo occupa tutta la barra: il padding lascia spazio ai pulsanti tondi ai lati.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (action != null) 100.dp else 52.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onBack != null) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", onBack, Modifier.align(Alignment.CenterStart))
            }
            Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (action != null) CircleIconButton(action.icon, action.description, action.onClick)
                CircleIconButton(
                    icon = if (colors.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    description = if (colors.isDark) "Modalità chiara" else "Modalità scura",
                    onClick = onToggleTheme,
                )
            }
        }
        content()
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}

@DevicePreview
@Composable
fun FlightFinderScaffoldPreview() {
    FlightFinderTheme {
        FlightFinderScaffold(title = "Venezia → Londra", onBack = {}) {}
    }
}
