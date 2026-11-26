package it.gzanee.flightfinder.ui.screens.search

import it.gzanee.flightfinder.ui.i18n.destinationSetLabel
import it.gzanee.flightfinder.ui.i18n.text
import it.gzanee.flightfinder.ui.i18n.asString
import it.gzanee.flightfinder.ui.i18n.UiText
import it.gzanee.flightfinder.ui.i18n.plural
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.BuildConfig
import it.gzanee.flightfinder.domain.model.AirlineMode
import it.gzanee.flightfinder.domain.model.AirportChoice
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.REAL_ROUTES
import it.gzanee.flightfinder.domain.model.Seat
import it.gzanee.flightfinder.domain.model.TripType
import it.gzanee.flightfinder.ui.i18n.estimate
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.ChipGroup
import it.gzanee.flightfinder.ui.screens.components.CollapsibleSection
import it.gzanee.flightfinder.ui.screens.components.DateField
import it.gzanee.flightfinder.ui.screens.components.DaysPicker
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.FlightFinderTextField
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.screens.components.GlassBar
import it.gzanee.flightfinder.ui.screens.components.Hairline
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.OptionList
import it.gzanee.flightfinder.ui.screens.components.SectionHeader
import it.gzanee.flightfinder.ui.screens.components.SegmentedControl
import it.gzanee.flightfinder.ui.screens.components.StepperBox
import it.gzanee.flightfinder.ui.screens.components.StepperRow
import it.gzanee.flightfinder.ui.screens.components.TimeField
import it.gzanee.flightfinder.ui.screens.components.ToggleRow
import it.gzanee.flightfinder.ui.screens.components.daysSummary
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import it.gzanee.flightfinder.ui.theme.TABULAR
import java.time.LocalDate

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onAddPlace: (PlaceKind) -> Unit,
    onSearchStarted: (String) -> Unit,
    onHistory: () -> Unit,
    onDiagnostics: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val destinationSets by viewModel.destinationSets.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val longSearch by viewModel.longSearch.collectAsStateWithLifecycle()

    SearchScreenContent(
        form = form,
        destinationSets = destinationSets,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onChange = viewModel::update,
        onAddPlace = onAddPlace,
        onSearch = { viewModel.search(onSearchStarted) },
        onHistory = onHistory,
        onDiagnostics = onDiagnostics,
        onToggleTheme = onToggleTheme,
    )

    longSearch?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLongSearch,
            title = { Text(stringResource(R.string.long_search_title)) },
            text = {
                Text(
                    stringResource(R.string.long_search_text, prompt.search.plan.searchesUpperBound, estimate(prompt.search.estimatedMinutes)),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmLongSearch(onSearchStarted) }) { Text(stringResource(R.string.start)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissLongSearch) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

enum class PlaceKind { ORIGIN, DESTINATION }

/** Il modulo unico del portale, impaginato per il telefono. */
@Composable
fun SearchScreenContent(
    form: SearchFormState,
    destinationSets: List<Pair<String, String>> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: UiText? = null,
    onChange: (SearchFormState.() -> SearchFormState) -> Unit = {},
    onAddPlace: (PlaceKind) -> Unit = {},
    onSearch: () -> Unit = {},
    onHistory: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    FlightFinderScaffold(
        title = "Flight Finder",
        subtitle = form.origins.joinToString(" & ") { it.label }.ifEmpty { "v${BuildConfig.VERSION_NAME}" },
        action = HeaderAction(Icons.Outlined.History, stringResource(R.string.your_searches), onHistory),
        onToggleTheme = onToggleTheme,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.search_headline), style = FlightFinderType.headlineXl, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            RouteSection(form, destinationSets, onChange, onAddPlace)
            PeriodSection(form, onChange)
            DaysSection(form, onChange)
            TravelersSection(form, onChange)
            FlightFinderCard { MoreFilters(form, onChange) }
            errorMessage?.let { InfoBanner(it.asString(), BannerTone.BAD) }
            HintText(
                stringResource(R.string.disclaimer),
                Modifier.padding(horizontal = 4.dp),
            )
            FlightFinderButton(stringResource(R.string.test_connection), onDiagnostics, style = ButtonStyle.OUTLINE, leadingIcon = Icons.Outlined.NetworkCheck)
        }
        SearchDock(form, isLoading, onSearch)
    }
}

