package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Etichetta di un campo: maiuscola, piccola e chiara (come "DA" sopra un valore). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier = modifier, style = FlightFinderType.overline, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** Etichetta maiuscola di un gruppo, con icona e un'azione facoltative a destra. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        Text(text.uppercase(), style = FlightFinderType.overline, color = colors.textTertiary, modifier = Modifier.weight(1f), maxLines = 1)
        if (action != null && onAction != null) {
            Text(action, style = FlightFinderType.label, color = colors.textSecondary, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, warn: Boolean = false) =
    Text(text, modifier = modifier, style = FlightFinderType.bodySm, color = if (warn) colors.warn else colors.textSecondary)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier = modifier, style = FlightFinderType.title, color = colors.textPrimary)

/** Intestazione di una card: icona e titolo maiuscoli in grigio, un'indicazione a destra. */
@Composable
fun SectionHeader(title: String, icon: ImageVector? = null, meta: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        Text(title.uppercase(), style = FlightFinderType.overline, color = colors.textTertiary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta != null) Text(meta, style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1)
    }
}

/** Piccola pillola di testo (codice IATA, conteggio): tenue, oppure piena se `strong`. */
@Composable
fun CodeBadge(text: String, modifier: Modifier = Modifier, strong: Boolean = false) = Text(
    text,
    modifier = modifier.clip(CircleShape)
        .background(if (strong) colors.brandFill else colors.surfaceVariant)
        .then(if (strong) Modifier else Modifier.border(1.dp, colors.border, CircleShape))
        .padding(horizontal = 9.dp, vertical = 3.dp),
    style = FlightFinderType.label.copy(fontFeatureSettings = "tnum"),
    color = if (strong) colors.onBrandFill else colors.textPrimary,
    maxLines = 1,
)

/** Icona in un cerchio tenue, a capo delle righe di elenco. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, filled: Boolean = false, size: Dp = 38.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(if (filled) colors.brandFill else colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (filled) colors.onBrandFill else colors.textPrimary, modifier = Modifier.size(size * 0.45f)) }
}

/** Una bandiera (emoji) in un cerchio tenue. */
@Composable
fun FlagTile(flag: String, modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(flag, style = FlightFinderType.title)
    }
}

/** Puntino di stato. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) = Box(modifier.size(7.dp).clip(CircleShape).background(color))

/** Riga sottile di separazione. */
@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(colors.border))
