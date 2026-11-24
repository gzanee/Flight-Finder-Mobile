package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

enum class ButtonStyle { PRIMARY, SECONDARY, DANGER, GHOST }

@Composable
fun FlightFinderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val (container, content) = when (style) {
        ButtonStyle.PRIMARY -> colors.brand to Color.White
        ButtonStyle.SECONDARY -> colors.brandSoft to colors.brand
        ButtonStyle.DANGER -> colors.badSoft to colors.bad
        ButtonStyle.GHOST -> Color.Transparent to colors.brand
    }
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.6f),
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = content, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}
