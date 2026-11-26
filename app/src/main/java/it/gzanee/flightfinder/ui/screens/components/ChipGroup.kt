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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/**
 * Pillola: bianca con bordo quando è un'opzione, tinta blu quando è scelta.
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
    val filled = selected || onRemove != null
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (filled) colors.brandSoft else colors.surface)
            .then(if (filled) Modifier else Modifier.border(1.dp, colors.border, CircleShape))
            .clickable(enabled = onRemove != null || onClick != null) { (onRemove ?: onClick)?.invoke() }
            .padding(start = 14.dp, end = if (onRemove != null) 10.dp else 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (filled) colors.brand else colors.textSecondary
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, style = FlightFinderType.body.copy(fontWeight = FontWeight.SemiBold), color = if (filled) colors.textPrimary else colors.textSecondary, maxLines = 1)
        if (detail != null) Text(detail, style = FlightFinderType.label.copy(fontWeight = FontWeight.Bold), color = tint)
        if (onRemove != null) Icon(Icons.Filled.Close, contentDescription = "Rimuovi $label", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}

/** Piccola etichetta informativa (es. "Trolley incluso"). */
@Composable
fun Tag(text: String, icon: ImageVector? = null, accent: Boolean = false) = Row(
    Modifier.clip(CircleShape).background(if (accent) colors.accentSoft else colors.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
) {
    val tint = if (accent) colors.accent else colors.brand
    if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    Text(text, style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = if (accent) colors.accent else colors.textPrimary, maxLines = 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
