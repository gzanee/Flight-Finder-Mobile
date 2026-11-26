package it.gzanee.flightfinder.ui.screens.results

import it.gzanee.flightfinder.ui.i18n.remaining
import it.gzanee.flightfinder.ui.i18n.names
import it.gzanee.flightfinder.ui.i18n.engineProgress
import it.gzanee.flightfinder.ui.i18n.engineText
import it.gzanee.flightfinder.ui.i18n.text
import it.gzanee.flightfinder.ui.i18n.plural
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.model.DestinationGroup
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.model.TripSummary
import it.gzanee.flightfinder.ui.format.asDuration
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.CodeBadge
import it.gzanee.flightfinder.ui.screens.components.CollapsibleSection
import it.gzanee.flightfinder.ui.screens.components.FlagTile
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.screens.components.ListRow
import it.gzanee.flightfinder.ui.screens.components.RoundIconButton
import it.gzanee.flightfinder.ui.screens.components.StatusDot
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Sync
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.components.PriceCalendar
import it.gzanee.flightfinder.ui.screens.components.SectionHeader
import it.gzanee.flightfinder.ui.screens.components.SectionTitle
import it.gzanee.flightfinder.ui.screens.components.SegmentedControl
import it.gzanee.flightfinder.ui.screens.components.TripCard
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import it.gzanee.flightfinder.ui.theme.TABULAR

@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onBack: () -> Unit,
    onTrip: (String) -> Unit,
    onEdit: (SearchJob) -> Unit,
    onToggleTheme: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultsScreenContent(
        state = state,
        onBack = onBack,
        onTrip = onTrip,
        onEdit = onEdit,
        onToggleTheme = onToggleTheme,
        onCancel = { viewModel.cancel() },
        onResume = { viewModel.resume() },
        onSort = viewModel::setSort,
        onMode = viewModel::setMode,
        onFilters = viewModel::setFilters,
        onDestination = viewModel::openDestination,
        onAllDestinations = viewModel::showAllDestinations,
        onFocus = viewModel::focusTrip,
        onPrevious = viewModel::previousPage,
        onNext = viewModel::nextPage,
    )
}

