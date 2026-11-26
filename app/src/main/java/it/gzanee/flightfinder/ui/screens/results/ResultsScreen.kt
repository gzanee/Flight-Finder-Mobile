package it.gzanee.flightfinder.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sell
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import it.gzanee.flightfinder.domain.search.AirportCatalog
import it.gzanee.flightfinder.ui.format.asDuration
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.flagOf
import it.gzanee.flightfinder.ui.format.remaining
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.CardShape
import it.gzanee.flightfinder.ui.screens.components.CodeBadge
import it.gzanee.flightfinder.ui.screens.components.CollapsibleSection
import it.gzanee.flightfinder.ui.screens.components.FlagTile
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.Overline
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.components.PriceCalendar
import it.gzanee.flightfinder.ui.screens.components.SectionHeader
import it.gzanee.flightfinder.ui.screens.components.SectionTitle
import it.gzanee.flightfinder.ui.screens.components.SegmentedControl
import it.gzanee.flightfinder.ui.screens.components.TripCard
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import it.gzanee.flightfinder.ui.theme.TABULAR

/** Nomi di città e bandiere dal catalogo aeroporti. */
fun AirportCatalog?.names() = object : PlaceNames {
    override fun city(code: String) = this@names?.city(code) ?: code
    override fun flag(code: String) = this@names?.details(code)?.country?.let(::flagOf).orEmpty()
    override fun country(code: String) = this@names?.let { c -> c.countryNames[c.details(code).country] }.orEmpty()
    override fun airport(code: String) = this@names?.details(code)?.name.orEmpty()
}

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
            cities.isEmpty() -> "Ovunque"
            cities.size > 2 -> "${cities.size} mete"
            else -> cities.joinToString(", ")
        }
        "${r.origins.joinToString(" + ") { names.city(it) }} → $to"
    } ?: "Ricerca"
    FlightFinderScaffold(
        title = title,
        subtitle = view?.job?.status?.label,
        onBack = onBack,
        action = view?.job?.takeIf { !it.status.isRunning }?.let { job -> HeaderAction(Icons.Outlined.Edit, "Modifica ricerca") { onEdit(job) } },
        onToggleTheme = onToggleTheme,
    ) {
        if (view == null) {
            Column(Modifier.padding(16.dp)) { state.errorMessage?.let { InfoBanner(it, BannerTone.BAD) } ?: HintText("Carico la ricerca…") }
            return@FlightFinderScaffold
        }
        val job = view.job
        val page = view.page
        val mode = state.effectiveMode
        val listState = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                page.filteredCount > 0 -> if (job.request.isRoundTrip) "Andata e ritorno" else "Solo andata"
                                job.status.isRunning -> "Cerchiamo…"
                                else -> "Nessun viaggio"
                            },
                            style = FlightFinderType.headline, color = colors.textPrimary,
                        )
                        if (page.filteredCount > 0) CodeBadge(count(page.filteredCount, "trovato", "trovati").uppercase())
                    }
                    HintText(
                        (if (job.status == JobStatus.COMPLETE) "" else "I risultati si aggiornano mentre cerchiamo. ") +
                            "Prezzi totali per ${count(job.request.travelers, "viaggiatore", "viaggiatori")}, orari locali.",
                    )
                }
            }
            if (page.recommendations.cheapest != null) item { Recommendations(state, names, onFocus) }
            if (page.calendar.size > 1) {
                item { FlightFinderCard { CollapsibleSection("Prezzi per data", summary = "Il giorno più economico per partire", icon = Icons.Outlined.CalendarMonth) { PriceCalendar(page.calendar) } } }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedControl(
                        listOf(ResultsMode.DESTINATIONS to "Mete", ResultsMode.TRIPS to "Tutti i voli"), mode, onMode, Modifier.weight(1f),
                        icons = listOf(Icons.Outlined.Explore, Icons.AutoMirrored.Outlined.ViewList),
                    )
                    val active = state.filters.activeCount
                    FlightFinderButton(if (active > 0) "Filtri · $active" else "Filtri", { showFilters = true }, style = ButtonStyle.SECONDARY, leadingIcon = Icons.Outlined.Tune)
                }
            }
            if (state.filters.destinations.isNotEmpty()) {
                item {
                    FlightFinderButton(
                        "Tutte le mete", onAllDestinations, style = ButtonStyle.GHOST, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    )
                }
            }
            if (mode == ResultsMode.TRIPS) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Overline("Ordina per")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOrder.entries.filter { job.request.isRoundTrip || it != SortOrder.USEFUL }.forEach { sort ->
                                FlightFinderChip(sort.label, selected = state.sort == sort, onClick = { onSort(sort) })
                            }
                        }
                    }
                }
                if (page.focusedId == null && state.focus != null) item { HintText("Questo viaggio non è più tra i risultati.", warn = true) }
                if (page.results.isEmpty()) item { EmptyResults(page.resultCount, job.status.isRunning) { onFilters(it.gzanee.flightfinder.domain.model.ResultFilters()) } }
                val best = page.recommendations.cheapest?.id
                items(page.results, key = { it.id }) { trip -> TripCard(trip, names, trip.id == page.focusedId, best = trip.id == best) { onTrip(trip.id) } }
                if (page.hasMore || page.offset > 0) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlightFinderButton("Precedenti", onPrevious, style = ButtonStyle.OUTLINE, enabled = page.offset > 0, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack)
                            Text(
                                "${page.offset + 1}–${page.offset + page.results.size} di ${page.filteredCount}", style = FlightFinderType.label.copy(fontFeatureSettings = TABULAR),
                                color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            )
                            FlightFinderButton("Successivi", onNext, style = ButtonStyle.OUTLINE, enabled = page.hasMore, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward)
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
    SectionTitle(if (resultCount > 0) "Nessun viaggio con questi filtri" else if (running) "Stiamo cercando…" else "Nessun volo trovato")
    HintText(
        when {
            resultCount > 0 -> "Prova ad alzare il prezzo massimo o ad azzerare i filtri."
            running -> "I primi voli compaiono qui appena li troviamo."
            else -> "Prova altre date, aggiungi aeroporti vicini, accetta uno scalo o alza il budget."
        },
    )
    if (resultCount > 0) FlightFinderButton("Azzera filtri", onReset, style = ButtonStyle.SECONDARY)
}

