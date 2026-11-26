package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/**
 * Pillola: tenue con un filo di bordo; piena quando è scelta.
 * Con `onRemove` mostra la × (aeroporti scelti); `detail` aggiunge un codice o un conteggio.
 */
@Composable
fun FlightFinderChip(
    label: String,
    detail: String? = null,
    selected: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val ink = if (selected) colors.onBrandFill else colors.textPrimary
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .background(if (selected) colors.brandFill else colors.surfaceVariant)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.border, CircleShape))
            .clickable(enabled = onRemove != null || onClick != null) { (onRemove ?: onClick)?.invoke() }
            .padding(start = 14.dp, end = if (onRemove != null) 11.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
        Text(label, style = FlightFinderType.body.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
        if (detail != null) Text(detail, style = FlightFinderType.label, color = if (selected) ink.copy(alpha = 0.7f) else colors.textTertiary)
        if (onRemove != null) Icon(Icons.Filled.Close, contentDescription = "Rimuovi $label", tint = colors.textTertiary, modifier = Modifier.size(15.dp))
    }
}

/** Piccola etichetta informativa; `dot` aggiunge il puntino di stato (es. verde per il prezzo migliore). */
@Composable
fun Tag(text: String, icon: ImageVector? = null, accent: Boolean = false, dot: Color? = null) = Row(
    Modifier.clip(CircleShape).background(colors.surfaceVariant).border(1.dp, colors.border, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    val mark = dot ?: if (accent) colors.accent else null
    if (mark != null) StatusDot(mark)
    if (icon != null) Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
    Text(text, style = FlightFinderType.label, color = colors.textPrimary, maxLines = 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