/** Una ricerca: avanzamento mentre gira, poi i viaggi salvati da esplorare. */
@Composable
fun ResultsScreenContent(
    state: ResultsUiState,
    onBack: () -> Unit = {},
    onTrip: (String) -> Unit = {},
    onEdit: (SearchJob) -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onCancel: () -> Unit = {},
    onResume: () -> Unit = {},
    onSort: (SortOrder) -> Unit = {},
    onMode: (ResultsMode) -> Unit = {},
    onFilters: (it.gzanee.flightfinder.domain.model.ResultFilters) -> Unit = {},
    onDestination: (Set<String>) -> Unit = {},
    onAllDestinations: () -> Unit = {},
    onFocus: (String) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val view = state.view
    val names = state.catalog.names()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val title = view?.job?.request?.let { r ->
        val cities = r.destinations.map(names::city).distinct()
        val to = when {
            cities.isEmpty() -> stringResource(R.string.anywhere)
            cities.size > 2 -> stringResource(R.string.n_destinations, cities.size)
            else -> cities.joinToString(", ")
        }
        "${r.origins.joinToString(" + ") { names.city(it) }} → $to"
    } ?: stringResource(R.string.search_title)
    FlightFinderScaffold(
        title = title,
        subtitle = view?.job?.status?.text(),
        onBack = onBack,
        action = view?.job?.takeIf { !it.status.isRunning }?.let { job -> HeaderAction(Icons.Outlined.Edit, stringResource(R.string.edit_search)) { onEdit(job) } },
        onToggleTheme = onToggleTheme,
    ) {
        if (view == null) {
            Column(Modifier.padding(16.dp)) { state.errorMessage?.let { InfoBanner(engineText(it), BannerTone.BAD) } ?: HintText(stringResource(R.string.loading_search)) }
            return@FlightFinderScaffold
        }
        val job = view.job
        val page = view.page
        val mode = state.effectiveMode
        val listState = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (job.status != JobStatus.COMPLETE || job.issues.isNotEmpty()) {
                item { ProgressCard(job, page.resultCount, names, state.isBusy, onCancel, onResume) }
            }
            state.errorMessage?.let { item { InfoBanner(it, BannerTone.BAD) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when {
                                page.filteredCount > 0 -> stringResource(if (job.request.isRoundTrip) R.string.round_trip else R.string.one_way)
                                job.status.isRunning -> stringResource(R.string.searching)
                                else -> stringResource(R.string.no_trips)
                            },
                            style = FlightFinderType.headline, color = colors.textPrimary,
                        )
                        if (page.filteredCount > 0) CodeBadge(plural(R.plurals.found_badge, page.filteredCount).uppercase())
                    }
                    HintText(
                        (if (job.status == JobStatus.COMPLETE) "" else stringResource(R.string.results_updating)) +
                            stringResource(
                                if (job.request.isRoundTrip) R.string.results_note_round else R.string.results_note_one_way,
                                plural(R.plurals.travelers, job.request.travelers),
                            ),
                    )
                }
            }
            if (page.recommendations.cheapest != null) {
                item { SectionHeader(stringResource(R.string.suggested), Icons.Outlined.AutoAwesome, meta = stringResource(R.string.tap_to_see), modifier = Modifier.padding(horizontal = 4.dp)) }
                item { Recommendations(state, names, onFocus) }
            }
            if (page.calendar.size > 1) {
                item { FlightFinderCard { CollapsibleSection(stringResource(R.string.prices_by_date), summary = stringResource(R.string.prices_by_date_hint), icon = Icons.Outlined.CalendarMonth) { PriceCalendar(page.calendar) } } }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedControl(listOf(ResultsMode.DESTINATIONS to stringResource(R.string.mode_destinations), ResultsMode.TRIPS to stringResource(R.string.mode_all_flights)), mode, onMode, Modifier.weight(1f))
                    val active = state.filters.activeCount
                    FlightFinderButton(if (active > 0) stringResource(R.string.filters_count, active) else stringResource(R.string.filters), { showFilters = true }, style = ButtonStyle.SECONDARY, leadingIcon = Icons.Outlined.Tune)
                }
            }
            if (state.filters.destinations.isNotEmpty()) {
                item {
                    FlightFinderButton(
                        stringResource(R.string.all_destinations), onAllDestinations, style = ButtonStyle.GHOST, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    )
                }
            }
            if (mode == ResultsMode.TRIPS) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOrder.entries.filter { job.request.isRoundTrip || it != SortOrder.USEFUL }.forEach { sort ->
                            FlightFinderChip(sort.text(), selected = state.sort == sort, onClick = { onSort(sort) })
                        }
                    }
                }
                if (page.focusedId == null && state.focus != null) item { HintText(stringResource(R.string.trip_gone), warn = true) }
                if (page.results.isEmpty()) item { EmptyResults(page.resultCount, job.status.isRunning) { onFilters(it.gzanee.flightfinder.domain.model.ResultFilters()) } }
                val best = page.recommendations.cheapest?.id
                items(page.results, key = { it.id }) { trip -> TripCard(trip, names, trip.id == page.focusedId, best = trip.id == best) { onTrip(trip.id) } }
                if (page.hasMore || page.offset > 0) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlightFinderButton(stringResource(R.string.previous), onPrevious, style = ButtonStyle.OUTLINE, enabled = page.offset > 0, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack)
                            Text(
                                stringResource(R.string.page_range, page.offset + 1, page.offset + page.results.size, page.filteredCount), style = FlightFinderType.label.copy(fontFeatureSettings = TABULAR),
                                color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            )
                            FlightFinderButton(stringResource(R.string.next), onNext, style = ButtonStyle.OUTLINE, enabled = page.hasMore, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward)
                        }
                    }
                }
            } else {
                if (page.groups.isEmpty()) item { EmptyResults(page.resultCount, job.status.isRunning) { onFilters(it.gzanee.flightfinder.domain.model.ResultFilters()) } }
                val bestCity = page.recommendations.cheapest?.destination?.let(names::city)
                items(page.groups, key = { it.city }) { group -> DestinationCard(group, page.facets.destinations, names, group.city == bestCity, onDestination) }
            }
        }
        // Un suggerimento toccato: si scorre fino al viaggio.
        LaunchedEffect(page.focusedId) {
            val focused = page.focusedId ?: return@LaunchedEffect
            val index = page.results.indexOfFirst { it.id == focused }
            if (index >= 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - page.results.size - (if (page.hasMore || page.offset > 0) 1 else 0) + index)
        }
    }
    if (showFilters && view != null) {
        FiltersSheet(view.page, state.filters, view.job.request.isRoundTrip, names, onDismiss = { showFilters = false }, onApply = onFilters)
    }
}

