package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Riquadro tenue con l'etichetta maiuscola sopra il valore; il tocco apre il selettore. */
@Composable
private fun PickerBox(label: String?, text: String, isPlaceholder: Boolean, icon: ImageVector, modifier: Modifier, onClear: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(FieldShape).background(colors.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (label != null) FieldLabel(label)
            Text(
                text,
                style = if (isPlaceholder) FlightFinderType.body else FlightFinderType.titleSm.copy(fontFeatureSettings = "tnum"),
                color = if (isPlaceholder) colors.textTertiary else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null && !isPlaceholder) {
            Icon(Icons.Filled.Close, contentDescription = "Cancella", tint = colors.textTertiary, modifier = Modifier.size(32.dp).clickable(onClick = onClear).padding(7.dp))
        } else {
            Icon(icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String?, value: LocalDate, minimum: LocalDate?, modifier: Modifier = Modifier, onValueChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, value.format(SHORT_DATE), false, Icons.Outlined.CalendarMonth, modifier, null) { open = true }
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
            }) { Text("OK", color = colors.brand) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla", color = colors.textSecondary) } },
        colors = DatePickerDefaults.colors(containerColor = colors.surface),
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = colors.surface,
                selectedDayContainerColor = colors.brandFill,
                selectedDayContentColor = colors.onBrandFill,
                todayDateBorderColor = colors.brandFill,
                todayContentColor = colors.textPrimary,
            ),
        )
    }
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
    PickerBox(label, value?.format(HH_MM) ?: placeholder, value == null, Icons.Outlined.Schedule, modifier, onClear) { open = true }
    if (!open) return
    val start = value ?: LocalTime.NOON
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = { open = false },
        containerColor = colors.surface,
        confirmButton = {
            TextButton(onClick = {
                onValueChange(LocalTime.of(state.hour, state.minute))
                open = false
            }) { Text("OK", color = colors.brand) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla", color = colors.textSecondary) } },
        text = { TimePicker(state = state) },
    )
}
