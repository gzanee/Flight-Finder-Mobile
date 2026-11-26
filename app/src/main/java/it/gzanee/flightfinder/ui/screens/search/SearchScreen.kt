package it.gzanee.flightfinder.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.i18n.names
import it.gzanee.flightfinder.ui.screens.components.FormSheet
import it.gzanee.flightfinder.ui.screens.components.IconTile
import it.gzanee.flightfinder.ui.screens.components.InnerShape
import it.gzanee.flightfinder.ui.screens.components.ListRow
import it.gzanee.flightfinder.ui.screens.components.Overline
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.components.dateSpan
import it.gzanee.flightfinder.ui.screens.components.shortName
import it.gzanee.flightfinder.ui.screens.history.SavedSearch
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import java.time.DayOfWeek
import it.gzanee.flightfinder.ui.screens.components.dateSpan
import it.gzanee.flightfinder.ui.i18n.destinationSetLabel
import it.gzanee.flightfinder.ui.i18n.text
import it.gzanee.flightfinder.ui.i18n.asString
import it.gzanee.flightfinder.ui.i18n.UiText
import it.gzanee.flightfinder.ui.i18n.plural
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NetworkCheck
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
import it.gzanee.flightfinder.domain.model.Seat
import it.gzanee.flightfinder.domain.model.TripType
import it.gzanee.flightfinder.ui.i18n.estimate
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.ChipGroup
import it.gzanee.flightfinder.ui.screens.components.DateField
import it.gzanee.flightfinder.ui.screens.components.DaysPicker
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.FlightFinderTextField
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
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
    onOpenSearch: (String) -> Unit,
    onHistory: () -> Unit,
    onDiagnostics: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val destinationSets by viewModel.destinationSets.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val longSearch by viewModel.longSearch.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshRecent() }

    SearchScreenContent(
        form = form,
        destinationSets = destinationSets,
        recent = recent,
        names = catalog.names(),
        isLoading = isLoading,
        errorMessage = errorMessage,
        onChange = viewModel::update,
        onAddPlace = onAddPlace,
        onSearch = { viewModel.search(onSearchStarted) },
        onOpenSearch = onOpenSearch,
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
            containerColor = colors.surface,
        )
    }
}

enum class PlaceKind { ORIGIN, DESTINATION }

/** I pannelli che si aprono dal riepilogo. */
private enum class FormPanel { DATES, TRAVELERS, FILTERS }

/**
 * La ricerca in un riepilogo compatto: tipo di viaggio, poi Da · A · Date · Viaggiatori e i
 * filtri avanzati come righe da toccare, tutto nella prima schermata. Ogni riga apre i suoi
 * controlli (gli aeroporti in una schermata, il resto in un pannello dal basso).
 */
@Composable
fun SearchScreenContent(
    form: SearchFormState,
    destinationSets: List<Pair<String, String>> = emptyList(),
    recent: List<SavedSearch> = emptyList(),
    names: PlaceNames = null.names(),
    isLoading: Boolean = false,
    errorMessage: UiText? = null,
    onChange: (SearchFormState.() -> SearchFormState) -> Unit = {},
    onAddPlace: (PlaceKind) -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenSearch: (String) -> Unit = {},
    onHistory: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    var panel by rememberSaveable { mutableStateOf<FormPanel?>(null) }
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
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.search_headline), style = FlightFinderType.headline, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
            SegmentedControl(
                listOf(TripType.ROUND_TRIP to stringResource(R.string.round_trip), TripType.ONE_WAY to stringResource(R.string.one_way)),
                form.tripType, { v -> onChange { copy(tripType = v) } },
            )
            Summary(form, destinationSets, onAddPlace) { panel = it }
            errorMessage?.let { InfoBanner(it.asString(), BannerTone.BAD) }
            if (recent.isNotEmpty()) RecentSearches(recent, names, onOpenSearch, onHistory)
            HintText(stringResource(R.string.disclaimer), Modifier.padding(horizontal = 4.dp))
            FlightFinderButton(stringResource(R.string.test_connection), onDiagnostics, style = ButtonStyle.OUTLINE, leadingIcon = Icons.Outlined.NetworkCheck)
        }
        // Sempre a portata di pollice, anche su un telefono basso.
        FlightFinderButton(
            stringResource(R.string.search_flights), onSearch,
            Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth().heightIn(min = 56.dp),
            isLoading = isLoading, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
    }
    when (panel) {
        FormPanel.DATES -> FormSheet(stringResource(R.string.dates_title), onDismiss = { panel = null }) { DatesPanel(form, onChange) }
        FormPanel.TRAVELERS -> FormSheet(stringResource(R.string.travelers_bags), onDismiss = { panel = null }) { TravelersPanel(form, onChange) }
        FormPanel.FILTERS -> FormSheet(stringResource(R.string.more_filters), onDismiss = { panel = null }) { FiltersPanel(form, onChange) }
        null -> Unit
    }
}

