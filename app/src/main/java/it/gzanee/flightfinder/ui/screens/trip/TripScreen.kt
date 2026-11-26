package it.gzanee.flightfinder.ui.screens.trip

import it.gzanee.flightfinder.ui.i18n.asString
import it.gzanee.flightfinder.ui.i18n.plural
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
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
import it.gzanee.flightfinder.ui.i18n.names
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
        title = stringResource(R.string.trip_title),
        onBack = onBack,
        action = trip?.let { val text = shareText(it, names); HeaderAction(Icons.Outlined.Share, stringResource(R.string.share)) { onShare(text) } },
        onToggleTheme = onToggleTheme,
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (trip == null) {
                if (state.isLoaded) {
                    InfoBanner(state.message?.text?.asString() ?: stringResource(R.string.trip_gone), state.message?.tone ?: BannerTone.WARN)
                    FlightFinderButton(stringResource(R.string.back_to_results), onBack, style = ButtonStyle.SECONDARY, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack)
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
                    RoundIconButton(Icons.Outlined.Refresh, stringResource(R.string.refresh_price), onRecheck, loading = state.isRechecking)
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        FieldLabel(stringResource(R.string.total))
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

@Composable
private fun shareText(trip: Trip, names: PlaceNames) = listOfNotNull(
    "${names.city(trip.origin)} → ${names.city(trip.destination)} · ${trip.price.asEuro()}${if (trip.inbound != null) stringResource(R.string.share_round_trip) else ""}",
    stringResource(R.string.share_outbound, "${trip.outbound.first.departure.shortDay()} ${trip.outbound.first.departure.clock()}"),
    trip.inbound?.let { stringResource(R.string.share_return, "${it.first.departure.shortDay()} ${it.first.departure.clock()}") },
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
            stringResource(if (inbound != null) R.string.trip_round_from else R.string.trip_one_way_from, names.city(trip.origin)),
            style = FlightFinderType.headline, color = colors.textPrimary,
        )
    }
    // Il prezzo su una card scura, come il totale delle spese in TREK.
    FlightFinderCard(inverted = true, spacing = 4) {
        Text(
            stringResource(if (inbound != null) R.string.combined_fare else R.string.fare).uppercase(), style = FlightFinderType.overline,
            color = colors.onBrandFill.copy(alpha = 0.55f),
        )
        Text(trip.price.asEuro(), style = FlightFinderType.priceXl, color = colors.onBrandFill)
        Text(
            listOf(
                if (trip.outbound.travelers > 1) stringResource(R.string.total_for_people, plural(R.plurals.people, trip.outbound.travelers)) else stringResource(R.string.total_per_passenger),
                if (trip.needsRecheck == true) stringResource(R.string.price_to_refresh_lower) else stringResource(R.string.price_at, trip.checkedAt.atZone(ZoneId.systemDefault()).format(CLOCK)),
            ).joinToString(" · "),
            style = FlightFinderType.label, color = colors.onBrandFill.copy(alpha = if (trip.needsRecheck == true) 1f else 0.7f),
        )
    }
    FlightFinderCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), spacing = 0) {
        ListRow(
            title = trip.outbound.first.departure.shortDay() + (inbound?.let { " → ${it.first.departure.shortDay()}" } ?: ""),
            detail = listOfNotNull(
                if (inbound != null) plural(R.plurals.nights, trip.nights ?: 0) else stringResource(R.string.one_way),
                trip.usefulMinutes?.takeIf { inbound != null }?.let { stringResource(if (trip.destination == away) R.string.time_there else R.string.time_between, it.asDuration()) },
                home?.takeIf { it != trip.origin }?.let { stringResource(R.string.back_to, names.city(it)) },
            ).joinToString(" · "),
            leading = { IconTile(Icons.Outlined.CalendarMonth, size = 42.dp) },
        )
    }
    state.message?.let { InfoBanner(it.text.asString(), it.tone) }
    FlightFinderCard(spacing = 18) {
        SectionHeader(stringResource(R.string.flights), Icons.Outlined.ConfirmationNumber, meta = if (inbound != null) stringResource(R.string.two_tickets) else null)
        LegRow(trip.outbound, inbound = false, names = names)
        if (inbound != null) {
            StayDivider(
                trip.usefulMinutes?.let { if (trip.destination == away) stringResource(R.string.time_in_city, it.asDuration(), names.city(trip.destination)) else stringResource(R.string.time_between, it.asDuration()) }
                    ?: plural(R.plurals.nights, trip.nights ?: 0),
            )
            LegRow(inbound, inbound = true, names = names)
        }
    }
    if (away != null && away != trip.destination) {
        InfoBanner(stringResource(R.string.airport_change_warning, names.airport(trip.destination), names.airport(away)))
    }
    trip.roundTripFrom?.let {
        InfoBanner(stringResource(R.string.round_trip_ticket_note, it.asEuro()), BannerTone.INFO)
    }
    if (inbound != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlightFinderButton(stringResource(R.string.outbound_only), { onOpen(trip.outbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.OUTLINE, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
            FlightFinderButton(stringResource(R.string.return_only), { onOpen(inbound.bookingUrl) }, Modifier.weight(1f), ButtonStyle.OUTLINE, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
        }
    }
    HintText(modifier = Modifier.padding(horizontal = 4.dp), text = stringResource(R.string.data_note))
}