private fun dates(s: TripSummary) = s.date.short() + (s.returnDate?.let { " – ${it.short()}" } ?: "")

@Composable
private fun EmptyResults(resultCount: Int, running: Boolean, onReset: () -> Unit) = FlightFinderCard {
    SectionTitle(stringResource(if (resultCount > 0) R.string.empty_filtered_title else if (running) R.string.empty_running_title else R.string.empty_title))
    HintText(
        when {
            resultCount > 0 -> stringResource(R.string.empty_filtered_hint)
            running -> stringResource(R.string.empty_running_hint)
            else -> stringResource(R.string.empty_hint)
        },
    )
    if (resultCount > 0) FlightFinderButton(stringResource(R.string.reset_filters), onReset, style = ButtonStyle.SECONDARY)
}

@Composable
private fun ProgressCard(job: SearchJob, found: Int, names: PlaceNames, busy: Boolean, onCancel: () -> Unit, onResume: () -> Unit) = FlightFinderCard {
    val r = job.request
    val running = job.status.isRunning
    SectionHeader(stringResource(R.string.progress), Icons.Outlined.Sync, meta = if (job.total > 0) "${(100 * job.completed / job.total).coerceIn(0, 100)}%" else null)
    Text(
        when (job.status) {
            JobStatus.RUNNING -> stringResource(R.string.progress_running)
            JobStatus.CANCELLING -> stringResource(R.string.progress_cancelling)
            JobStatus.CANCELLED -> stringResource(R.string.progress_cancelled)
            JobStatus.BLOCKED -> stringResource(R.string.progress_blocked)
            JobStatus.INTERRUPTED -> stringResource(R.string.progress_interrupted)
            JobStatus.COMPLETE -> stringResource(R.string.progress_complete)
        },
        style = FlightFinderType.title, color = colors.textPrimary,
    )
    HintText(
        "${r.origins.joinToString(" + ") { names.city(it) }} → ${if (r.destinations.isEmpty()) stringResource(R.string.anywhere_lower) else r.destinations.map(names::city).distinct().joinToString(", ")} · " +
            r.startDate.short() + (if (r.startDate == r.endDate) "" else " – ${r.endDate.short()}") + " · " + plural(R.plurals.travelers, r.travelers),
    )
    LinearProgressIndicator(
        progress = { if (job.total > 0) (job.completed.toFloat() / job.total).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = if (job.status == JobStatus.BLOCKED) colors.warn else colors.brandFill,
        trackColor = colors.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
    Text(
        plural(R.plurals.trips_found, found).let { if (running) stringResource(R.string.found_so_far, it, remaining(job.etaSeconds)) else it },
        style = FlightFinderType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary,
    )
    val current = job.current
        ?.replace(Regex("\\b[A-Z0-9]{3}\\b")) { names.city(it.value) }
        ?.replace(Regex("\\d{4}-\\d{2}-\\d{2}")) { java.time.LocalDate.parse(it.value).short() }
    if (running && current != null) HintText(stringResource(R.string.now_searching, engineProgress(current)))
    if (running) HintText(stringResource(R.string.keep_open), warn = true)
    if (job.issues.isNotEmpty()) InfoBanner(job.issues.map { engineText(it) }.joinToString(" "))
    if (running) {
        FlightFinderButton(stringResource(R.string.stop), onCancel, Modifier.fillMaxWidth(), ButtonStyle.DANGER, enabled = job.status != JobStatus.CANCELLING, leadingIcon = Icons.Outlined.StopCircle)
    } else if (job.status.canResume) {
        FlightFinderButton(stringResource(R.string.resume), onResume, Modifier.fillMaxWidth(), isLoading = busy, leadingIcon = Icons.Outlined.PlayArrow)
    }
}

@Composable
private fun Recommendations(state: ResultsUiState, names: PlaceNames, onFocus: (String) -> Unit) {
    val r = state.view!!.page.recommendations
    val round = state.view.job.request.isRoundTrip
    data class Pick(val title: String, val trip: TripSummary?, val why: String, val best: Boolean)
    val cards = buildList {
        add(Pick(stringResource(R.string.pick_cheapest), r.cheapest, stringResource(R.string.pick_cheapest_why), true))
        if (round) {
            add(Pick(stringResource(R.string.pick_longest), r.longest, stringResource(if (r.longest?.id == r.cheapest?.id) R.string.pick_longest_also_cheapest else if (r.longest != null) R.string.pick_longest_why else R.string.pick_no_comparison), false))
            add(Pick(stringResource(R.string.pick_balanced), r.balanced, r.balanced?.let { stringResource(R.string.pick_balanced_why, (it.extraPrice ?: 0.0).asEuro(), (it.extraMinutes ?: 0).asDuration()) } ?: stringResource(R.string.pick_balanced_none), false))
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (pick in cards) {
            val trip = pick.trip
            FlightFinderCard(Modifier.width(260.dp), onClick = trip?.let { { onFocus(it.id) } }, spacing = 8) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pick.best) StatusDot(colors.accent)
                    FieldLabel(pick.title)
                }
                if (trip != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${names.city(trip.destination)} ${names.flag(trip.destination)}".trim(), style = FlightFinderType.title,
                                color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(trip.price.asEuro(), style = FlightFinderType.price, color = colors.textPrimary)
                        }
                        RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.see_trip), { onFocus(trip.id) }, filled = true, size = 36.dp)
                    }
                    Text(
                        "${dates(trip)} · ${stringResource(R.string.from_city, names.city(trip.origin))}" + (trip.usefulMinutes?.let { " · " + stringResource(if (trip.destinationAirportChange) R.string.time_between else R.string.time_there, it.asDuration()) } ?: ""),
                        style = FlightFinderType.label, color = colors.textTertiary, maxLines = 2,
                    )
                } else {
                    Text(pick.why, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(group: DestinationGroup, allDestinations: List<String>, names: PlaceNames, best: Boolean, onOpen: (Set<String>) -> Unit) {
    // Il filtro prende tutti gli aeroporti della città fra i risultati, non solo quelli rimasti dopo i filtri.
    val code = group.destinations.first()
    val cityAirports = allDestinations.filter { names.city(it) == names.city(code) }.toSet().ifEmpty { group.destinations.toSet() }
    FlightFinderCard(onClick = { onOpen(cityAirports) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        ListRow(
            title = names.city(code),
            detail = listOf(names.country(code), group.destinations.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · "),
            subdetail = "${dates(group.featured)} · ${stringResource(R.string.from_city, names.city(group.featured.origin))}",
            leading = { FlagTile(names.flag(code)) },
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (best) StatusDot(colors.accent)
                    Text(group.cheapest.price.asEuro(), style = FlightFinderType.price.copy(fontSize = FlightFinderType.title.fontSize), color = colors.textPrimary)
                }
                Text(plural(R.plurals.options, group.count), style = FlightFinderType.label, color = colors.textTertiary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
