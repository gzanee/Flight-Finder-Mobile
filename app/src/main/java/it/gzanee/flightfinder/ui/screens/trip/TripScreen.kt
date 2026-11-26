package it.gzanee.flightfinder.ui.screens.trip

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.screens.components.GlassBar
import it.gzanee.flightfinder.ui.screens.components.RoundIconButton
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.ConfirmationNumber
import it.gzanee.flightfinder.ui.screens.components.ListRow
import it.gzanee.flightfinder.ui.screens.components.SectionHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
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
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.IconTile
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.LegRow
import it.gzanee.flightfinder.ui.screens.components.Overline
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.components.StayDivider
import it.gzanee.flightfinder.ui.screens.results.names
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
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
    val trip = state.trip
    FlightFinderScaffold(
        title = "Dettaglio viaggio",
        onBack = onBack,
        action = trip?.let { HeaderAction(Icons.Outlined.Share, "Condividi") { onShare(shareText(it, names)) } },
        onToggleTheme = onToggleTheme,
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (trip == null) {
                if (state.isLoaded) {
                    InfoBanner(state.message?.text ?: "Questo viaggio non è più tra i risultati.", state.message?.tone ?: BannerTone.WARN)
                    FlightFinderButton("Torna ai risultati", onBack, style = ButtonStyle.SECONDARY, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack)
                }
                return@Column
            }
            TripDetails(trip, names, state, onOpen)
        }
        if (trip != null) {
            val link = trip.bookingLink()
            // Dock in basso: prezzo e prenotazione sempre a portata di pollice.
            GlassBar(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth(), dock = true) {
                Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(start = 4.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(Icons.Outlined.Refresh, "Aggiorna prezzo", onRecheck, loading = state.isRechecking)
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        FieldLabel("Totale")
                        Text(trip.price.asEuro(), style = FlightFinderType.titleSm.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary, maxLines = 1)
                    }
                    FlightFinderButton("Google Flights", { onOpen(link) }, enabled = safeGoogleLink(link) != null, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
                }
            }
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private fun Trip.bookingLink() = if (inbound != null) roundTripUrl ?: outbound.bookingUrl else outbound.bookingUrl

private fun shareText(trip: Trip, names: PlaceNames) = listOfNotNull(
    "${names.city(trip.origin)} → ${names.city(trip.destination)} · ${trip.price.asEuro()}${if (trip.inbound != null) " andata e ritorno" else ""}",
    "Andata: ${trip.outbound.first.departure.shortDay()} ${trip.outbound.first.departure.clock()}",
    trip.inbound?.let { "Ritorno: ${it.first.departure.shortDay()} ${it.first.departure.clock()}" },
    safeGoogleLink(trip.bookingLink()),
).joinToString("\n")

@Composable
private fun TripDetails(trip: Trip, names: PlaceNames, state: TripUiState, onOpen: (String?) -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    val country = names.country(trip.destination)
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Overline((names.city(trip.destination) + if (country.isNotEmpty()) " · $country" else ""), icon = Icons.Outlined.Place)
        Text(
            "${if (inbound != null) "Andata e ritorno" else "Solo andata"} da ${names.city(trip.origin)}",
            style = FlightFinderType.headline, color = colors.textPrimary,
        )
    }
    // Il prezzo su una card scura, come il totale delle spese in TREK.
    FlightFinderCard(inverted = true, spacing = 4) {
        Text(
            (if (inbound != null) "Tariffa combinata" else "Tariffa").uppercase(), style = FlightFinderType.overline,
            color = colors.onBrandFill.copy(alpha = 0.55f),
        )
        Text(trip.price.asEuro(), style = FlightFinderType.priceXl, color = colors.onBrandFill)
        Text(
            listOf(
                if (trip.outbound.travelers > 1) "Totale per ${trip.outbound.travelers} persone" else "Totale per passeggero",
                if (trip.needsRecheck == true) "prezzo da aggiornare" else "prezzo delle ${trip.checkedAt.atZone(ZoneId.systemDefault()).format(CLOCK)}",
            ).joinToString(" · "),
            style = FlightFinderType.label, color = colors.onBrandFill.copy(alpha = if (trip.needsRecheck == true) 1f else 0.7f),
        )
    }
    FlightFinderCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), spacing = 0) {
        ListRow(
            title = trip.outbound.first.departure.shortDay() + (inbound?.let { " → ${it.first.departure.shortDay()}" } ?: ""),
            detail = listOfNotNull(
                if (inbound != null) count(trip.nights ?: 0, "notte", "notti") else "Solo andata",
                trip.usefulMinutes?.takeIf { inbound != null }?.let { "${it.asDuration()} ${if (trip.destination == away) "sul posto" else "tra i voli"}" },
                home?.takeIf { it != trip.origin }?.let { "rientro a ${names.city(it)}" },
            ).joinToString(" · "),
            leading = { IconTile(Icons.Outlined.CalendarMonth, size = 42.dp) },
        )
    }
    state.message?.let { InfoBanner(it.text, it.tone) }
    FlightFinderCard(spacing = 18) {
        SectionHeader("Voli", Icons.Outlined.ConfirmationNumber, meta = if (inbound != null) "2 biglietti separati" else null)
        LegRow(trip.outbound, "Andata", names)
        if (inbound != null) {
            StayDivider(
                trip.usefulMinutes?.let { "${it.asDuration()} ${if (trip.destination == away) "a ${names.city(trip.destination)}" else "tra i voli"}" }
                    ?: count(trip.nights ?: 0, "notte", "notti"),
            )
            LegRow(inbound, "Ritorno", names)
        }
    }
    if (away != null && away != trip.destination) {
        InfoBanner("Arrivi a ${state.catalog?.details(trip.destination)?.name} e riparti da ${state.catalog?.details(away)?.name}: il trasferimento è a carico tuo.")
    }
    trip.roundTripFrom?.let {
        InfoBanner("Con questa andata Google vende un biglietto A/R unico da ${it.asEuro()}, ma con un ritorno che può essere diverso da questo.", BannerTone.INFO)
    }
    if (inbound != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlightFinderButton("Solo andata", { onOpen(trip.outbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.OUTLINE, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
            FlightFinderButton("Solo ritorno", { onOpen(inbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.OUTLINE, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
        }
    }
    HintText(modifier = Modifier.padding(horizontal = 4.dp), text = "Dati di shopping osservati su Google Flights: numero volo, aeroporti, orari e prezzo. Non sono una conferma della compagnia. Condizioni e bagagli vanno verificati dal venditore.")
}
