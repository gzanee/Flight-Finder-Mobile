package it.gzanee.flightfinder.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.engine.Calendar

/** The palette of the previous app and the web page, light and dark. */
data class Palette(
    val bg: Color, val card: Color, val text: Color, val muted: Color, val faint: Color, val border: Color,
    val primary: Color, val primarySoft: Color, val good: Color, val goodSoft: Color, val warn: Color, val warnSoft: Color,
    val bad: Color, val badSoft: Color, val chip: Color, val hero: Color,
)

private val Light = Palette(
    bg = Color(0xFFF4F6FB), card = Color.White, text = Color(0xFF121826), muted = Color(0xFF5B6478), faint = Color(0xFF8A93A6),
    border = Color(0xFFE2E6EF), primary = Color(0xFF1A5CFF), primarySoft = Color(0xFFE8EFFF), good = Color(0xFF0F8A4F),
    goodSoft = Color(0xFFE5F6ED), warn = Color(0xFFA55A00), warnSoft = Color(0xFFFFF3E0), bad = Color(0xFFC0262D),
    badSoft = Color(0xFFFDECEC), chip = Color(0xFFEEF1F7), hero = Color(0xFF0B1F4D),
)

private val Dark = Palette(
    bg = Color(0xFF0C0F16), card = Color(0xFF161B26), text = Color(0xFFEEF1F7), muted = Color(0xFFA3ABBD), faint = Color(0xFF6F788C),
    border = Color(0xFF272E3D), primary = Color(0xFF5B8CFF), primarySoft = Color(0xFF1B2747), good = Color(0xFF4CC58A),
    goodSoft = Color(0xFF12291E), warn = Color(0xFFF0A54A), warnSoft = Color(0xFF2E2212), bad = Color(0xFFFF6B6B),
    badSoft = Color(0xFF2E1517), chip = Color(0xFF202736), hero = Color(0xFF0F1A36),
)

val LocalPalette = staticCompositionLocalOf { Light }

val colors: Palette @Composable get() = LocalPalette.current

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = p.primary, background = p.bg, surface = p.card, onSurface = p.text, onBackground = p.text, surfaceVariant = p.chip, outline = p.border)
    } else {
        lightColorScheme(primary = p.primary, background = p.bg, surface = p.card, onSurface = p.text, onBackground = p.text, surfaceVariant = p.chip, outline = p.border)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = colors
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = c.card,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) c.primary else c.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun Label(text: String) = Text(text, color = colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun Hint(text: String, tone: String = "muted") {
    val c = colors
    Text(text, color = when (tone) { "warn" -> c.warn; "bad" -> c.bad; "good" -> c.good; else -> c.muted }, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun Notice(text: String, tone: String = "warn") {
    val c = colors
    val (bg, fg) = when (tone) {
        "bad" -> c.badSoft to c.bad
        "good" -> c.goodSoft to c.good
        "info" -> c.primarySoft to c.primary
        else -> c.warnSoft to c.warn
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp)) {
        Text(text, color = fg, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun Title(text: String) = Text(text, color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

@Composable
fun Heading(text: String) = Text(text, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, kind: String = "primary", onClick: () -> Unit) {
    val c = colors
    val (bg, fg) = when (kind) {
        "secondary" -> c.primarySoft to c.primary
        "danger" -> c.badSoft to c.bad
        "ghost" -> Color.Transparent to c.primary
        else -> c.primary to Color.White
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = bg.copy(alpha = 0.5f), disabledContentColor = fg.copy(alpha = 0.6f)),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
            Box(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip).padding(3.dp)) {
        for ((v, label) in options) {
            val on = v == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c.card else Color.Transparent)
                    .clickable(role = Role.Tab) { onChange(v) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) c.text else c.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(label: String, detail: String? = null, selected: Boolean = false, onRemove: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val c = colors
    Row(
        Modifier.clip(CircleShape).background(if (selected) c.primary else c.chip)
            .clickable(enabled = onRemove != null || onClick != null) { (onRemove ?: onClick)?.invoke() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = if (selected) Color.White else c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (detail != null) Text(detail, color = if (selected) Color.White else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (onRemove != null) Text("×", color = c.muted, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, hint: String? = null, onChange: (Int) -> Unit) {
    val c = colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 16.sp)
            if (hint != null) Text(hint, color = c.muted, fontSize = 12.sp)
        }
        StepButton("−", value > min) { onChange(value - 1) }
        Text("$value", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        StepButton("+", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = colors
    Box(
        Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) c.primary else c.faint, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    numeric: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Label(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = placeholder?.let { { Text(it, color = colors.faint) } },
            suffix = suffix?.let { { Text(it, color = colors.muted) } },
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A box that opens the date or time picker. */
@Composable
private fun PickerBox(label: String?, text: String, placeholder: Boolean, modifier: Modifier, onClear: (() -> Unit)?, onClick: () -> Unit) {
    val c = colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Label(label)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.border, RoundedCornerShape(12.dp)).background(c.card)
                .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = if (placeholder) c.faint else c.text, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (onClear != null && !placeholder) Text("×", color = c.muted, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClear))
        }
    }
}

/** A date "YYYY-MM-DD" chosen with the Material calendar. */
@Composable
fun DateField(label: String?, value: String, minimum: String?, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, Fmt.longDay(value), false, modifier, null) { open = true }
    if (open) {
        val minMs = minimum?.let { Calendar.parseDay(it) * 86_400_000L }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Calendar.parseDay(value) * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = minMs == null || utcTimeMillis >= minMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Calendar.formatDay(it.floorDiv(86_400_000L))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

/** A time "HH:MM" chosen with the Material clock; empty allowed when `onClear` is given. */
@Composable
fun TimeField(label: String?, value: String, modifier: Modifier = Modifier, placeholder: String = "—", onClear: (() -> Unit)? = null, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, value.ifEmpty { placeholder }, value.isEmpty(), modifier, onClear) { open = true }
    if (open) {
        val start = value.ifEmpty { "12:00" }
        val state = rememberTimePickerState(start.take(2).toInt(), start.substring(3, 5).toInt(), is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}")
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
fun DaysPicker(value: List<Int>, onChange: (List<Int>) -> Unit) {
    val c = colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Fmt.DAY_LETTERS.forEachIndexed { i, l ->
            val on = i in value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) c.primary else c.chip)
                    .clickable(role = Role.Checkbox) { onChange(if (on) value - i else (value + i).sorted()) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) Color.White else c.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
fun Collapsible(title: String, right: String? = null, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val c = colors
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (right != null) Text(right, color = c.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false))
            Text(if (open) "▴" else "▾", color = c.muted)
        }
        if (open) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun <T> OptionList(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit) {
    val c = colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.border, RoundedCornerShape(12.dp))) {
        options.forEachIndexed { i, (v, label) ->
            if (i > 0) Box(Modifier.fillMaxWidth().background(c.border).size(1.dp))
            Row(
                Modifier.fillMaxWidth().background(if (v == value) c.primarySoft else c.card).clickable(role = Role.RadioButton) { onChange(v) }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                Text(label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (v == value) Text("✓", color = c.primary, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun Pill(text: String) {
    val c = colors
    Text(text, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(CircleShape).background(c.chip).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun RowGap(content: @Composable RowScope.() -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
