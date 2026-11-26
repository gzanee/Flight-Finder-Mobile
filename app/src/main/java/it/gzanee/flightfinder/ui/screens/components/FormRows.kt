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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

val FieldShape = InnerShape

/** Campo di testo in un riquadro tenue; l'etichetta maiuscola sta sopra. */
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FlightFinderType.body.copy(fontSize = FlightFinderType.titleSm.fontSize, fontWeight = FontWeight.Medium, color = colors.textPrimary),
            placeholder = placeholder?.let { { Text(it, style = FlightFinderType.body, color = colors.textTertiary) } },
            suffix = suffix?.let { { Text(it, style = FlightFinderType.body, color = colors.textSecondary) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = colors.textSecondary) } },
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            shape = FieldShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = colors.surfaceVariant,
    unfocusedContainerColor = colors.surfaceVariant,
    focusedBorderColor = colors.brandFill,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = colors.textPrimary,
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textPrimary,
)

/** Contatore in riga: etichetta e nota a sinistra, − valore + a destra. */
@Composable
fun StepperRow(label: String, value: Int, range: IntRange, hint: String? = null, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FlightFinderType.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            if (hint != null) Text(hint, style = FlightFinderType.label, color = colors.textTertiary)
        }
        RoundIconButton(Icons.Filled.Remove, "Meno ${label.lowercase()}", { onValueChange(value - 1) }, size = 34.dp, enabled = value > range.first)
        Text("$value", style = FlightFinderType.title.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary, modifier = Modifier.padding(horizontal = 14.dp))
        RoundIconButton(Icons.Filled.Add, "Più ${label.lowercase()}", { onValueChange(value + 1) }, size = 34.dp, enabled = value < range.last)
    }
}

/** Contatore in un riquadro: etichetta maiuscola, numero grande, − e + tondi sulla stessa riga. */
@Composable
fun StepperBox(label: String, value: Int, unit: String, range: IntRange, modifier: Modifier = Modifier, onValueChange: (Int) -> Unit) {
    Column(modifier.clip(FieldShape).background(colors.surfaceVariant).padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text("$value", style = FlightFinderType.headline.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary)
                Text(" $unit", style = FlightFinderType.label, color = colors.textTertiary, modifier = Modifier.padding(bottom = 4.dp))
            }
            RoundIconButton(
                Icons.Filled.Remove, "Meno ${label.lowercase()}", { onValueChange(value - 1) }, size = 32.dp, tinted = false, enabled = value > range.first,
                modifier = Modifier.border(1.dp, colors.surfaceStrong, CircleShape),
            )
            Box(Modifier.size(6.dp))
            RoundIconButton(Icons.Filled.Add, "Più ${label.lowercase()}", { onValueChange(value + 1) }, size = 32.dp, filled = true, enabled = value < range.last)
        }
    }
}

/** Interruttore come in TREK: binario pieno quando è acceso, pomello chiaro. */
@Composable
fun ToggleRow(label: String, checked: Boolean, hint: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onCheckedChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(label, style = FlightFinderType.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            if (hint != null) Text(hint, style = FlightFinderType.label, color = colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.brandFill,
                checkedThumbColor = colors.onBrandFill,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = colors.surfaceStrong,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Opzioni a scelta singola: righe dentro un unico riquadro, separate da un filo. */
@Composable
fun <T> OptionList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(FieldShape).background(colors.surfaceVariant)) {
        options.forEachIndexed { index, (value, label) ->
            val on = value == selected
            if (index > 0) Hairline(Modifier.padding(horizontal = 14.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.RadioButton) { onSelect(value) }.padding(horizontal = 14.dp, vertical = 12.dp),
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
            .then(if (selected) Modifier else Modifier.border(1.5.dp, colors.textTertiary.copy(alpha = 0.6f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onBrandFill, modifier = Modifier.size(14.dp)) }
}

private val DAY_LABELS = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

/** I giorni ammessi, abbreviati in "Solo ven, sab" per le intestazioni. */
fun daysSummary(days: List<DayOfWeek>): String = when (days.size) {
    0 -> "Nessun giorno"
    7 -> "Tutti i giorni"
    else -> "Solo " + days.sorted().joinToString(", ") { DAY_LABELS[it.value - 1].lowercase() }
}

/** I giorni come nella striscia dei giorni di TREK: il giorno scelto è la pillola piena. */
@Composable
fun DaysPicker(selected: List<DayOfWeek>, onChange: (List<DayOfWeek>) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceVariant).padding(3.dp)) {
        DayOfWeek.entries.forEachIndexed { i, day ->
            val on = day in selected
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(CircleShape).background(if (on) colors.brandFill else Color.Transparent)
                    .clickable(role = Role.Checkbox) { onChange(if (on) selected - day else (selected + day).sorted()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    DAY_LABELS[i], style = FlightFinderType.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) colors.onBrandFill else colors.textPrimary,
                )
            }
        }
    }
}

/** Riga che si apre (es. "Altri filtri"): icona in un cerchio, titolo, riepilogo e freccia. */
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
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) IconTile(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = FlightFinderType.titleSm, color = colors.textPrimary)
                if (summary != null) Text(summary, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1)
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (open) "Chiudi" else "Apri", tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
