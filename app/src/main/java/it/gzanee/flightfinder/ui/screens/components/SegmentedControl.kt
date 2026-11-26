package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Scelta fra poche opzioni: binario tenue a pillola, l'opzione scelta è la pillola piena. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
) {
    Row(modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceVariant).padding(3.dp)) {
        options.forEachIndexed { index, (value, label) ->
            val on = value == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.brandFill else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (on) colors.onBrandFill else colors.textPrimary
                icons.getOrNull(index)?.let { Icon(it, contentDescription = null, tint = ink, modifier = Modifier.padding(end = 6.dp).size(15.dp)) }
                Text(
                    label, style = FlightFinderType.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