@Composable
private fun ProgressCard(job: SearchJob, found: Int, names: PlaceNames, busy: Boolean, onCancel: () -> Unit, onResume: () -> Unit) = FlightFinderCard(highlighted = job.status.isRunning) {
    val r = job.request
    val running = job.status.isRunning
    Overline(if (job.total > 0) "Avanzamento · ${(100 * job.completed / job.total).coerceIn(0, 100)}%" else "Avanzamento")
    Text(
        when (job.status) {
            JobStatus.RUNNING -> "Stiamo cercando i voli migliori…"
            JobStatus.CANCELLING -> "Ci fermiamo…"
            JobStatus.CANCELLED -> "Ricerca fermata"
            JobStatus.BLOCKED -> "Ricerca in pausa"
            JobStatus.INTERRUPTED -> "Ricerca interrotta"
            JobStatus.COMPLETE -> "Ricerca completata"
        },
        style = FlightFinderType.title, color = colors.textPrimary,
    )
    HintText(
        "${r.origins.joinToString(" + ") { names.city(it) }} → ${if (r.destinations.isEmpty()) "ovunque" else r.destinations.map(names::city).distinct().joinToString(", ")} · " +
            r.startDate.short() + (if (r.startDate == r.endDate) "" else " – ${r.endDate.short()}") + " · ${count(r.travelers, "viaggiatore", "viaggiatori")}",
    )
    LinearProgressIndicator(
        progress = { if (job.total > 0) (job.completed.toFloat() / job.total).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (job.status == JobStatus.BLOCKED) colors.warn else colors.brandFill,
        trackColor = colors.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
    Text(
        count(found, "viaggio trovato", "viaggi trovati") + if (running) " finora · ancora ${remaining(job.etaSeconds)}" else "",
        style = FlightFinderType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary,
    )
    val current = job.current
        ?.replace(Regex("\\b[A-Z0-9]{3}\\b")) { names.city(it.value) }
        ?.replace(Regex("\\d{4}-\\d{2}-\\d{2}")) { java.time.LocalDate.parse(it.value).short() }
    if (running && current != null) HintText("Ora: $current")
    if (running) HintText("Tieni l’app aperta: lo schermo resta acceso. Se esci, la ricerca si ferma e potrai riprenderla.", warn = true)
    if (job.issues.isNotEmpty()) InfoBanner(job.issues.joinToString(" "))
    if (running) {
        FlightFinderButton("Ferma", onCancel, Modifier.fillMaxWidth(), ButtonStyle.DANGER, enabled = job.status != JobStatus.CANCELLING, leadingIcon = Icons.Outlined.StopCircle)
    } else if (job.status.canResume) {
        FlightFinderButton("Riprendi", onResume, Modifier.fillMaxWidth(), isLoading = busy, leadingIcon = Icons.Outlined.PlayArrow)
    }
}

@Composable
private fun Recommendations(state: ResultsUiState, names: PlaceNames, onFocus: (String) -> Unit) {
    val r = state.view!!.page.recommendations
    val round = state.view.job.request.isRoundTrip
    data class Pick(val title: String, val icon: ImageVector, val trip: TripSummary?, val why: String, val accent: Boolean)
    val cards = buildList {
        add(Pick("Il più economico", Icons.Outlined.Sell, r.cheapest, "Il prezzo più basso trovato.", true))
        if (round) {
            add(Pick("Più tempo sul posto", Icons.Outlined.HourglassBottom, r.longest, if (r.longest?.id == r.cheapest?.id) "Ed è anche il più economico." else if (r.longest != null) "Il soggiorno più lungo." else "Nessun confronto possibile.", false))
            add(Pick("Il miglior compromesso", Icons.Outlined.Balance, r.balanced, r.balanced?.let { "${(it.extraPrice ?: 0.0).asEuro()} in più, ${(it.extraMinutes ?: 0).asDuration()} in più sul posto." } ?: "Il più economico è già la scelta migliore.", false))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Suggeriti", Icons.Outlined.AutoAwesome, meta = "Tocca per vederlo")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pick in cards) {
                val trip = pick.trip
                Column(
                    Modifier.width(232.dp).clip(CardShape).background(colors.surface).border(1.dp, colors.border, CardShape)
                        .clickable(enabled = trip != null) { trip?.let { onFocus(it.id) } },
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(if (pick.accent) colors.accentSoft else colors.surfaceVariant).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(pick.icon, contentDescription = null, tint = if (pick.accent) colors.accent else colors.brand, modifier = Modifier.size(15.dp))
                        Text(pick.title.uppercase(), style = FlightFinderType.overline, color = if (pick.accent) colors.accent else colors.brand, maxLines = 1)
                    }
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (trip != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${names.flag(trip.destination)} ${names.city(trip.destination)}".trim(), style = FlightFinderType.titleSm,
                                    color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                Text(trip.price.asEuro(), style = FlightFinderType.price.copy(fontSize = FlightFinderType.title.fontSize), color = if (pick.accent) colors.accent else colors.textPrimary)
                            }
                            Text("${dates(trip)} · da ${names.city(trip.origin)}", style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            trip.usefulMinutes?.let {
                                Text("${it.asDuration()} ${if (trip.destinationAirportChange) "tra i voli" else "sul posto"}", style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                            }
                        }
                        Text(pick.why, style = FlightFinderType.label, color = colors.textSecondary, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(group: DestinationGroup, allDestinations: List<String>, names: PlaceNames, best: Boolean, onOpen: (Set<String>) -> Unit) {
    // Il filtro prende tutti gli aeroporti della città fra i risultati, non solo quelli rimasti dopo i filtri.
    val cityAirports = allDestinations.filter { names.city(it) == group.city }.toSet().ifEmpty { group.destinations.toSet() }
    val code = group.destinations.first()
    FlightFinderCard(onClick = { onOpen(cityAirports) }, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlagTile(names.flag(code))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.city, style = FlightFinderType.titleSm, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    group.destinations.take(3).forEach { CodeBadge(it) }
                }
                Text(
                    listOf(names.country(code), "da ${names.city(group.featured.origin)}").filter { it.isNotEmpty() }.joinToString(" · "),
                    style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(dates(group.featured), style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("da", style = FlightFinderType.label, color = colors.textSecondary)
                Text(group.cheapest.price.asEuro(), style = FlightFinderType.price, color = if (best) colors.accent else colors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(count(group.count, "opzione", "opzioni"), style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = colors.brand)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.brand, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
