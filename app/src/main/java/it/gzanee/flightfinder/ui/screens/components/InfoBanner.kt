package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

enum class BannerTone { INFO, GOOD, WARN, BAD }

/** Un messaggio in un riquadro tenue, con il puntino del suo stato. */
@Composable
fun InfoBanner(text: String, tone: BannerTone = BannerTone.WARN, modifier: Modifier = Modifier) {
    val (dot, background) = when (tone) {
        BannerTone.INFO -> colors.info to colors.surfaceVariant
        BannerTone.GOOD -> colors.good to colors.goodSoft
        BannerTone.WARN -> colors.warn to colors.warnSoft
        BannerTone.BAD -> colors.bad to colors.badSoft
    }
    Row(
        modifier.fillMaxWidth().clip(InnerShape).background(background).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(dot))
        Text(text, style = FlightFinderType.body, color = colors.textPrimary)
    }
}