/** Dock in fondo, come quello di TREK: il periodo scelto e il pulsante pieno per cercare. */
@Composable
private fun SearchDock(form: SearchFormState, isLoading: Boolean, onSearch: () -> Unit) {
    GlassBar(Modifier.imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth(), dock = true) {
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(start = 22.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                FieldLabel(stringResource(if (form.isFlexible) R.string.period else R.string.dates))
                Text(
                    form.startDate.short() + if (form.isRoundTrip || form.isFlexible) " → ${form.endDate.short()}" else "",
                    style = FlightFinderType.titleSm.copy(fontFeatureSettings = TABULAR), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            FlightFinderButton(stringResource(R.string.search_button), onSearch, isLoading = isLoading, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward)
        }
    }
}

@Composable
private fun RouteSection(
    form: SearchFormState,
    destinationSets: List<Pair<String, String>>,
    onChange: (SearchFormState.() -> SearchFormState) -> Unit,
    onAddPlace: (PlaceKind) -> Unit,
) = FlightFinderCard(spacing = 18) {
    SectionHeader(stringResource(R.string.route), Icons.Outlined.Route)
    SegmentedControl(listOf(TripType.ROUND_TRIP to stringResource(R.string.round_trip), TripType.ONE_WAY to stringResource(R.string.one_way)), form.tripType, { v -> onChange { copy(tripType = v) } })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FieldLabel(stringResource(R.string.from))
        PlaceChips(form.origins, onRemove = { p -> onChange { copy(origins = origins - p) } }, onAdd = { onAddPlace(PlaceKind.ORIGIN) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FieldLabel(stringResource(R.string.to))
        ToggleRow(stringResource(R.string.anywhere), form.anywhere, hint = stringResource(R.string.anywhere_hint)) { v -> onChange { copy(anywhere = v) } }
        if (form.anywhere) {
            OptionList(listOf(REAL_ROUTES to stringResource(R.string.all_reachable)) + destinationSets.map { (key, label) -> key to destinationSetLabel(key, label) }, form.destinationSet) { v -> onChange { copy(destinationSet = v) } }
        } else {
            PlaceChips(form.destinations, onRemove = { p -> onChange { copy(destinations = destinations - p) } }, onAdd = { onAddPlace(PlaceKind.DESTINATION) })
        }
    }
}

@Composable
private fun PeriodSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard(spacing = 18) {
    SectionHeader(stringResource(R.string.period), Icons.Outlined.CalendarMonth)
    SegmentedControl(listOf(DateMode.FLEXIBLE to stringResource(R.string.flexible_dates), DateMode.FIXED to stringResource(R.string.exact_dates)), form.dateMode, { v -> onChange { withDateMode(v) } })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlightFinderChip(stringResource(R.string.a_weekend), icon = Icons.Outlined.Weekend, onClick = { onChange { weekend() } })
        FlightFinderChip(stringResource(R.string.any_day), icon = Icons.Outlined.DateRange, onClick = { onChange { anyDay() } })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateField(stringResource(if (form.isFlexible) R.string.from_day else R.string.departure), form.startDate, LocalDate.now(), Modifier.weight(1f)) { v -> onChange { withStartDate(v) } }
        if (form.isRoundTrip || form.isFlexible) {
            DateField(stringResource(if (form.isFlexible) R.string.to_day else R.string.return_date), form.endDate, form.startDate, Modifier.weight(1f)) { v -> onChange { copy(endDate = v) } }
        }
    }
    if (form.isRoundTrip && form.isFlexible) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepperBox(stringResource(R.string.nights_min), form.minNights, stringResource(R.string.nights_unit), 0..30, Modifier.weight(1f)) { n -> onChange { copy(minNights = n, maxNights = maxOf(n, maxNights)) } }
            StepperBox(stringResource(R.string.nights_max), form.maxNights, stringResource(R.string.nights_unit), 0..30, Modifier.weight(1f)) { n -> onChange { copy(maxNights = n, minNights = minOf(n, minNights)) } }
        }
    }
    if (form.isFlexible) {
        HintText(
            stringResource(if (form.isRoundTrip) R.string.flexible_hint_round else R.string.flexible_hint_one_way),
        )
    }
}

