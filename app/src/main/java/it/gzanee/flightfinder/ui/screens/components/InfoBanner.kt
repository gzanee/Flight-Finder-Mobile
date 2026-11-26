package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

enum class BannerTone { INFO, GOOD, WARN, BAD }

/** Un messaggio evidenziato, con l'icona del suo tono: informazione, conferma, avviso o errore. */
@Composable
fun InfoBanner(text: String, tone: BannerTone = BannerTone.WARN, modifier: Modifier = Modifier) {
    val (background, foreground) = when (tone) {
        BannerTone.INFO -> colors.surfaceVariant to colors.brand
        BannerTone.GOOD -> colors.goodSoft to colors.good
        BannerTone.WARN -> colors.warnSoft to colors.warn
        BannerTone.BAD -> colors.badSoft to colors.bad
    }
    val icon = when (tone) {
        BannerTone.INFO -> Icons.Outlined.Info
        BannerTone.GOOD -> Icons.Outlined.CheckCircle
        BannerTone.WARN -> Icons.Outlined.WarningAmber
        BannerTone.BAD -> Icons.Outlined.ErrorOutline
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Text(text, style = FlightFinderType.body, color = if (colors.isDark) foreground else colors.textPrimary)
    }
}
