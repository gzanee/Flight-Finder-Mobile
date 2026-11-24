package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.core.FormError
import it.gzanee.flightfinder.app.core.FormState
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.core.Place
import it.gzanee.flightfinder.app.ui.AppCard
import it.gzanee.flightfinder.app.ui.Chip
import it.gzanee.flightfinder.app.ui.Chips
import it.gzanee.flightfinder.app.ui.Collapsible
import it.gzanee.flightfinder.app.ui.DateField
import it.gzanee.flightfinder.app.ui.DaysPicker
import it.gzanee.flightfinder.app.ui.Field
import it.gzanee.flightfinder.app.ui.Hint
import it.gzanee.flightfinder.app.ui.Label
import it.gzanee.flightfinder.app.ui.Notice
import it.gzanee.flightfinder.app.ui.OptionList
import it.gzanee.flightfinder.app.ui.PrimaryButton
import it.gzanee.flightfinder.app.ui.RowGap
import it.gzanee.flightfinder.app.ui.Segmented
import it.gzanee.flightfinder.app.ui.Stepper
import it.gzanee.flightfinder.app.ui.TimeField
import it.gzanee.flightfinder.app.ui.ToggleRow
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Airports
import it.gzanee.flightfinder.engine.Calendar
import it.gzanee.flightfinder.engine.Plan
import it.gzanee.flightfinder.engine.SearchValidationError
import it.gzanee.flightfinder.engine.buildPlan
import it.gzanee.flightfinder.engine.parseRequest
import it.gzanee.flightfinder.engine.secondsPerSearch
import kotlinx.coroutines.launch

private val SEATS = listOf("economy" to "Economy", "premium-economy" to "Premium economy", "business" to "Business", "first" to "Prima")
private val BAGS = listOf("base" to "Solo bagaglio piccolo", "cabin" to "Con trolley in cabina", "checked" to "Con valigia in stiva")