@Composable
private fun DaysSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard(spacing = 18) {
    SectionHeader(stringResource(R.string.outbound), Icons.Filled.FlightTakeoff, meta = if (form.isFlexible) daysSummary(form.departureDays) else null)
    if (form.isFlexible) DaysPicker(form.departureDays) { v -> onChange { copy(departureDays = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeField(stringResource(R.string.leave_from), form.departureMin, Modifier.weight(1f)) { v -> onChange { copy(departureMin = v) } }
        TimeField(stringResource(R.string.until), form.departureMax, Modifier.weight(1f)) { v -> onChange { copy(departureMax = v) } }
    }
    if (form.isRoundTrip) {
        Hairline()
        SectionHeader(stringResource(R.string.inbound), Icons.Filled.FlightLand, meta = if (form.isFlexible) daysSummary(form.returnDays) else null)
        if (form.isFlexible) DaysPicker(form.returnDays) { v -> onChange { copy(returnDays = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField(stringResource(R.string.leave_back_from), form.returnMin, Modifier.weight(1f)) { v -> onChange { copy(returnMin = v) } }
            TimeField(stringResource(R.string.until), form.returnMax, Modifier.weight(1f)) { v -> onChange { copy(returnMax = v) } }
        }
    }
}

@Composable
private fun PlaceChips(places: List<PlaceChoice>, onRemove: (PlaceChoice) -> Unit, onAdd: () -> Unit) = ChipGroup {
    for (place in places) {
        val detail = when (place) {
            is PlaceChoice.Country -> plural(R.plurals.airports, place.codes.size)
            is PlaceChoice.Airport -> place.code
        }
        FlightFinderChip(place.label, detail = detail, onRemove = { onRemove(place) })
    }
    FlightFinderChip(stringResource(R.string.add), icon = Icons.Filled.Add, onClick = onAdd, selected = places.isEmpty())
}

@Composable
private fun TravelersSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard(spacing = 18) {
    val people = listOfNotNull(
        plural(R.plurals.adults, form.adults),
        form.children.takeIf { it > 0 }?.let { plural(R.plurals.children, it) },
        form.infants.takeIf { it > 0 }?.let { plural(R.plurals.infants, it) },
    ).joinToString(", ")
    CollapsibleSection(stringResource(R.string.travelers_bags), summary = "$people · ${form.seat.text()} · ${form.baggage.text()}", icon = Icons.Outlined.Group) {
        StepperRow(stringResource(R.string.adults_label), form.adults, 1..9, stringResource(R.string.adults_hint)) { n -> onChange { copy(adults = n) } }
        StepperRow(stringResource(R.string.children_label), form.children, 0..8, stringResource(R.string.children_hint)) { n -> onChange { copy(children = n) } }
        StepperRow(stringResource(R.string.infants_label), form.infants, 0..8, stringResource(R.string.infants_hint)) { n -> onChange { copy(infants = n) } }
        FieldLabel(stringResource(R.string.cabin_class), Modifier.padding(top = 6.dp))
        OptionList(Seat.entries.map { it to it.text() }, form.seat) { v -> onChange { copy(seat = v) } }
        FieldLabel(stringResource(R.string.bags), Modifier.padding(top = 6.dp))
        OptionList(Baggage.entries.map { it to it.text() }, form.baggage) { v -> onChange { copy(baggage = v) } }
    }
    FlightFinderTextField(
        form.budget, { v -> onChange { copy(budget = v.filter { it.isDigit() || it == ',' || it == '.' }) } },
        label = stringResource(if (form.isRoundTrip) R.string.budget_round else R.string.field_budget), placeholder = stringResource(R.string.no_limit), suffix = "€", numeric = true,
    )
    FieldLabel(stringResource(R.string.stops_label))
    SegmentedControl(listOf(0 to stringResource(R.string.stops_direct), 1 to stringResource(R.string.stops_one), 2 to stringResource(R.string.stops_two), 3 to stringResource(R.string.stops_any)), form.maxStops, { v -> onChange { copy(maxStops = v) } })
}

@Composable
private fun MoreFilters(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = CollapsibleSection(
    stringResource(R.string.more_filters), summary = stringResource(R.string.more_filters_hint), icon = Icons.Outlined.FilterList,
) {
    FieldLabel(stringResource(R.string.arrivals), Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField(stringResource(R.string.outbound_arrival_from), form.departureArrivalMin, Modifier.weight(1f)) { v -> onChange { copy(departureArrivalMin = v) } }
        TimeField(stringResource(R.string.by), form.departureArrivalMax, Modifier.weight(1f)) { v -> onChange { copy(departureArrivalMax = v) } }
    }
    if (form.isRoundTrip) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(stringResource(R.string.return_arrival_from), form.returnArrivalMin, Modifier.weight(1f)) { v -> onChange { copy(returnArrivalMin = v) } }
            TimeField(stringResource(R.string.by), form.returnArrivalMax, Modifier.weight(1f)) { v -> onChange { copy(returnArrivalMax = v) } }
        }
    }
    TimeField(
        stringResource(if (form.isRoundTrip) R.string.home_by_round else R.string.arrive_by_one_way),
        form.finalArrival, placeholder = stringResource(R.string.no_limit), onClear = { onChange { copy(finalArrival = null) } },
    ) { v -> onChange { copy(finalArrival = v) } }
    if (form.isRoundTrip) StepperRow(stringResource(R.string.min_hours_there), form.minHours, 0..720) { n -> onChange { copy(minHours = n) } }
    FieldLabel(stringResource(R.string.flight_duration_hours), Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlightFinderTextField(form.departureDurationMin, { v -> onChange { copy(departureDurationMin = v) } }, Modifier.weight(1f), label = stringResource(R.string.outbound_from), numeric = true)
        FlightFinderTextField(form.departureDurationMax, { v -> onChange { copy(departureDurationMax = v) } }, Modifier.weight(1f), label = stringResource(R.string.to_value), placeholder = stringResource(R.string.no_limit), numeric = true)
    }
    if (form.isRoundTrip) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlightFinderTextField(form.returnDurationMin, { v -> onChange { copy(returnDurationMin = v) } }, Modifier.weight(1f), label = stringResource(R.string.return_from), numeric = true)
            FlightFinderTextField(form.returnDurationMax, { v -> onChange { copy(returnDurationMax = v) } }, Modifier.weight(1f), label = stringResource(R.string.to_value), placeholder = stringResource(R.string.no_limit), numeric = true)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlightFinderTextField(form.minLayover, { v -> onChange { copy(minLayover = v.filter(Char::isDigit)) } }, Modifier.weight(1f), label = stringResource(R.string.min_layover_minutes), numeric = true)
        FlightFinderTextField(form.maxLayover, { v -> onChange { copy(maxLayover = v.filter(Char::isDigit)) } }, Modifier.weight(1f), label = stringResource(R.string.max_layover_minutes), placeholder = stringResource(R.string.no_limit), numeric = true)
    }
    FieldLabel(stringResource(R.string.airlines_and_stops), Modifier.padding(top = 8.dp))
    SegmentedControl(listOf(AirlineMode.ALL to stringResource(R.string.all_airlines), AirlineMode.LOW_COST to stringResource(R.string.low_cost_only)), form.airlineMode, { v -> onChange { copy(airlineMode = v) } })
    FlightFinderTextField(form.airlines, { v -> onChange { copy(airlines = v.uppercase()) } }, label = stringResource(R.string.only_these_airlines), placeholder = stringResource(R.string.airlines_example))
    ToggleRow(stringResource(R.string.exclude_basic), form.excludeBasicEconomy) { v -> onChange { copy(excludeBasicEconomy = v) } }
    ToggleRow(stringResource(R.string.accept_self_transfer), form.selfTransfer) { v -> onChange { copy(selfTransfer = v) } }
    ToggleRow(stringResource(R.string.accept_airport_change), form.airportChange) { v -> onChange { copy(airportChange = v) } }
    ToggleRow(stringResource(R.string.schengen_only), form.schengenTransfers) { v -> onChange { copy(schengenTransfers = v) } }
    if (form.isRoundTrip) {
        FieldLabel(stringResource(R.string.back_to_label), Modifier.padding(top = 8.dp))
        OptionList(listOf(AirportChoice.SAME to stringResource(R.string.same_departure_airport), AirportChoice.GROUP to stringResource(R.string.any_departure_airport)), form.returnAirport) { v -> onChange { copy(returnAirport = v) } }
        if (!form.anywhere) {
            FieldLabel(stringResource(R.string.return_from_label), Modifier.padding(top = 8.dp))
            OptionList(listOf(AirportChoice.SAME to stringResource(R.string.same_arrival_airport), AirportChoice.GROUP to stringResource(R.string.any_destination_airport)), form.returnDeparture) { v -> onChange { copy(returnDeparture = v) } }
            HintText(stringResource(R.string.other_airport_hint))
        }
    }
}

@DevicePreview
@Composable
fun SearchScreenPreview() {
    FlightFinderTheme {
        SearchScreenContent(form = SearchFormState(), destinationSets = listOf("sun" to "Mare e sole"))
    }
}
