package it.gzanee.flightfinder.ui.screens.trip

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.ui.format.asDuration
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.clock
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.safeGoogleLink
import it.gzanee.flightfinder.ui.format.shortDay
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.LegRow
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.results.names
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TripScreen(viewModel: TripViewModel, onBack: () -> Unit, onToggleTheme: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    TripScreenContent(
        state = state,
        onBack = onBack,
        onToggleTheme = onToggleTheme,
        onOpen = { url -> safeGoogleLink(url)?.let { runCatching { uriHandler.openUri(it) } } },
        onRecheck = { viewModel.recheck() },
        onShare = { text -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) },
    )
}

/** Un viaggio: voli, freschezza del prezzo, ricontrollo e link per prenotare su Google Flights. */
@Composable
fun TripScreenContent(
    state: TripUiState,
    onBack: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onOpen: (String?) -> Unit = {},
    onRecheck: () -> Unit = {},
    onShare: (String) -> Unit = {},
) {
    val names = state.catalog.names()
    FlightFinderScaffold(title = "Viaggio", subtitle = null, onBack = onBack, onToggleTheme = onToggleTheme) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val trip = state.trip
            if (trip == null) {
                if (state.isLoaded) {
                    InfoBanner(state.message?.text ?: "Questo viaggio non è più tra i risultati.", state.message?.tone ?: BannerTone.WARN)
                    FlightFinderButton("Torna ai risultati", onBack, style = ButtonStyle.SECONDARY)
                }
                return@Column
            }
            TripDetails(trip, names, state, onOpen, onRecheck, onShare)
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun TripDetails(trip: Trip, names: PlaceNames, state: TripUiState, onOpen: (String?) -> Unit, onRecheck: () -> Unit, onShare: (String) -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    val bookingLink = if (inbound != null) trip.roundTripUrl ?: trip.outbound.bookingUrl else trip.outbound.bookingUrl
    Column {
        Text("${names.city(trip.origin)} → ${names.flag(trip.destination)} ${names.city(trip.destination)}", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        HintText(
            (if (inbound != null) count(trip.nights ?: 0, "notte", "notti") else "Solo andata") +
                (trip.usefulMinutes?.takeIf { inbound != null }?.let { " · ${it.asDuration()} ${if (trip.destination == away) "a destinazione" else "tra i voli"}" } ?: "") +
                (if (home != null && home != trip.origin) " · rientro a ${names.city(home)}" else ""),
        )
    }
    FlightFinderCard {
        Text(trip.price.asEuro(), color = colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        HintText((if (inbound != null) "andata e ritorno, due biglietti separati" else "solo andata") + if (trip.outbound.travelers > 1) ", ${trip.outbound.travelers} persone" else "")
        HintText(
            if (trip.needsRecheck == true) "Prezzo da aggiornare" else "Prezzo delle ${trip.checkedAt.atZone(ZoneId.systemDefault()).format(CLOCK)}",
            warn = trip.needsRecheck == true,
        )
    }
    state.message?.let { InfoBanner(it.text, it.tone) }
    FlightFinderCard {
        LegRow(trip.outbound, "Andata", names)
        if (inbound != null) LegRow(inbound, "Ritorno", names)
    }
    if (away != null && away != trip.destination) {
        InfoBanner("Arrivi a ${state.catalog?.details(trip.destination)?.name} e riparti da ${state.catalog?.details(away)?.name}: il trasferimento è a carico tuo.")
    }
    trip.roundTripFrom?.let {
        InfoBanner("Con questa andata Google vende un biglietto A/R unico da ${it.asEuro()}, ma con un ritorno che può essere diverso da questo.", BannerTone.INFO)
    }
    FlightFinderButton("Vedi offerta su Google Flights", { onOpen(bookingLink) }, Modifier.fillMaxWidth(), enabled = safeGoogleLink(bookingLink) != null)
    if (inbound != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlightFinderButton("Solo andata", { onOpen(trip.outbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.SECONDARY)
            FlightFinderButton("Solo ritorno", { onOpen(inbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.SECONDARY)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlightFinderButton("Aggiorna prezzo", onRecheck, Modifier.weight(1f), ButtonStyle.SECONDARY, isLoading = state.isRechecking)
        FlightFinderButton("Condividi", {
            onShare(
                listOfNotNull(
                    "${names.city(trip.origin)} → ${names.city(trip.destination)} · ${trip.price.asEuro()}${if (inbound != null) " andata e ritorno" else ""}",
                    "Andata: ${trip.outbound.first.departure.shortDay()} ${trip.outbound.first.departure.clock()}",
                    inbound?.let { "Ritorno: ${it.first.departure.shortDay()} ${it.first.departure.clock()}" },
                    safeGoogleLink(bookingLink),
                ).joinToString("\n"),
            )
        }, Modifier.weight(1f), ButtonStyle.SECONDARY)
    }
    HintText("Dati di shopping osservati su Google Flights: numero volo, aeroporti, orari e prezzo. Non sono una conferma della compagnia. Condizioni e bagagli vanno verificati dal venditore.")
}
