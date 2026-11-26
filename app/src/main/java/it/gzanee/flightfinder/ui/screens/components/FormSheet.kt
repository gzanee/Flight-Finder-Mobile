package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.R
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Pannello dal basso con un titolo, i controlli che scorrono e il pulsante pieno "Fatto". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(title, style = FlightFinderType.headline, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            FlightFinderButton(
                stringResource(R.string.done), onDismiss,
                Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp).fillMaxWidth().heightIn(min = 54.dp),
            )
        }
    }
}
