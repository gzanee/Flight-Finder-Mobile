package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Etichetta di un campo. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)

/** Etichetta maiuscola sopra un gruppo, con un'azione facoltativa a destra (es. "SELEZIONATI (2) · Rimuovi tutti"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = FlightFinderType.overline, color = colors.textSecondary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = colors.brand, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, warn: Boolean = false) =
    Text(text, modifier = modifier, style = FlightFinderType.bodySm, color = if (warn) colors.warn else colors.textSecondary)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier = modifier, style = FlightFinderType.title, color = colors.textPrimary)

/** Intestazione di una sezione: icona blu, titolo e un'indicazione breve a destra (es. "Solo ven, sab"). */
@Composable
fun SectionHeader(title: String, icon: ImageVector? = null, meta: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(20.dp))
        Text(title, style = FlightFinderType.titleSm, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta != null) Text(meta, style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1)
    }
}

/** Codice IATA o breve etichetta in un riquadro tenue (VCE, FR, FLESSIBILE). */
@Composable
fun CodeBadge(text: String, modifier: Modifier = Modifier, strong: Boolean = false) = Text(
    text,
    modifier = modifier.clip(RoundedCornerShape(6.dp)).background(if (strong) colors.brandFill else colors.surfaceStrong).padding(horizontal = 7.dp, vertical = 2.dp),
    style = FlightFinderType.overline.copy(fontWeight = FontWeight.Bold),
    color = if (strong) Color.White else colors.brand,
    maxLines = 1,
)

/** Icona in un quadrato tenue, a capo delle righe di elenco. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (filled) colors.brandFill else colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (filled) Color.White else colors.brand, modifier = Modifier.size(20.dp)) }
}

/** Una bandiera (emoji) in un quadrato tenue. */
@Composable
fun FlagTile(flag: String, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(flag, style = FlightFinderType.title)
    }
}

/** Puntino separatore tra due informazioni. */
@Composable
fun Dot() = Box(Modifier.padding(horizontal = 6.dp).size(3.dp).clip(CircleShape).background(colors.textTertiary))
