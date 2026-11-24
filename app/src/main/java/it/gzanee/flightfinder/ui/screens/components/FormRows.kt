package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import java.time.DayOfWeek

@Composable
fun FlightFinderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    numeric: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = placeholder?.let { { Text(it, color = colors.textTertiary) } },
            suffix = suffix?.let { { Text(it, color = colors.textSecondary) } },
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun StepperRow(label: String, value: Int, range: IntRange, hint: String? = null, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.textPrimary, fontSize = 16.sp)
            if (hint != null) Text(hint, color = colors.textSecondary, fontSize = 12.sp)
        }
        StepButton("−", "Meno ${label.lowercase()}", value > range.first) { onValueChange(value - 1) }
        Text("$value", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        StepButton("+", "Più ${label.lowercase()}", value < range.last) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).border(1.dp, colors.border, CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) colors.brand else colors.textTertiary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Elenco di opzioni a scelta singola, con la spunta sulla selezionata. */
@Composable
fun <T> OptionList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, colors.border, RoundedCornerShape(12.dp))) {
        options.forEachIndexed { index, (value, label) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(
                Modifier.fillMaxWidth().background(if (value == selected) colors.brandSoft else colors.surface)
                    .clickable(role = Role.RadioButton) { onSelect(value) }.padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                Text(label, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (value == selected) Text("✓", color = colors.brand, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private val DAY_LABELS = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

@Composable
fun DaysPicker(selected: List<DayOfWeek>, onChange: (List<DayOfWeek>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { i, day ->
            val on = day in selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) colors.brand else colors.surfaceVariant)
                    .clickable(role = Role.Checkbox) { onChange(if (on) selected - day else (selected + day).sorted()) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(DAY_LABELS[i], color = if (on) Color.White else colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

/** Sezione che si apre e si chiude (es. "Altri filtri"). */
@Composable
fun CollapsibleSection(title: String, summary: String? = null, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (summary != null) Text(summary, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = colors.textSecondary)
        }
        if (open) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
