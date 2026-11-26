package it.gzanee.flightfinder.ui.screens.diagnostics

import it.gzanee.flightfinder.ui.i18n.engineText
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.repository.ConnectionTest
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.Overline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkCheck
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit, onToggleTheme: () -> Unit) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    DiagnosticsScreenContent(result, isLoading, onRun = { viewModel.run() }, onBack = onBack, onToggleTheme = onToggleTheme)
}

/** Una sola ricerca vera, per sapere se Google risponde a questo telefono. */
@Composable
fun DiagnosticsScreenContent(result: ConnectionTest?, isLoading: Boolean, onRun: () -> Unit = {}, onBack: () -> Unit = {}, onToggleTheme: () -> Unit = {}) {
    FlightFinderScaffold(title = stringResource(R.string.test_title), subtitle = stringResource(R.string.test_subtitle), onBack = onBack, onToggleTheme = onToggleTheme) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HintText(stringResource(R.string.test_hint))
            FlightFinderButton(stringResource(R.string.test_now), onRun, Modifier.fillMaxWidth(), isLoading = isLoading, leadingIcon = Icons.Outlined.NetworkCheck)
            when (result?.ok) {
                true -> InfoBanner(stringResource(R.string.test_ok), BannerTone.GOOD)
                false -> InfoBanner(stringResource(R.string.test_failed), BannerTone.BAD)
                null -> Unit
            }
            if (result != null) {
                FlightFinderCard {
                    Overline(stringResource(R.string.google_answer))
                    result.lines.forEach { Text(engineText(it), color = colors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

@DevicePreview
@Composable
fun DiagnosticsScreenPreview() {
    FlightFinderTheme {
        DiagnosticsScreenContent(ConnectionTest(true, listOf("Risposta in 412 ms, 1954 kB.", "VY 6095 alle 22:20: 53,00 €")), isLoading = false)
    }
}
