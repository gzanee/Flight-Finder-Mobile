package it.gzanee.flightfinder.ui.screens.diagnostics

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
    FlightFinderScaffold(title = "Prova connessione", subtitle = "Una ricerca reale su Google Flights", onBack = onBack, onToggleTheme = onToggleTheme) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HintText("Una sola ricerca reale, Venezia → Barcellona tra 30 giorni, dalla connessione di questo telefono. Serve a capire se Google risponde prima di avviare ricerche lunghe.")
            FlightFinderButton("Prova adesso", onRun, Modifier.fillMaxWidth(), isLoading = isLoading, leadingIcon = Icons.Outlined.NetworkCheck)
            when (result?.ok) {
                true -> InfoBanner("Google risponde: le ricerche funzionano da questa rete.", BannerTone.GOOD)
                false -> InfoBanner("La prova non è riuscita. Se Google limita le richieste, aspetta un quarto d’ora o prova con un’altra rete (Wi-Fi o dati mobili).", BannerTone.BAD)
                null -> Unit
            }
            if (result != null) {
                FlightFinderCard {
                    Overline("Risposta di Google")
                    result.lines.forEach { Text(it, color = colors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
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
