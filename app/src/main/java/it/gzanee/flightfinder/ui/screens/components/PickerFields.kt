package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.format.long
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Campo che apre il selettore di data o ora. */
@Composable
private fun PickerBox(label: String?, text: String, isPlaceholder: Boolean, modifier: Modifier, onClear: (() -> Unit)?, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .background(colors.surface).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = if (isPlaceholder) colors.textTertiary else colors.textPrimary, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (onClear != null && !isPlaceholder) Text("×", color = colors.textSecondary, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClear))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String?, value: LocalDate, minimum: LocalDate?, modifier: Modifier = Modifier, onValueChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, value.long(), false, modifier, null) { open = true }
    if (!open) return
    val minMillis = minimum?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = minMillis == null || utcTimeMillis >= minMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla") } },
    ) { DatePicker(state = state, showModeToggle = false) }
}

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/** Un orario; vuoto quando `onClear` c'è e non si è scelto nulla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String?,
    value: LocalTime?,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    onClear: (() -> Unit)? = null,
    onValueChange: (LocalTime) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, value?.format(HH_MM) ?: placeholder, value == null, modifier, onClear) { open = true }
    if (!open) return
    val start = value ?: LocalTime.NOON
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                onValueChange(LocalTime.of(state.hour, state.minute))
                open = false
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla") } },
        text = { TimePicker(state = state) },
    )
}
