package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

val CardShape = RoundedCornerShape(22.dp)
val InnerShape = RoundedCornerShape(16.dp)

/**
 * Card chiara, molto arrotondata, con un filo di luce sul bordo e un'ombra appena accennata.
 * `highlighted` la segna con il contorno pieno; `inverted` la rende scura (il riquadro del prezzo).
 */
@Composable
fun FlightFinderCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    inverted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    spacing: Int = 14,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (inverted) 14.dp else 6.dp, CardShape, ambientColor = Color(0x14000000), spotColor = Color(if (inverted) 0x40000000 else 0x14000000))
            .then(if (onClick != null) Modifier.clip(CardShape).clickable(onClick = onClick) else Modifier),
        shape = CardShape,
        color = if (inverted) colors.brandFill else colors.surface,
        border = when {
            highlighted -> BorderStroke(1.5.dp, colors.brandFill)
            inverted -> null
            else -> BorderStroke(1.dp, glassEdge())
        },
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}
