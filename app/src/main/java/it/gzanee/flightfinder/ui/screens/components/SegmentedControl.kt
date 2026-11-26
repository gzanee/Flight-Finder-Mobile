package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.Inter

/** Scelta fra poche opzioni affiancate: binario tenue con il cursore bianco sull'opzione scelta. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(9.dp)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(if (on) Modifier.shadow(2.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A)) else Modifier)
                    .clip(shape)
                    .background(if (on) (if (colors.isDark) colors.surfaceStrong else colors.surface) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (on) colors.brand else colors.textSecondary
                icons.getOrNull(index)?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 6.dp).size(16.dp)) }
                Text(
                    label, color = if (on) colors.brand else colors.textSecondary, fontFamily = Inter,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
