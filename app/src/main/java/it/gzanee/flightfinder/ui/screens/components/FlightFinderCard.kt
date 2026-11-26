package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** Tre misure: contenitori 26, superfici annidate 18, dettagli 12. */
val CardShape = RoundedCornerShape(26.dp)
val InnerShape = RoundedCornerShape(18.dp)
val DetailShape = RoundedCornerShape(12.dp)

/** Superficie annidata: più chiara della card, con il suo bordo di luce. */
@Composable
fun Modifier.nested(shape: androidx.compose.ui.graphics.Shape = InnerShape): Modifier =
    clip(shape).background(colors.nested).border(1.dp, colors.nestedEdge, shape)

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
            .shadow(if (inverted) 16.dp else 4.dp, CardShape, ambientColor = Color(0x0F000000), spotColor = Color(if (inverted) 0x40000000 else 0x12000000))
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