/** The single search form of the portal, laid out for a phone. */
@Composable
fun SearchScreen(onPlace: (String) -> Unit, onResults: (String) -> Unit, onHistory: () -> Unit, onDiagnostics: () -> Unit) {
    val c = colors
    val f by AppState.form.collectAsState()
    fun set(update: FormState.() -> FormState) { AppState.form.value = AppState.form.value.update() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    val scope = rememberCoroutineScope()
    val today = Calendar.today()

    fun start() {
        error = null
        if (AppState.jobs.running) {
            error = "Una ricerca è già in corso: aprila da «Ricerche» e fermala prima di avviarne un’altra."
            return
        }
        busy = true
        scope.launch {
            try {
                val request = parseRequest(f.toSpec())
                val plan: Plan = buildPlan(request, Airports.candidateRoutes(request))
                val minutes = (plan.searchesUpperBound * secondsPerSearch(request, AppState.live.requestInterval) / 60).toInt()
                val go: suspend () -> Unit = { onResults(AppState.jobs.create(request, plan)) }
                if (minutes >= 20) {
                    val time = if (minutes >= 90) "${minutes / 60},${(minutes % 60) / 6} ore" else "$minutes minuti"
                    confirm = "Fino a ${plan.searchesUpperBound} richieste a Google, circa $time. Tieni l’app aperta: lo schermo resta acceso. I primi voli diretti arrivano subito." to go
                } else {
                    go()
                }
            } catch (e: SearchValidationError) {
                error = e.message
            } catch (e: FormError) {
                error = e.message
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Screen("Flight Finder", onBack = null, actions = { TextButton(onClick = onHistory) { Text("Ricerche", fontWeight = FontWeight.SemiBold) } }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.hero).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trova il volo giusto,\nal prezzo giusto.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
                Text("Tutte le compagnie, da più aeroporti insieme. Hai date flessibili? Troviamo noi i giorni più convenienti.", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 21.sp)
            }

            AppCard {
                Segmented(listOf("round_trip" to "Andata e ritorno", "one_way" to "Solo andata"), f.tripType, { v -> set { copy(tripType = v) } })
                Segmented(listOf("fixed" to "Date esatte", "flexible" to "Date flessibili"), f.dateMode, { v -> set { withDateMode(v) } })
                RowGap {
                    PrimaryButton("Un weekend", Modifier.weight(1f), kind = "secondary") { set { weekend() } }
                    PrimaryButton("Qualsiasi giorno", Modifier.weight(1f), kind = "secondary") { set { anytime() } }
                }
                Label("Da")
                PlaceChips(f.origins, onRemove = { p -> set { copy(origins = origins - p) } }, onAdd = { onPlace("origins") })
                ToggleRow("A: ovunque", f.anywhere) { v -> set { copy(anywhere = v) } }
                if (f.anywhere) {
                    OptionList(listOf("real" to "Tutte le mete raggiungibili") + Airports.sets.map { it.key to it.value.label }, f.destinationSet) { v -> set { copy(destinationSet = v) } }
                } else {
                    PlaceChips(f.destinations, onRemove = { p -> set { copy(destinations = destinations - p) } }, onAdd = { onPlace("destinations") })
                }
                RowGap {
                    DateField(if (f.flexible) "Dal" else "Partenza", f.startDate, today, Modifier.weight(1f)) { v ->
                        set { copy(startDate = v, endDate = if (endDate < v) Calendar.addDays(v, if (flexible) 30 else 2) else endDate) }
                    }
                    if (f.round || f.flexible) DateField(if (f.flexible) "Al" else "Ritorno", f.endDate, f.startDate, Modifier.weight(1f)) { v -> set { copy(endDate = v) } }
                }
                if (f.flexible) {
                    Hint(
                        if (f.round) "Confrontiamo tutte le partenze e i ritorni possibili in questo periodo, nei giorni e con le notti che scegli."
                        else "Confrontiamo tutte le partenze possibili in questo periodo, nei giorni che scegli.",
                    )
                }
                Label("Andata")
                if (f.flexible) DaysPicker(f.departureDays) { v -> set { copy(departureDays = v) } }
                RowGap {
                    TimeField("dalle", f.departureMin, Modifier.weight(1f)) { v -> set { copy(departureMin = v) } }
                    TimeField("alle", f.departureMax, Modifier.weight(1f)) { v -> set { copy(departureMax = v) } }
                }
                if (f.round) {
                    Label("Ritorno")
                    if (f.flexible) DaysPicker(f.returnDays) { v -> set { copy(returnDays = v) } }
                    RowGap {
                        TimeField("dalle", f.returnMin, Modifier.weight(1f)) { v -> set { copy(returnMin = v) } }
                        TimeField("alle", f.returnMax, Modifier.weight(1f)) { v -> set { copy(returnMax = v) } }
                    }
                }
                if (f.round && f.flexible) {
                    Stepper("Notti, minimo", f.minNights, 0, 30) { n -> set { copy(minNights = n, maxNights = maxOf(n, maxNights)) } }
                    Stepper("Notti, massimo", f.maxNights, 0, 30) { n -> set { copy(maxNights = n, minNights = minOf(n, minNights)) } }
                }
            }

            AppCard {
                val people = listOfNotNull(
                    Fmt.count(f.adults, "adulto", "adulti"),
                    f.children.takeIf { it > 0 }?.let { Fmt.count(it, "bambino", "bambini") },
                    f.infants.takeIf { it > 0 }?.let { Fmt.count(it, "neonato", "neonati") },
                ).joinToString(", ")
                Collapsible("Viaggiatori", right = "$people · ${SEATS.first { it.first == f.seat }.second}") {
                    Stepper("Adulti", f.adults, 1, 9, "12 anni e più") { n -> set { copy(adults = n) } }
                    Stepper("Bambini", f.children, 0, 8, "2–11 anni, con posto") { n -> set { copy(children = n) } }
                    Stepper("Neonati", f.infants, 0, 8, "sotto i 2 anni, in braccio") { n -> set { copy(infants = n) } }
                    Label("Classe")
                    OptionList(SEATS, f.seat) { v -> set { copy(seat = v) } }
                    Label("Bagaglio")
                    OptionList(BAGS, f.baggage) { v -> set { copy(baggage = v) } }
                }
                Field(f.budget, { v -> set { copy(budget = v.filter { it.isDigit() || it == ',' || it == '.' }) } }, label = if (f.round) "Budget A/R" else "Budget", placeholder = "Nessun limite", suffix = "€", numeric = true)
                Label("Scali")
                Segmented(listOf(0 to "Diretti", 1 to "1 scalo", 2 to "2 scali", 3 to "Qualsiasi"), f.maxStops, { v -> set { copy(maxStops = v) } })
            }

            AppCard { MoreFilters(f) { u -> set(u) } }

            error?.let { Notice(it, "bad") }
            PrimaryButton("Cerca voli", Modifier.fillMaxWidth(), loading = busy) { start() }
            Hint("Flight Finder confronta i prezzi pubblicati su Google Flights, dalla connessione di questo telefono. Prezzi e disponibilità possono cambiare: verifica sempre le condizioni sul sito del venditore prima di prenotare.")
            TextButton(onClick = onDiagnostics) { Text("Prova la connessione a Google") }
        }
    }

    confirm?.let { (text, go) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Ricerca lunga") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; scope.launch { runCatching { go() }.onFailure { error = it.message } } }) { Text("Avvia") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun PlaceChips(places: List<Place>, onRemove: (Place) -> Unit, onAdd: () -> Unit) = Chips {
    for (p in places) {
        Chip(p.label, detail = if (p is Place.Country) "${p.codes.size} aeroporti" else (p as Place.Airport).code, onRemove = { onRemove(p) })
    }
    Chip("+ Aggiungi", onClick = onAdd)
}

@Composable
private fun MoreFilters(f: FormState, set: (FormState.() -> FormState) -> Unit) = Collapsible("Altri filtri") {
    Label("Arrivi")
    RowGap {
        TimeField("Arrivo andata dalle", f.departureArrivalMin, Modifier.weight(1f)) { v -> set { copy(departureArrivalMin = v) } }
        TimeField("entro le", f.departureArrivalMax, Modifier.weight(1f)) { v -> set { copy(departureArrivalMax = v) } }
    }
    if (f.round) {
        RowGap {
            TimeField("Arrivo ritorno dalle", f.returnArrivalMin, Modifier.weight(1f)) { v -> set { copy(returnArrivalMin = v) } }
            TimeField("entro le", f.returnArrivalMax, Modifier.weight(1f)) { v -> set { copy(returnArrivalMax = v) } }
        }
    }
    TimeField(
        if (f.round) "A casa entro le (giorno del ritorno)" else "Arrivo entro le (giorno della partenza)",
        f.finalArrival, placeholder = "Nessun limite", onClear = { set { copy(finalArrival = "") } },
    ) { v -> set { copy(finalArrival = v) } }
    if (f.round) Stepper("Ore minime a destinazione", f.minHours, 0, 720) { n -> set { copy(minHours = n) } }
    Label("Durata dei voli (ore)")
    RowGap {
        Field(f.departureDurationMin, { v -> set { copy(departureDurationMin = v) } }, Modifier.weight(1f), label = "Andata da", numeric = true)
        Field(f.departureDurationMax, { v -> set { copy(departureDurationMax = v) } }, Modifier.weight(1f), label = "a", placeholder = "Nessun limite", numeric = true)
    }
    if (f.round) {
        RowGap {
            Field(f.returnDurationMin, { v -> set { copy(returnDurationMin = v) } }, Modifier.weight(1f), label = "Ritorno da", numeric = true)
            Field(f.returnDurationMax, { v -> set { copy(returnDurationMax = v) } }, Modifier.weight(1f), label = "a", placeholder = "Nessun limite", numeric = true)
        }
    }
    RowGap {
        Field(f.minLayover, { v -> set { copy(minLayover = v.filter(Char::isDigit)) } }, Modifier.weight(1f), label = "Scalo minimo (min)", numeric = true)
        Field(f.maxLayover, { v -> set { copy(maxLayover = v.filter(Char::isDigit)) } }, Modifier.weight(1f), label = "Scalo massimo (min)", placeholder = "Nessun limite", numeric = true)
    }
    Label("Compagnie e scali")
    Segmented(listOf("all" to "Tutte le compagnie", "low_cost" to "Solo low cost"), f.airlineMode, { v -> set { copy(airlineMode = v) } })
    Field(f.airlines, { v -> set { copy(airlines = v.uppercase()) } }, label = "Solo queste compagnie", placeholder = "Es. FR, U2, V7")
    ToggleRow("Escludi le tariffe Basic economy", f.excludeBasicEconomy) { v -> set { copy(excludeBasicEconomy = v) } }
    ToggleRow("Accetta scali con cambio volo a carico mio", f.selfTransfer) { v -> set { copy(selfTransfer = v) } }
    ToggleRow("Accetta il cambio di aeroporto durante lo scalo", f.airportChange) { v -> set { copy(airportChange = v) } }
    ToggleRow("Scali solo in area Schengen", f.schengenTransfers) { v -> set { copy(schengenTransfers = v) } }
    if (f.round) {
        Label("Rientro a")
        OptionList(listOf("same" to "Stesso aeroporto di partenza", "group" to "Uno qualsiasi tra quelli di partenza"), f.returnAirport) { v -> set { copy(returnAirport = v) } }
        if (!f.anywhere) {
            Label("Ripartenza da")
            OptionList(listOf("same" to "Stesso aeroporto di arrivo", "group" to "Uno qualsiasi tra le destinazioni scelte"), f.returnDeparture) { v -> set { copy(returnDeparture = v) } }
            Hint("Se riparti da un altro aeroporto, il trasferimento è a carico tuo.")
        }
    }
}