@Composable
private fun Summary(form: SearchFormState, destinationSets: List<Pair<String, String>>, onAddPlace: (PlaceKind) -> Unit, onPanel: (FormPanel) -> Unit) {
    val people = listOfNotNull(
        plural(R.plurals.adults, form.adults),
        form.children.takeIf { it > 0 }?.let { plural(R.plurals.children, it) },
        form.infants.takeIf { it > 0 }?.let { plural(R.plurals.infants, it) },
    ).joinToString(", ")
    val destination = when {
        form.anywhere -> stringResource(R.string.anywhere)
        form.destinations.isEmpty() -> null
        else -> form.destinations.joinToString(" · ") { it.label }
    }
    val setLabel = destinationSets.firstOrNull { it.first == form.destinationSet }?.let { (key, label) -> destinationSetLabel(key, label) }
    val dates = if (form.isRoundTrip || form.isFlexible) dateSpan(form.startDate, form.endDate) else form.startDate.short()
    val datesDetail = when {
        !form.isFlexible -> stringResource(R.string.exact_dates)
        form.isRoundTrip -> "${dayList(form.departureDays)} → ${dayList(form.returnDays)} · " +
            if (form.minNights == form.maxNights) plural(R.plurals.nights, form.minNights) else stringResource(R.string.nights_range, form.minNights, form.maxNights)
        else -> dayList(form.departureDays)
    }
    val advanced = form.advancedCount()
    FlightFinderCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0) {
        SummaryRow(Icons.Filled.FlightTakeoff, stringResource(R.string.from), form.origins.joinToString(" · ") { it.label }.ifEmpty { null }, form.origins.flatMap { it.codes }.joinToString(" · ").ifEmpty { null }) { onAddPlace(PlaceKind.ORIGIN) }
        Hairline()
        SummaryRow(
            Icons.Filled.FlightLand, stringResource(R.string.to), destination,
            if (form.anywhere) setLabel ?: stringResource(R.string.all_reachable) else form.destinationCodes().take(6).joinToString(" · ").ifEmpty { null },
        ) { onAddPlace(PlaceKind.DESTINATION) }
        Hairline()
        SummaryRow(Icons.Outlined.CalendarMonth, stringResource(R.string.dates), dates, datesDetail) { onPanel(FormPanel.DATES) }
        Hairline()
        SummaryRow(Icons.Outlined.Group, stringResource(R.string.travelers_title), people, "${form.seat.text()} · ${form.baggage.text()}") { onPanel(FormPanel.TRAVELERS) }
        Hairline()
        SummaryRow(
            Icons.Outlined.Tune, stringResource(R.string.more_filters),
            if (advanced == 0) stringResource(R.string.filters_none) else plural(R.plurals.active_filters, advanced),
            stringResource(R.string.more_filters_hint),
        ) { onPanel(FormPanel.FILTERS) }
    }
}

/** "ven" o "ven/sab" per i giorni ammessi. */
@Composable
private fun dayList(days: List<DayOfWeek>): String = when (days.size) {
    0 -> stringResource(R.string.days_none)
    7 -> stringResource(R.string.days_all).lowercase()
    else -> days.sorted().joinToString("/") { it.shortName().lowercase() }
}

