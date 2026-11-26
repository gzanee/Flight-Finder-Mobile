package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Un'azione nell'intestazione (storico, modifica, condividi...). */
data class HeaderAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/** Tela calda: sfumatura radiale con la luce appena sopra lo schermo (120% × 80% a 50%, −10%). */
@Composable
fun Modifier.canvas(): Modifier {
    val top = colors.canvasTop
    val mid = colors.background
    val bottom = colors.canvasBottom
    return drawBehind {
        drawRect(bottom)
        val center = Offset(size.width / 2f, -0.1f * size.height)
        val rx = 1.2f * size.width
        val ry = 0.8f * size.height
        // Ellisse: si scala il gradiente circolare in verticale.
        scale(1f, ry / rx, pivot = center) {
            drawCircle(Brush.radialGradient(0f to top, 0.55f to mid, 1f to bottom, center = center, radius = rx), radius = rx * 1.5f, center = center)
        }
    }
}

/** Bordo chiaro che fa da riflesso sulle superfici "di vetro". */
@Composable
fun glassEdge(): Color = if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

/**
 * Barra a pillola che galleggia sulla tela: vetro con bordo di luce e ombra concentrata.
 * `dock` è il materiale più coprente e l'ombra più profonda della barra in basso.
 */
@Composable
fun GlassBar(modifier: Modifier = Modifier, dock: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(if (dock) 22.dp else 16.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(if (dock) 0x59000000 else 0x38000000))
            .clip(CircleShape)
            .background(if (dock) colors.dock else colors.glass)
            .border(1.dp, glassEdge(), CircleShape),
    ) { content() }
}

/**
 * Intestazione a pillola che galleggia in alto: indietro, titolo, azioni rotonde e
 * il tema chiaro/scuro nel cerchio pieno. Sotto, la tela con la sua sfumatura.
 */
@Composable
fun FlightFinderScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    action: HeaderAction? = null,
    onToggleTheme: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().canvas().statusBarsPadding()) {
        GlassBar(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 6.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = if (onBack != null) 4.dp else 18.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), onBack)
                Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                    Text(title, style = FlightFinderType.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (action != null) RoundIconButton(action.icon, action.description, action.onClick, tinted = false)
                RoundIconButton(
                    if (colors.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    stringResource(if (colors.isDark) R.string.cd_light_mode else R.string.cd_dark_mode),
                    onToggleTheme, filled = true,
                )
            }
        }
        content()
    }
}

/**
 * Bottone tondo: tenue, pieno (l'azione principale) o solo icona. Il cerchio disegnato è
 * `size` (38 dp di norma), l'area da toccare almeno 48 dp.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tinted: Boolean = true,
    size: Dp = 38.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
    touch: Dp = 48.dp,
) {
    val ink = (if (filled) colors.onBrandFill else colors.textPrimary).copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        Modifier.size(maxOf(size, touch)).clip(CircleShape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier.size(size).clip(CircleShape)
                .background(if (filled) colors.brandFill else if (tinted) colors.surfaceVariant else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(size * 0.42f), color = ink, strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.47f), tint = ink)
            }
        }
    }
}

@DevicePreview
@Composable
fun FlightFinderScaffoldPreview() {
    FlightFinderTheme {
        FlightFinderScaffold(title = "Venezia → Londra", subtitle = "Completata", onBack = {}) {}
    }
}
