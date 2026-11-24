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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import it.gzanee.flightfinder.ui.screens.components.CollapsibleSection
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.components.PriceCalendar
import it.gzanee.flightfinder.ui.screens.components.SectionTitle
import it.gzanee.flightfinder.ui.screens.components.SegmentedControl
import it.gzanee.flightfinder.ui.screens.components.TripCard
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

/** Nomi di città e bandiere dal catalogo aeroporti. */
fun AirportCatalog?.names() = object : PlaceNames {
    override fun city(code: String) = this@names?.city(code) ?: code
    override fun flag(code: String) = this@names?.details(code)?.country?.let(::flagOf).orEmpty()
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
        val to = when {
            r.destinations.isEmpty() -> "Ovunque"
            r.destinations.size > 2 -> "${r.destinations.size} mete"
            else -> r.destinations.joinToString(", ") { names.city(it) }
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
                Column {
                    SectionTitle(
                        when {
                            page.filteredCount > 0 -> "${count(page.filteredCount, "viaggio", "viaggi")} ${if (job.request.isRoundTrip) "andata e ritorno" else "di sola andata"}"
                            job.status.isRunning -> "Cerchiamo…"
                            else -> "Nessun viaggio"
                        },
                    )
                    HintText(
                        (if (job.status == JobStatus.COMPLETE) "" else "I risultati si aggiornano mentre cerchiamo. ") +
                            "Prezzi totali per ${count(job.request.travelers, "viaggiatore", "viaggiatori")}, orari locali.",
                    )
                }
            }
            if (page.recommendations.cheapest != null) item { Recommendations(state, names, onFocus) }
            if (page.calendar.size > 1) item { FlightFinderCard { CollapsibleSection("Prezzi per data") { PriceCalendar(page.calendar) } } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedControl(listOf(ResultsMode.DESTINATIONS to "Destinazioni", ResultsMode.TRIPS to "Tutti i voli"), mode, onMode, Modifier.weight(1f))
                    val active = state.filters.activeCount
                    FlightFinderButton(if (active > 0) "Filtri ($active)" else "Filtri", { showFilters = true }, style = ButtonStyle.SECONDARY)
                }
            }
            if (state.filters.destinations.isNotEmpty()) {
                item { Text("← Tutte le destinazioni", color = colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAllDestinations)) }
            }
            if (mode == ResultsMode.TRIPS) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOrder.entries.filter { job.request.isRoundTrip || it != SortOrder.USEFUL }.forEach { sort ->
                            FlightFinderChip(sort.label, selected = state.sort == sort, onClick = { onSort(sort) })
                        }
                    }
                }
                if (page.focusedId == null && state.focus != null) item { HintText("Questo viaggio non è più tra i risultati.", warn = true) }
                if (page.results.isEmpty()) item { EmptyResults(page.resultCount, job.status.isRunning) { onFilters(it.gzanee.flightfinder.domain.model.ResultFilters()) } }
                items(page.results, key = { it.id }) { trip -> TripCard(trip, names, trip.id == page.focusedId) { onTrip(trip.id) } }
                if (page.hasMore || page.offset > 0) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlightFinderButton("← Precedenti", onPrevious, style = ButtonStyle.SECONDARY, enabled = page.offset > 0)
                            Text("${page.offset + 1}–${page.offset + page.results.size} di ${page.filteredCount}", color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            FlightFinderButton("Successivi →", onNext, style = ButtonStyle.SECONDARY, enabled = page.hasMore)
                        }
                    }
                }
            } else {
                if (page.groups.isEmpty()) item { EmptyResults(page.resultCount, job.status.isRunning) { onFilters(it.gzanee.flightfinder.domain.model.ResultFilters()) } }
                items(page.groups, key = { it.city }) { group -> DestinationCard(group, page.facets.destinations, names, state.catalog, onDestination) }
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
private fun ProgressCard(job: SearchJob, found: Int, names: PlaceNames, busy: Boolean, onCancel: () -> Unit, onResume: () -> Unit) = FlightFinderCard {
    val r = job.request
    val running = job.status.isRunning
    Text(
        when (job.status) {
            JobStatus.RUNNING -> "Stiamo cercando i voli migliori…"
            JobStatus.CANCELLING -> "Ci fermiamo…"
            JobStatus.CANCELLED -> "Ricerca fermata"
            JobStatus.BLOCKED -> "Ricerca in pausa"
            JobStatus.INTERRUPTED -> "Ricerca interrotta"
            JobStatus.COMPLETE -> "Ricerca completata"
        },
        color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
    )
    HintText(
        "${r.origins.joinToString(" + ") { names.city(it) }} → ${if (r.destinations.isEmpty()) "ovunque" else r.destinations.joinToString(", ") { names.city(it) }} · " +
            r.startDate.short() + (if (r.startDate == r.endDate) "" else " – ${r.endDate.short()}") + " · ${count(r.travelers, "viaggiatore", "viaggiatori")}",
    )
    LinearProgressIndicator(
        progress = { if (job.total > 0) (job.completed.toFloat() / job.total).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = if (job.status == JobStatus.BLOCKED) colors.warn else colors.brand,
        trackColor = colors.surfaceVariant,
    )
    Text(count(found, "viaggio trovato", "viaggi trovati") + if (running) " finora · ancora ${remaining(job.etaSeconds)}" else "", color = colors.textPrimary, fontSize = 14.sp)
    val current = job.current
        ?.replace(Regex("\\b[A-Z0-9]{3}\\b")) { names.city(it.value) }
        ?.replace(Regex("\\d{4}-\\d{2}-\\d{2}")) { java.time.LocalDate.parse(it.value).short() }
    if (running && current != null) HintText("Ora: $current")
    if (running) HintText("Tieni l’app aperta: lo schermo resta acceso. Se esci, la ricerca si ferma e potrai riprenderla.", warn = true)
    if (job.issues.isNotEmpty()) InfoBanner(job.issues.joinToString(" "))
    if (running) {
        FlightFinderButton("Ferma", onCancel, Modifier.fillMaxWidth(), ButtonStyle.DANGER, enabled = job.status != JobStatus.CANCELLING)
    } else if (job.status.canResume) {
        FlightFinderButton("Riprendi", onResume, Modifier.fillMaxWidth(), isLoading = busy)
    }
}

@Composable
private fun Recommendations(state: ResultsUiState, names: PlaceNames, onFocus: (String) -> Unit) {
    val r = state.view!!.page.recommendations
    val round = state.view.job.request.isRoundTrip
    val cards = buildList {
        add(Triple("Il più economico", r.cheapest, "Il prezzo più basso trovato."))
        if (round) {
            add(Triple("Più tempo a destinazione", r.longest, if (r.longest?.id == r.cheapest?.id) "Ed è anche il più economico." else if (r.longest != null) "Il soggiorno più lungo." else "Nessun confronto possibile."))
            add(Triple("Il miglior compromesso", r.balanced, r.balanced?.let { "${(it.extraPrice ?: 0.0).asEuro()} in più, ${(it.extraMinutes ?: 0).asDuration()} in più sul posto." } ?: "Il più economico è già la scelta migliore."))
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((title, trip, why) in cards) {
            Column(
                Modifier.width(220.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(18.dp))
                    .clickable(enabled = trip != null) { trip?.let { onFocus(it.id) } }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title.uppercase(), color = colors.brand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                if (trip != null) {
                    Text("${names.flag(trip.destination)} ${names.city(trip.destination)}", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(trip.price.asEuro(), color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${dates(trip)} · da ${names.city(trip.origin)}", color = colors.textSecondary, fontSize = 12.sp, maxLines = 1)
                    trip.usefulMinutes?.let { Text("${it.asDuration()} ${if (trip.destinationAirportChange) "tra i voli" else "a destinazione"}", color = colors.textSecondary, fontSize = 12.sp) }
                }
                Text(why, color = colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DestinationCard(group: DestinationGroup, allDestinations: List<String>, names: PlaceNames, catalog: AirportCatalog?, onOpen: (Set<String>) -> Unit) {
    // Il filtro prende tutti gli aeroporti della città fra i risultati, non solo quelli rimasti dopo i filtri.
    val cityAirports = allDestinations.filter { names.city(it) == group.city }.toSet().ifEmpty { group.destinations.toSet() }
    FlightFinderCard(onClick = { onOpen(cityAirports) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${names.flag(group.destinations.first())} ${group.city}", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (group.destinations.size == 1) catalog?.details(group.destinations.first())?.name.orEmpty() else "${group.destinations.size} aeroporti · ${group.destinations.joinToString(", ")}",
                    color = colors.textSecondary, fontSize = 12.sp, maxLines = 1,
                )
                Text("${dates(group.featured)} · da ${names.city(group.featured.origin)}", color = colors.textSecondary, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("da", color = colors.textSecondary, fontSize = 11.sp)
                Text(group.cheapest.price.asEuro(), color = colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("${count(group.count, "opzione", "opzioni")} →", color = colors.brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