/** Una riga del riepilogo: icona, etichetta maiuscola, valore grande e dettaglio, freccia. */
@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String?, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(InnerShape).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            FieldLabel(label)
            Text(
                value ?: stringResource(R.string.choose), style = FlightFinderType.titleSm,
                color = if (value == null) colors.textTertiary else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) Text(detail, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RecentSearches(recent: List<SavedSearch>, names: PlaceNames, onOpen: (String) -> Unit, onAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline(stringResource(R.string.recent_searches), Modifier.padding(horizontal = 4.dp), icon = Icons.Outlined.History, action = stringResource(R.string.see_all), onAction = onAll)
        FlightFinderCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0) {
            recent.forEachIndexed { i, saved ->
                if (i > 0) Hairline()
                val r = saved.job.request
                ListRow(
                    title = "${r.origins.map { names.city(it) }.distinct().joinToString(" + ")} → " +
                        if (r.destinations.isEmpty()) stringResource(R.string.anywhere) else r.destinations.map(names::city).distinct().let { c -> c.take(2).joinToString(", ") + if (c.size > 2) "…" else "" },
                    detail = (if (r.startDate == r.endDate) r.startDate.short() else dateSpan(r.startDate, r.endDate)) + " · " + saved.status.text(),
                    leading = { IconTile(if (r.isRoundTrip) Icons.Outlined.SyncAlt else Icons.Filled.FlightTakeoff, filled = saved.status.isRunning) },
                    onClick = { onOpen(saved.job.id) },
                ) {
                    saved.cheapest?.let { Text(it.asEuro(), style = FlightFinderType.titleSm.copy(fontFeatureSettings = TABULAR), color = colors.textPrimary) }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Date, giorni, notti e orari. */
@Composable
internal fun ColumnScope.DatesPanel(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) {
    SegmentedControl(listOf(DateMode.FLEXIBLE to stringResource(R.string.flexible_dates), DateMode.FIXED to stringResource(R.string.exact_dates)), form.dateMode, { v -> onChange { withDateMode(v) } })
    ChipGroup {
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
            StepperBox(stringResource(R.string.nights_min), form.minNights, 0..30, Modifier.weight(1f)) { n -> onChange { copy(minNights = n, maxNights = maxOf(n, maxNights)) } }
            StepperBox(stringResource(R.string.nights_max), form.maxNights, 0..30, Modifier.weight(1f)) { n -> onChange { copy(maxNights = n, minNights = minOf(n, minNights)) } }
        }
    }
    if (form.isFlexible) {
        HintText(
            stringResource(if (form.isRoundTrip) R.string.flexible_hint_round else R.string.flexible_hint_one_way),
        )
    }
    Hairline(Modifier.padding(vertical = 4.dp))
    SectionHeader(stringResource(R.string.outbound), Icons.Filled.FlightTakeoff, meta = if (form.isFlexible) daysSummary(form.departureDays) else null)
    if (form.isFlexible) DaysPicker(form.departureDays) { v -> onChange { copy(departureDays = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeField(stringResource(R.string.leave_from), form.departureMin, Modifier.weight(1f)) { v -> onChange { copy(departureMin = v) } }
        TimeField(stringResource(R.string.until), form.departureMax, Modifier.weight(1f)) { v -> onChange { copy(departureMax = v) } }
    }
    if (form.isRoundTrip) {
        Hairline(Modifier.padding(vertical = 4.dp))
        SectionHeader(stringResource(R.string.inbound), Icons.Filled.FlightLand, meta = if (form.isFlexible) daysSummary(form.returnDays) else null)
        if (form.isFlexible) DaysPicker(form.returnDays) { v -> onChange { copy(returnDays = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField(stringResource(R.string.leave_back_from), form.returnMin, Modifier.weight(1f)) { v -> onChange { copy(returnMin = v) } }
            TimeField(stringResource(R.string.until), form.returnMax, Modifier.weight(1f)) { v -> onChange { copy(returnMax = v) } }
        }
    }
}

/** Viaggiatori, classe, bagaglio, budget e scali. */
@Composable
internal fun ColumnScope.TravelersPanel(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) {
        StepperRow(stringResource(R.string.adults_label), form.adults, 1..9, stringResource(R.string.adults_hint)) { n -> onChange { copy(adults = n) } }
        StepperRow(stringResource(R.string.children_label), form.children, 0..8, stringResource(R.string.children_hint)) { n -> onChange { copy(children = n) } }
        StepperRow(stringResource(R.string.infants_label), form.infants, 0..8, stringResource(R.string.infants_hint)) { n -> onChange { copy(infants = n) } }
        FieldLabel(stringResource(R.string.cabin_class), Modifier.padding(top = 6.dp))
        OptionList(Seat.entries.map { it to it.text() }, form.seat) { v -> onChange { copy(seat = v) } }
        FieldLabel(stringResource(R.string.bags), Modifier.padding(top = 6.dp))
        OptionList(Baggage.entries.map { it to it.text() }, form.baggage) { v -> onChange { copy(baggage = v) } }
    FlightFinderTextField(
        form.budget, { v -> onChange { copy(budget = v.filter { it.isDigit() || it == ',' || it == '.' }) } },
        label = stringResource(if (form.isRoundTrip) R.string.budget_round else R.string.field_budget), placeholder = stringResource(R.string.no_limit), suffix = "€", numeric = true,
    )
    FieldLabel(stringResource(R.string.stops_label))
    SegmentedControl(listOf(0 to stringResource(R.string.stops_direct), 1 to stringResource(R.string.stops_one), 2 to stringResource(R.string.stops_two), 3 to stringResource(R.string.stops_any)), form.maxStops, { v -> onChange { copy(maxStops = v) } })
}

/** Arrivi, durate, scali, compagnie e aeroporti del ritorno. */
@Composable
internal fun ColumnScope.FiltersPanel(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) {
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
