package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.core.FormState
import it.gzanee.flightfinder.app.ui.AppCard
import it.gzanee.flightfinder.app.ui.Chip
import it.gzanee.flightfinder.app.ui.Chips
import it.gzanee.flightfinder.app.ui.Collapsible
import it.gzanee.flightfinder.app.ui.Field
import it.gzanee.flightfinder.app.ui.Heading
import it.gzanee.flightfinder.app.ui.Hint
import it.gzanee.flightfinder.app.ui.Label
import it.gzanee.flightfinder.app.ui.Notice
import it.gzanee.flightfinder.app.ui.OptionList
import it.gzanee.flightfinder.app.ui.PrimaryButton
import it.gzanee.flightfinder.app.ui.Segmented
import it.gzanee.flightfinder.app.ui.TimeField
import it.gzanee.flightfinder.app.ui.Title
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Calendar
import it.gzanee.flightfinder.engine.CalendarPrice
import it.gzanee.flightfinder.engine.JobView
import it.gzanee.flightfinder.engine.ResultFilters
import it.gzanee.flightfinder.engine.TripSummary
import kotlinx.coroutines.launch

private val STATES = mapOf(
    "running" to "Stiamo cercando i voli migliori…", "cancelling" to "Ci fermiamo…", "cancelled" to "Ricerca fermata",
    "blocked" to "Ricerca in pausa", "interrupted" to "Ricerca interrotta", "complete" to "Ricerca completata",
)
private val SORTS = listOf("price" to "Prezzo più basso", "useful" to "Più tempo a destinazione", "duration" to "Voli più brevi", "departure" to "Partenza più vicina")

/** One option per city; the value lists its airports (e.g. "LGW,LTN,STN"). */
private fun cityOptions(codes: List<String>): List<Pair<String, String>> =
    codes.groupBy(Fmt::city).map { (city, list) -> list.sorted().joinToString(",") to "$city (${list.joinToString(", ")})" }.sortedBy { it.second }

