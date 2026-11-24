package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

enum class BannerTone { INFO, GOOD, WARN, BAD }

/** Un messaggio evidenziato: avviso, errore, conferma o informazione. */
@Composable
fun InfoBanner(text: String, tone: BannerTone = BannerTone.WARN, modifier: Modifier = Modifier) {
    val (background, foreground) = when (tone) {
        BannerTone.INFO -> colors.brandSoft to colors.brand
        BannerTone.GOOD -> colors.goodSoft to colors.good
        BannerTone.WARN -> colors.warnSoft to colors.warn
        BannerTone.BAD -> colors.badSoft to colors.bad
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
    ) {
        Text(text, color = foreground, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
