package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

@Composable
fun FlightFinderChip(
    label: String,
    detail: String? = null,
    selected: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.brand else colors.surfaceVariant)
            .clickable(enabled = onRemove != null || onClick != null) { (onRemove ?: onClick)?.invoke() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = if (selected) Color.White else colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (detail != null) Text(detail, color = if (selected) Color.White else colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (onRemove != null) Text("×", color = colors.textSecondary, fontSize = 16.sp)
    }
}

/** Piccola etichetta informativa (es. "Trolley incluso"). */
@Composable
fun Tag(text: String) = Text(
    text,
    color = colors.textSecondary,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.clip(CircleShape).background(colors.surfaceVariant).padding(horizontal = 10.dp, vertical = 3.dp),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