/** A search: live progress while it runs, then the saved results to explore. */
@Composable
fun ResultsScreen(id: String, onBack: () -> Unit, onTrip: (String, String) -> Unit, onEdit: () -> Unit) {
    val c = colors
    var sort by remember { mutableStateOf("price") }
    var offset by remember { mutableStateOf(0) }
    var filters by remember { mutableStateOf(mapOf<String, String>()) }
    var focus by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<JobView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val tick by AppState.tick.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, sort, offset, filters, focus, tick, refresh) {
        try {
            view = AppState.jobs.view(id, sort, offset, ResultFilters.parse(filters), focus)
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }
    fun changeFilters(next: Map<String, String>) {
        filters = next.filterValues { it.isNotEmpty() }
        offset = 0
        focus = ""
    }

    val v = view
    val title = v?.job?.request?.let { r ->
        val to = if (r.destinations.isEmpty()) "Ovunque" else if (r.destinations.size > 2) "${r.destinations.size} mete" else r.destinations.joinToString(", ", transform = Fmt::city)
        "${r.origins.joinToString(" + ", transform = Fmt::city)} → $to"
    } ?: "Ricerca"
    Screen(title, onBack, actions = {
        if (v != null && !v.job.running) {
            TextButton(onClick = {
                AppState.form.value = FormState.from(v.job.request, Fmt::city)
                onEdit()
            }) { Text("Modifica", fontWeight = FontWeight.SemiBold) }
        }
    }) { padding ->
        if (v == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { error?.let { Notice(it, "bad") } ?: Hint("Carico la ricerca…") }
            return@Screen
        }
        val job = v.job
        val b = v.browse
        val running = job.running
        val round = job.request.roundTrip
        val viewMode = mode ?: if (job.plan.candidateCount > 1) "destinations" else "trips"
        val list = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (job.status != "complete" || job.issues.isNotEmpty()) item { Progress(v) { refresh++ } }
            item {
                Column {
                    Heading(
                        when {
                            b.filteredCount > 0 -> "${Fmt.count(b.filteredCount, "viaggio", "viaggi")} ${if (round) "andata e ritorno" else "di sola andata"}"
                            running -> "Cerchiamo…"
                            else -> "Nessun viaggio"
                        },
                    )
                    Hint(
                        (if (job.status == "complete") "" else "I risultati si aggiornano mentre cerchiamo. ") +
                            "Prezzi totali per ${Fmt.count(job.request.travelers, "viaggiatore", "viaggiatori")}, orari locali.",
                    )
                }
            }
            if (b.recommendations.cheapest != null) item { Recommendations(v, round) { tripId -> focus = tripId; mode = "trips" } }
            if (b.calendar.size > 1) item { AppCard { Collapsible("Prezzi per data") { PriceCalendar(b.calendar) } } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Segmented(listOf("destinations" to "Destinazioni", "trips" to "Tutti i voli"), viewMode, { mode = it }, Modifier.weight(1f))
                    val active = runCatching { ResultFilters.parse(filters).active }.getOrDefault(0)
                    PrimaryButton(if (active > 0) "Filtri ($active)" else "Filtri", kind = "secondary") { showFilters = true }
                }
            }
            if (filters["destination"] != null) {
                item { Text("← Tutte le destinazioni", color = c.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { changeFilters(filters - "destination"); mode = "destinations" }) }
            }
            if (viewMode == "trips") {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SORTS.filter { round || it.first != "useful" }.forEach { (k, label) -> Chip(label, selected = sort == k, onClick = { sort = k; offset = 0 }) }
                    }
                }
                if (b.focusedId == null && focus.isNotEmpty()) item { Hint("Questo viaggio non è più tra i risultati.", "warn") }
                if (b.results.isEmpty()) item { Empty(v) { changeFilters(emptyMap()) } }
                items(b.results, key = { it.id }) { trip -> TripCard(trip, trip.id == b.focusedId) { onTrip(id, trip.id) } }
                if (b.hasMore || offset > 0) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PrimaryButton("← Precedenti", enabled = offset > 0, kind = "secondary") { offset = maxOf(0, offset - 50) }
                            Text("${b.offset + 1}–${b.offset + b.results.size} di ${b.filteredCount}", color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            PrimaryButton("Successivi →", enabled = b.hasMore, kind = "secondary") { offset += 50 }
                        }
                    }
                }
            } else {
                if (b.groups.isEmpty()) item { Empty(v) { changeFilters(emptyMap()) } }
                val cityValues = cityOptions(b.facets.destinations).associate { (value, _) -> Fmt.city(value.substringBefore(',')) to value }
                items(b.groups, key = { it.city }) { g ->
                    AppCard(onClick = { changeFilters(filters + ("destination" to (cityValues[g.city] ?: g.destination))); mode = "trips" }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${Fmt.flag(g.destinations[0])} ${g.city}", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                Text(if (g.destinations.size == 1) v.airports[g.destinations[0]]?.name ?: "" else "${g.destinations.size} aeroporti · ${g.destinations.joinToString(", ")}", color = c.muted, fontSize = 12.sp, maxLines = 1)
                                Text(dates(g.featured) + " · da ${Fmt.city(g.featured.origin)}", color = c.muted, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("da", color = c.muted, fontSize = 11.sp)
                                Text(Fmt.money(g.cheapest.price), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                                Text("${Fmt.count(g.count, "opzione", "opzioni")} →", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(b.focusedId) {
            val focused = b.focusedId ?: return@LaunchedEffect
            val index = b.results.indexOfFirst { it.id == focused }
            if (index >= 0) scope.launch { list.animateScrollToItem(list.layoutInfo.totalItemsCount - b.results.size - (if (b.hasMore || offset > 0) 1 else 0) + index) }
        }
        if (showFilters) FiltersSheet(v, filters, round, onClose = { showFilters = false }) { changeFilters(it) }
    }
}

private fun dates(s: TripSummary) = Fmt.shortDay(s.date) + (s.returnDate?.let { " – ${Fmt.shortDay(it)}" } ?: "")

@Composable
private fun Empty(v: JobView, onReset: () -> Unit) {
    val running = v.job.running
    AppCard {
        Heading(if (v.browse.resultCount > 0) "Nessun viaggio con questi filtri" else if (running) "Stiamo cercando…" else "Nessun volo trovato")
        Hint(
            when {
                v.browse.resultCount > 0 -> "Prova ad alzare il prezzo massimo o ad azzerare i filtri."
                running -> "I primi voli compaiono qui appena li troviamo."
                else -> "Prova altre date, aggiungi aeroporti vicini, accetta uno scalo o alza il budget."
            },
        )
        if (v.browse.resultCount > 0) PrimaryButton("Azzera filtri", kind = "secondary", onClick = onReset)
    }
}

@Composable
private fun Progress(v: JobView, onChange: () -> Unit) {
    val c = colors
    val job = v.job
    val r = job.request
    val running = job.running
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = job.current
        ?.replace(Regex("\\b[A-Z0-9]{3}\\b")) { v.airports[it.value]?.city ?: Fmt.city(it.value) }
        ?.replace(Regex("\\d{4}-\\d{2}-\\d{2}")) { Fmt.shortDay(it.value) }
    AppCard {
        Text(STATES[job.status] ?: job.status, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Hint(
            "${r.origins.joinToString(" + ", transform = Fmt::city)} → ${if (r.destinations.isEmpty()) "ovunque" else r.destinations.joinToString(", ", transform = Fmt::city)} · " +
                Fmt.shortDay(r.startDate) + (if (r.startDate == r.endDate) "" else " – ${Fmt.shortDay(r.endDate)}") +
                " · ${Fmt.count(r.travelers, "viaggiatore", "viaggiatori")}",
        )
        LinearProgressIndicator(
            progress = { if (job.total > 0) (job.completed.toFloat() / job.total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (job.status == "blocked") c.warn else c.primary,
            trackColor = c.chip,
        )
        Text(Fmt.count(v.browse.resultCount, "viaggio trovato", "viaggi trovati") + if (running) " finora · ancora ${Fmt.remaining(job.etaSeconds)}" else "", color = c.text, fontSize = 14.sp)
        if (running && current != null) Hint("Ora: $current")
        if (running) Hint("Tieni l’app aperta: lo schermo resta acceso. Se esci, la ricerca si ferma e potrai riprenderla.", "warn")
        if (job.issues.isNotEmpty()) Notice(job.issues.joinToString(" "))
        error?.let { Notice(it, "bad") }
        if (running) {
            PrimaryButton("Ferma", Modifier.fillMaxWidth(), enabled = job.status != "cancelling", kind = "danger") {
                scope.launch { AppState.jobs.cancel(job.id); onChange() }
            }
        } else if (job.status in setOf("cancelled", "blocked", "interrupted")) {
            PrimaryButton("Riprendi", Modifier.fillMaxWidth(), loading = busy) {
                busy = true
                error = null
                scope.launch {
                    try {
                        AppState.jobs.resume(job.id)
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        busy = false
                        onChange()
                    }
                }
            }
        }
    }
}

@Composable
private fun Recommendations(v: JobView, round: Boolean, onOpen: (String) -> Unit) {
    val c = colors
    val r = v.browse.recommendations
    val cards = buildList {
        add(Triple("Il più economico", r.cheapest, "Il prezzo più basso trovato."))
        if (round) {
            add(Triple("Più tempo a destinazione", r.longest, if (r.longest?.id == r.cheapest?.id) "Ed è anche il più economico." else if (r.longest != null) "Il soggiorno più lungo." else "Nessun confronto possibile."))
            add(Triple("Il miglior compromesso", r.balanced, r.balanced?.let { "${Fmt.money(it.extraPrice ?: 0.0)} in più, ${Fmt.duration(it.extraMinutes ?: 0)} in più sul posto." } ?: "Il più economico è già la scelta migliore."))
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((title, trip, why) in cards) {
            Column(
                Modifier.width(220.dp).clip(RoundedCornerShape(18.dp)).background(c.card).border(1.dp, c.border, RoundedCornerShape(18.dp))
                    .clickable(enabled = trip != null) { trip?.let { onOpen(it.id) } }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title.uppercase(), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                if (trip != null) {
                    Text("${Fmt.flag(trip.destination)} ${Fmt.city(trip.destination)}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(Fmt.money(trip.price), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${dates(trip)} · da ${Fmt.city(trip.origin)}", color = c.muted, fontSize = 12.sp, maxLines = 1)
                    trip.usefulMinutes?.let { Text("${Fmt.duration(it)} ${if (trip.destinationAirportChange) "tra i voli" else "a destinazione"}", color = c.muted, fontSize = 12.sp) }
                }
                Text(why, color = c.muted, fontSize = 12.sp)
            }
        }
    }
}

/** Cheapest price per departure day, month by month (green: cheapest third). */
@Composable
private fun PriceCalendar(items: List<CalendarPrice>) {
    val c = colors
    val byDay = items.groupBy { it.date }
    val best = byDay.values.map { list -> list.minOf { it.price } }
    val lo = best.min()
    val hi = best.max()
    fun tier(p: Double) = when {
        hi == lo || p <= lo + (hi - lo) / 3 -> c.goodSoft
        p <= lo + 2 * (hi - lo) / 3 -> c.warnSoft
        else -> c.badSoft
    }
    val keys = byDay.keys.sorted()
    var y = keys.first().take(4).toInt()
    var m = keys.first().substring(5, 7).toInt()
    val lastMonth = keys.last().take(7)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Hint("Prezzo minimo per giorno di partenza. Verde: più economico.")
        while ("${y}-${m.toString().padStart(2, '0')}" <= lastMonth) {
            val year = y
            val month = m
            val first = Calendar.weekday("$year-${month.toString().padStart(2, '0')}-01")
            val days = Calendar.daysBetween("$year-${month.toString().padStart(2, '0')}-01", if (month == 12) "${year + 1}-01-01" else "$year-${(month + 1).toString().padStart(2, '0')}-01")
            val cells = List(first) { null } + (1..days).toList()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${Fmt.MONTHS_LONG[month - 1].replaceFirstChar { it.uppercase() }} $year", color = c.text, fontWeight = FontWeight.Bold)
                Row { listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom").forEach { Text(it, color = c.faint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                cells.chunked(7).forEach { week ->
                    Row {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i)
                            Box(Modifier.weight(1f).padding(1.5.dp)) {
                                if (d != null) {
                                    val key = "$year-${month.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                                    val price = byDay[key]?.minOf { it.price }
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (price != null) tier(price) else c.card)
                                            .then(if (price == lo) Modifier.border(1.5.dp, c.good, RoundedCornerShape(6.dp)) else Modifier).padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("$d", color = if (price != null) c.text else c.faint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        if (price != null) Text(Fmt.money(kotlin.math.round(price)), color = c.text, fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (m == 12) { y++; m = 1 } else m++
        }
    }
}

@Composable
private fun FiltersSheet(v: JobView, filters: Map<String, String>, round: Boolean, onClose: () -> Unit, onApply: (Map<String, String>) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(filters) }
    var error by remember { mutableStateOf<String?>(null) }
    fun set(key: String, value: String) { draft = draft + (key to value) }
    val excluded = (draft["exclude"] ?: "").split(",").filter { it.isNotEmpty() }
    val cities = cityOptions(v.browse.facets.destinations)
    val f = v.browse.facets
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = colors.bg) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Title("Filtra i risultati")
            Hint("Filtri e suggerimenti lavorano su tutti i ${v.browse.resultCount} viaggi salvati, senza nuove richieste a Google.")
            Field(draft["max_price"] ?: "", { set("max_price", it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.')) }, label = "Prezzo massimo", placeholder = "Qualsiasi", suffix = "€", numeric = true)
            if (round) {
                Label("Tempo a destinazione")
                Segmented(listOf("" to "Tutte", "24" to "24 h", "36" to "36 h", "48" to "48 h", "60" to "60 h"), draft["min_hours"] ?: "", { set("min_hours", it) })
                TimeField("Rientro entro le", draft["home_before"] ?: "", placeholder = "Qualsiasi ora", onClear = { set("home_before", "") }) { set("home_before", it) }
            }
            if (f.origins.size > 1) {
                Label("Partenza da")
                OptionList(listOf("" to "Tutti gli aeroporti") + f.origins.map { it to "${Fmt.city(it)} ($it)" }, draft["origin"] ?: "") { set("origin", it) }
            }
            if (cities.size > 1) {
                Label("Destinazione")
                OptionList(listOf("" to "Tutte") + cities, draft["destination"] ?: "") { set("destination", it) }
            }
            if (f.airlines.size > 1) {
                Label("Compagnia (su ogni volo)")
                OptionList(listOf("" to "Tutte") + f.airlines.map { it.code to it.name }, draft["airline"] ?: "") { set("airline", it) }
            }
            if (cities.size > 1) {
                Label("Escludi destinazioni")
                Chips {
                    for ((value, label) in cities) {
                        val codes = value.split(",")
                        val on = codes.all { it in excluded }
                        Chip(label, selected = on, onClick = { set("exclude", (if (on) excluded - codes.toSet() else excluded + codes).joinToString(",")) })
                    }
                }
            }
            error?.let { Notice(it, "bad") }
            PrimaryButton("Mostra risultati", Modifier.fillMaxWidth()) {
                try {
                    ResultFilters.parse(draft) // validated by the engine, like the web page
                    onApply(draft)
                    onClose()
                } catch (e: Exception) {
                    error = e.message
                }
            }
            PrimaryButton("Azzera filtri", Modifier.fillMaxWidth(), kind = "ghost") { onApply(emptyMap()); onClose() }
        }
    }
}
