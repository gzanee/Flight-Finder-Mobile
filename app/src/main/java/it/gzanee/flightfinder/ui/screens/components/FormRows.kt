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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import java.time.DayOfWeek

val FieldShape = RoundedCornerShape(12.dp)

/** Campo di testo tenue, senza bordo finché non è attivo. */
@Composable
fun FlightFinderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    numeric: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FlightFinderType.body.copy(fontSize = FlightFinderType.titleSm.fontSize, color = colors.textPrimary),
            placeholder = placeholder?.let { { Text(it, style = FlightFinderType.body, color = colors.textTertiary) } },
            suffix = suffix?.let { { Text(it, style = FlightFinderType.body, color = colors.textSecondary) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = colors.brand) } },
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            shape = FieldShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = colors.surface,
    unfocusedContainerColor = colors.surfaceVariant,
    focusedBorderColor = colors.brand,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = colors.brand,
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textPrimary,
)

/** Contatore in riga: etichetta e nota a sinistra, − valore + a destra. */
@Composable
fun StepperRow(label: String, value: Int, range: IntRange, hint: String? = null, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FlightFinderType.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            if (hint != null) Text(hint, style = FlightFinderType.label, color = colors.textSecondary)
        }
        StepButton(Icons.Filled.Remove, "Meno ${label.lowercase()}", value > range.first) { onValueChange(value - 1) }
        Text("$value", style = FlightFinderType.time.copy(fontSize = FlightFinderType.titleSm.fontSize), color = colors.textPrimary, modifier = Modifier.padding(horizontal = 14.dp))
        StepButton(Icons.Filled.Add, "Più ${label.lowercase()}", value < range.last) { onValueChange(value + 1) }
    }
}

/** Contatore compatto in un riquadro tenue (es. Minimo 2 notti). */
@Composable
fun StepperBox(label: String, value: Int, unit: String, range: IntRange, modifier: Modifier = Modifier, onValueChange: (Int) -> Unit) {
    Row(
        modifier.clip(FieldShape).background(colors.surfaceVariant).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FlightFinderType.label, color = colors.textSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$value", style = FlightFinderType.time, color = colors.textPrimary)
                Text(" $unit", style = FlightFinderType.label, color = colors.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        StepButton(Icons.Filled.Remove, "Meno ${label.lowercase()}", value > range.first, filledOnTint = true) { onValueChange(value - 1) }
        Box(Modifier.size(6.dp))
        StepButton(Icons.Filled.Add, "Più ${label.lowercase()}", value < range.last, filledOnTint = true) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, filledOnTint: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
            .background(if (filledOnTint) colors.surfaceStrong else colors.surfaceVariant)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = if (enabled) colors.textPrimary else colors.textTertiary, modifier = Modifier.size(18.dp)) }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, hint: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onCheckedChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = FlightFinderType.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            if (hint != null) Text(hint, style = FlightFinderType.label, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.brandFill,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = colors.surfaceVariant,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

/** Opzioni a scelta singola come righe separate: la scelta è tinta di blu, con il segno di spunta. */
@Composable
fun <T> OptionList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FieldShape)
                    .background(if (on) colors.surfaceVariant else colors.surface)
                    .border(1.dp, if (on) colors.brand else colors.border, FieldShape)
                    .clickable(role = Role.RadioButton) { onSelect(value) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = FlightFinderType.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = colors.textPrimary, modifier = Modifier.weight(1f))
                SelectionMark(on)
            }
        }
    }
}

/** Cerchio vuoto o pieno con la spunta. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) colors.brandFill else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, colors.border, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) }
}

private val DAY_LABELS = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

/** I giorni ammessi, abbreviati in "Solo ven, sab" per le intestazioni. */
fun daysSummary(days: List<DayOfWeek>): String = when (days.size) {
    0 -> "Nessun giorno"
    7 -> "Tutti i giorni"
    else -> "Solo " + days.sorted().joinToString(", ") { DAY_LABELS[it.value - 1].lowercase() }
}

@Composable
fun DaysPicker(selected: List<DayOfWeek>, onChange: (List<DayOfWeek>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { i, day ->
            val on = day in selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) colors.brandFill else colors.surfaceVariant)
                    .clickable(role = Role.Checkbox) { onChange(if (on) selected - day else (selected + day).sorted()) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(DAY_LABELS[i], style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = if (on) Color.White else colors.textSecondary) }
        }
    }
}

/** Sezione che si apre e si chiude (es. "Altri filtri"). */
@Composable
fun CollapsibleSection(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.padding(end = 10.dp).size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FlightFinderType.titleSm, color = colors.textPrimary)
                if (summary != null) Text(summary, style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Chiudi" else "Apri", tint = colors.textSecondary)
        }
        if (open) Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
