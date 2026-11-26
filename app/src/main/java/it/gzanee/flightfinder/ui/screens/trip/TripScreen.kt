package it.gzanee.flightfinder.ui.screens.trip

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
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
import it.gzanee.flightfinder.ui.screens.components.CodeBadge
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
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (trip == null) {
                if (state.isLoaded) {
                    InfoBanner(state.message?.text ?: "Questo viaggio non è più tra i risultati.", state.message?.tone ?: BannerTone.WARN)
                    FlightFinderButton("Torna ai risultati", onBack, style = ButtonStyle.SECONDARY, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack)
                }
                return@Column
            }
            TripDetails(trip, names, state, onOpen, onRecheck)
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
private fun TripDetails(trip: Trip, names: PlaceNames, state: TripUiState, onOpen: (String?) -> Unit, onRecheck: () -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    val bookingLink = trip.bookingLink()
    val country = names.country(trip.destination)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Place, contentDescription = null, tint = colors.brand, modifier = Modifier.size(14.dp))
            Text(
                (names.city(trip.destination) + if (country.isNotEmpty()) " ($country)" else "").uppercase(),
                style = FlightFinderType.overline.copy(fontWeight = FontWeight.Bold), color = colors.brand,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${if (inbound != null) "A/R" else "Solo andata"} da ${names.city(trip.origin)}",
                style = FlightFinderType.headline, color = colors.textPrimary, modifier = Modifier.weight(1f),
            )
            CodeBadge(listOfNotNull(trip.origin, home?.takeIf { it != trip.origin }).joinToString(" • "))
        }
    }
    FlightFinderCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Outlined.CalendarMonth)
            Column {
                Text(
                    trip.outbound.first.departure.shortDay() + (inbound?.let { " — ${it.first.departure.shortDay()}" } ?: ""),
                    style = FlightFinderType.titleSm, color = colors.textPrimary,
                )
                Text(
                    listOfNotNull(
                        if (inbound != null) count(trip.nights ?: 0, "notte", "notti") else "Solo andata",
                        trip.usefulMinutes?.takeIf { inbound != null }?.let { "${it.asDuration()} ${if (trip.destination == away) "sul posto" else "tra i voli"}" },
                        home?.takeIf { it != trip.origin }?.let { "rientro a ${names.city(it)}" },
                    ).joinToString(" · "),
                    style = FlightFinderType.label, color = colors.textSecondary,
                )
            }
        }
    }
    state.message?.let { InfoBanner(it.text, it.tone) }
    FlightFinderCard(highlighted = true, contentPadding = PaddingValues(0.dp), spacing = 0) {
        Row(Modifier.fillMaxWidth().background(colors.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (inbound != null) "TARIFFA COMBINATA" else "TARIFFA", style = FlightFinderType.overline, color = colors.textSecondary)
                Text(if (inbound != null) "Andata e ritorno" else "Solo andata", style = FlightFinderType.titleSm, color = colors.textPrimary)
                Text(
                    if (trip.needsRecheck == true) "Prezzo da aggiornare" else "Prezzo delle ${trip.checkedAt.atZone(ZoneId.systemDefault()).format(CLOCK)}",
                    style = FlightFinderType.label, color = if (trip.needsRecheck == true) colors.warn else colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(trip.price.asEuro(), style = FlightFinderType.priceXl, color = colors.brand)
                Text(
                    if (trip.outbound.travelers > 1) "totale per ${trip.outbound.travelers} persone" else "totale per passeggero",
                    style = FlightFinderType.label, color = colors.textSecondary,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LegRow(trip.outbound, "Andata", names)
            if (inbound != null) {
                StayDivider(
                    trip.usefulMinutes?.let { "${it.asDuration()} ${if (trip.destination == away) "a ${names.city(trip.destination)}" else "tra i voli"}" }
                        ?: count(trip.nights ?: 0, "notte", "notti"),
                )
                LegRow(inbound, "Ritorno", names)
            }
            FlightFinderButton(
                "Apri su Google Flights", { onOpen(bookingLink) }, Modifier.fillMaxWidth(),
                enabled = safeGoogleLink(bookingLink) != null, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(
                    if (inbound != null) "Due biglietti separati, si prenotano dal venditore" else "Si prenota dal venditore",
                    style = FlightFinderType.label, color = colors.textSecondary,
                )
            }
        }
    }
    if (away != null && away != trip.destination) {
        InfoBanner("Arrivi a ${state.catalog?.details(trip.destination)?.name} e riparti da ${state.catalog?.details(away)?.name}: il trasferimento è a carico tuo.")
    }
    trip.roundTripFrom?.let {
        InfoBanner("Con questa andata Google vende un biglietto A/R unico da ${it.asEuro()}, ma con un ritorno che può essere diverso da questo.", BannerTone.INFO)
    }
    if (inbound != null) {
        Overline("Prenota le tratte una per una")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlightFinderButton("Solo andata", { onOpen(trip.outbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.OUTLINE, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
            FlightFinderButton("Solo ritorno", { onOpen(inbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.OUTLINE, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
        }
    }
    FlightFinderButton("Aggiorna prezzo", onRecheck, Modifier.fillMaxWidth(), ButtonStyle.SECONDARY, isLoading = state.isRechecking, leadingIcon = Icons.Outlined.Refresh)
    HintText("Dati di shopping osservati su Google Flights: numero volo, aeroporti, orari e prezzo. Non sono una conferma della compagnia. Condizioni e bagagli vanno verificati dal venditore.")
}
