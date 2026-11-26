package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.Poppins

/** PRIMARY pieno, SECONDARY tenue, OUTLINE con bordo, DANGER per Ferma/Elimina, GHOST solo testo. */
enum class ButtonStyle { PRIMARY, SECONDARY, OUTLINE, DANGER, GHOST }

/** Pulsante a pillola. */
@Composable
fun FlightFinderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val (container, content) = when (style) {
        ButtonStyle.PRIMARY -> colors.brandFill to colors.onBrandFill
        ButtonStyle.SECONDARY -> colors.surfaceVariant to colors.textPrimary
        ButtonStyle.OUTLINE -> Color.Transparent to colors.textPrimary
        ButtonStyle.DANGER -> colors.badSoft to colors.bad
        ButtonStyle.GHOST -> Color.Transparent to colors.textPrimary
    }
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = if (style == ButtonStyle.OUTLINE) BorderStroke(1.dp, colors.surfaceStrong) else null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (container == Color.Transparent) container else container.copy(alpha = 0.45f),
            disabledContentColor = content.copy(alpha = 0.45f),
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = content, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null && !isLoading) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(17.dp))
        }
    }
}
