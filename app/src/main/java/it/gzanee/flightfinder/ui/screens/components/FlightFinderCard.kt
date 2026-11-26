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

val CardShape = RoundedCornerShape(16.dp)

/**
 * Pannello bianco con bordo sottile, appoggiato sulla tela senza ombra.
 * `highlighted` lo porta in primo piano: bordo blu e un'ombra appena percettibile.
 */
@Composable
fun FlightFinderCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.shadow(10.dp, CardShape, ambientColor = Color(0x140F172A), spotColor = Color(0x1F0F172A)) else Modifier)
            .then(if (onClick != null) Modifier.clip(CardShape).clickable(onClick = onClick) else Modifier),
        shape = CardShape,
        color = colors.surface,
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) colors.brand else colors.border),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}
