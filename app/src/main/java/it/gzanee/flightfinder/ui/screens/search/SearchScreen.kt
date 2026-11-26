package it.gzanee.flightfinder.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Tune
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
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.estimate
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
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.OptionList
import it.gzanee.flightfinder.ui.screens.components.Overline
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
            title = { Text("Ricerca lunga") },
            text = {
                Text(
                    "Fino a ${prompt.search.plan.searchesUpperBound} richieste a Google, circa ${estimate(prompt.search.estimatedMinutes)}. " +
                        "Tieni l’app aperta: lo schermo resta acceso. I primi voli diretti arrivano subito.",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmLongSearch(onSearchStarted) }) { Text("Avvia") } },
            dismissButton = { TextButton(onClick = viewModel::dismissLongSearch) { Text("Annulla") } },
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
    errorMessage: String? = null,
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
        action = HeaderAction(Icons.Outlined.History, "Le tue ricerche", onHistory),
        onToggleTheme = onToggleTheme,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Intro()
            RouteSection(form, destinationSets, onChange, onAddPlace)
            PeriodSection(form, onChange)
            DaysSection(form, onChange)
            TravelersSection(form, onChange)
            FlightFinderCard { MoreFilters(form, onChange) }
            errorMessage?.let { InfoBanner(it, BannerTone.BAD) }
            HintText(
                "Flight Finder confronta i prezzi pubblicati su Google Flights, dalla connessione di questo telefono. " +
                    "Prezzi e disponibilità possono cambiare: verifica sempre le condizioni sul sito del venditore prima di prenotare.",
            )
            FlightFinderButton("Prova la connessione a Google", onDiagnostics, style = ButtonStyle.GHOST, leadingIcon = Icons.Outlined.NetworkCheck)
        }
        SearchBar(form, isLoading, onSearch)
    }
}

@Composable
private fun Intro() = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Dove si vola?", style = FlightFinderType.headlineXl, color = colors.textPrimary)
    Text(
        "Tutte le compagnie, da più aeroporti insieme. Con le date flessibili troviamo noi i giorni più convenienti.",
        style = FlightFinderType.body, color = colors.textSecondary,
    )
}

/** Barra fissa in fondo: riepilogo del periodo e pulsante di ricerca. */
@Composable
private fun SearchBar(form: SearchFormState, isLoading: Boolean, onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(colors.surface).imePadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(if (form.isFlexible) "PERIODO" else "DATE", style = FlightFinderType.overline, color = colors.textSecondary)
                Text(
                    form.startDate.short() + if (form.isRoundTrip || form.isFlexible) " – ${form.endDate.short()}" else "",
                    style = FlightFinderType.titleSm.copy(fontFeatureSettings = TABULAR), color = colors.brand, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (form.isRoundTrip && form.isFlexible) {
                    Text(
                        if (form.minNights == form.maxNights) count(form.minNights, "notte", "notti") else "${form.minNights}–${form.maxNights} notti",
                        style = FlightFinderType.label, color = colors.textSecondary,
                    )
                }
            }
            FlightFinderButton("Cerca voli", onSearch, isLoading = isLoading, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward)
        }
    }
}

@Composable
private fun RouteSection(
    form: SearchFormState,
    destinationSets: List<Pair<String, String>>,
    onChange: (SearchFormState.() -> SearchFormState) -> Unit,
    onAddPlace: (PlaceKind) -> Unit,
) = FlightFinderCard(spacing = 14) {
    SectionHeader("Itinerario", Icons.Outlined.Route)
    SegmentedControl(
        listOf(TripType.ROUND_TRIP to "Andata e ritorno", TripType.ONE_WAY to "Solo andata"), form.tripType, { v -> onChange { copy(tripType = v) } },
        icons = listOf(Icons.Outlined.SyncAlt, Icons.AutoMirrored.Outlined.ArrowRightAlt),
    )
    Overline(if (form.origins.isEmpty()) "Da" else "Da (${form.origins.size})")
    PlaceChips(form.origins, onRemove = { p -> onChange { copy(origins = origins - p) } }, onAdd = { onAddPlace(PlaceKind.ORIGIN) })
    Overline("A")
    ToggleRow("Ovunque", form.anywhere, hint = "Tutte le mete che si raggiungono dai tuoi aeroporti") { v -> onChange { copy(anywhere = v) } }
    if (form.anywhere) {
        OptionList(listOf(REAL_ROUTES to "Tutte le mete raggiungibili") + destinationSets, form.destinationSet) { v -> onChange { copy(destinationSet = v) } }
    } else {
        PlaceChips(form.destinations, onRemove = { p -> onChange { copy(destinations = destinations - p) } }, onAdd = { onAddPlace(PlaceKind.DESTINATION) })
    }
}

@Composable
private fun PeriodSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard(spacing = 14) {
    SectionHeader("Periodo", Icons.Outlined.CalendarMonth, meta = if (form.isFlexible) "Date flessibili" else "Date esatte")
    SegmentedControl(
        listOf(DateMode.FLEXIBLE to "Date flessibili", DateMode.FIXED to "Date esatte"), form.dateMode, { v -> onChange { withDateMode(v) } },
        icons = listOf(Icons.Outlined.Tune, Icons.Outlined.Event),
    )
    Overline("Scorciatoie")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlightFinderChip("Un weekend", icon = Icons.Outlined.Weekend, onClick = { onChange { weekend() } })
        FlightFinderChip("Qualsiasi giorno", icon = Icons.Outlined.DateRange, onClick = { onChange { anyDay() } })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateField(if (form.isFlexible) "Dal giorno" else "Partenza", form.startDate, LocalDate.now(), Modifier.weight(1f)) { v -> onChange { withStartDate(v) } }
        if (form.isRoundTrip || form.isFlexible) {
            DateField(if (form.isFlexible) "Al giorno" else "Ritorno", form.endDate, form.startDate, Modifier.weight(1f)) { v -> onChange { copy(endDate = v) } }
        }
    }
    if (form.isFlexible) {
        HintText(
            if (form.isRoundTrip) "Confrontiamo tutte le partenze e i ritorni possibili in questo periodo, nei giorni e con le notti che scegli."
            else "Confrontiamo tutte le partenze possibili in questo periodo, nei giorni che scegli.",
        )
    }
    if (form.isRoundTrip && form.isFlexible) {
        SectionHeader(
            "Notti sul posto", Icons.Outlined.Hotel,
            meta = if (form.minNights == form.maxNights) count(form.minNights, "notte", "notti") else "${form.minNights}–${form.maxNights} notti",
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepperBox("Minimo", form.minNights, "notti", 0..30, Modifier.weight(1f)) { n -> onChange { copy(minNights = n, maxNights = maxOf(n, maxNights)) } }
            StepperBox("Massimo", form.maxNights, "notti", 0..30, Modifier.weight(1f)) { n -> onChange { copy(maxNights = n, minNights = minOf(n, minNights)) } }
        }
    }
}

@Composable
private fun DaysSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard(spacing = 14) {
    SectionHeader(
        if (form.isFlexible) "Giorni e orari di andata" else "Orari di andata", Icons.Filled.FlightTakeoff,
        meta = if (form.isFlexible) daysSummary(form.departureDays) else null,
    )
    if (form.isFlexible) DaysPicker(form.departureDays) { v -> onChange { copy(departureDays = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeField("Partenza dalle", form.departureMin, Modifier.weight(1f)) { v -> onChange { copy(departureMin = v) } }
        TimeField("alle", form.departureMax, Modifier.weight(1f)) { v -> onChange { copy(departureMax = v) } }
    }
    if (form.isRoundTrip) {
        SectionHeader(
            if (form.isFlexible) "Giorni e orari di ritorno" else "Orari di ritorno", Icons.Filled.FlightLand,
            meta = if (form.isFlexible) daysSummary(form.returnDays) else null, modifier = Modifier.padding(top = 6.dp),
        )
        if (form.isFlexible) DaysPicker(form.returnDays) { v -> onChange { copy(returnDays = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField("Partenza dalle", form.returnMin, Modifier.weight(1f)) { v -> onChange { copy(returnMin = v) } }
            TimeField("alle", form.returnMax, Modifier.weight(1f)) { v -> onChange { copy(returnMax = v) } }
        }
    }
}

@Composable
private fun PlaceChips(places: List<PlaceChoice>, onRemove: (PlaceChoice) -> Unit, onAdd: () -> Unit) = ChipGroup {
    for (place in places) {
        val detail = when (place) {
            is PlaceChoice.Country -> "${place.codes.size} aeroporti"
            is PlaceChoice.Airport -> place.code
        }
        FlightFinderChip(place.label, detail = detail, onRemove = { onRemove(place) })
    }
    FlightFinderChip("Aggiungi", icon = Icons.Filled.Add, onClick = onAdd)
}

@Composable
private fun TravelersSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard(spacing = 14) {
    val people = listOfNotNull(
        count(form.adults, "adulto", "adulti"),
        form.children.takeIf { it > 0 }?.let { count(it, "bambino", "bambini") },
        form.infants.takeIf { it > 0 }?.let { count(it, "neonato", "neonati") },
    ).joinToString(", ")
    CollapsibleSection("Viaggiatori e bagagli", summary = "$people · ${form.seat.label} · ${form.baggage.label}", icon = Icons.Outlined.Group) {
        StepperRow("Adulti", form.adults, 1..9, "12 anni e più") { n -> onChange { copy(adults = n) } }
        StepperRow("Bambini", form.children, 0..8, "2–11 anni, con posto") { n -> onChange { copy(children = n) } }
        StepperRow("Neonati", form.infants, 0..8, "sotto i 2 anni, in braccio") { n -> onChange { copy(infants = n) } }
        Overline("Classe", Modifier.padding(top = 6.dp))
        OptionList(Seat.entries.map { it to it.label }, form.seat) { v -> onChange { copy(seat = v) } }
        Overline("Bagaglio", Modifier.padding(top = 6.dp))
        OptionList(Baggage.entries.map { it to it.label }, form.baggage) { v -> onChange { copy(baggage = v) } }
    }
    FlightFinderTextField(
        form.budget, { v -> onChange { copy(budget = v.filter { it.isDigit() || it == ',' || it == '.' }) } },
        label = if (form.isRoundTrip) "Budget A/R" else "Budget", placeholder = "Nessun limite", suffix = "€", numeric = true,
    )
    Overline("Scali", Modifier.padding(top = 6.dp))
    SegmentedControl(listOf(0 to "Diretti", 1 to "1 scalo", 2 to "2 scali", 3 to "Qualsiasi"), form.maxStops, { v -> onChange { copy(maxStops = v) } })
}

@Composable
private fun MoreFilters(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = CollapsibleSection(
    "Altri filtri", summary = "Arrivi, durate, scali, compagnie", icon = Icons.Outlined.FilterList,
) {
    Overline("Arrivi", Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField("Arrivo andata dalle", form.departureArrivalMin, Modifier.weight(1f)) { v -> onChange { copy(departureArrivalMin = v) } }
        TimeField("entro le", form.departureArrivalMax, Modifier.weight(1f)) { v -> onChange { copy(departureArrivalMax = v) } }
    }
    if (form.isRoundTrip) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Arrivo ritorno dalle", form.returnArrivalMin, Modifier.weight(1f)) { v -> onChange { copy(returnArrivalMin = v) } }
            TimeField("entro le", form.returnArrivalMax, Modifier.weight(1f)) { v -> onChange { copy(returnArrivalMax = v) } }
        }
    }
    TimeField(
        if (form.isRoundTrip) "A casa entro le (giorno del ritorno)" else "Arrivo entro le (giorno della partenza)",
        form.finalArrival, placeholder = "Nessun limite", onClear = { onChange { copy(finalArrival = null) } },
    ) { v -> onChange { copy(finalArrival = v) } }
    if (form.isRoundTrip) StepperRow("Ore minime a destinazione", form.minHours, 0..720) { n -> onChange { copy(minHours = n) } }
    Overline("Durata dei voli (ore)", Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlightFinderTextField(form.departureDurationMin, { v -> onChange { copy(departureDurationMin = v) } }, Modifier.weight(1f), label = "Andata da", numeric = true)
        FlightFinderTextField(form.departureDurationMax, { v -> onChange { copy(departureDurationMax = v) } }, Modifier.weight(1f), label = "a", placeholder = "Nessun limite", numeric = true)
    }
    if (form.isRoundTrip) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlightFinderTextField(form.returnDurationMin, { v -> onChange { copy(returnDurationMin = v) } }, Modifier.weight(1f), label = "Ritorno da", numeric = true)
            FlightFinderTextField(form.returnDurationMax, { v -> onChange { copy(returnDurationMax = v) } }, Modifier.weight(1f), label = "a", placeholder = "Nessun limite", numeric = true)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlightFinderTextField(form.minLayover, { v -> onChange { copy(minLayover = v.filter(Char::isDigit)) } }, Modifier.weight(1f), label = "Scalo minimo (min)", numeric = true)
        FlightFinderTextField(form.maxLayover, { v -> onChange { copy(maxLayover = v.filter(Char::isDigit)) } }, Modifier.weight(1f), label = "Scalo massimo (min)", placeholder = "Nessun limite", numeric = true)
    }
    Overline("Compagnie e scali", Modifier.padding(top = 6.dp))
    SegmentedControl(listOf(AirlineMode.ALL to "Tutte le compagnie", AirlineMode.LOW_COST to "Solo low cost"), form.airlineMode, { v -> onChange { copy(airlineMode = v) } })
    FlightFinderTextField(form.airlines, { v -> onChange { copy(airlines = v.uppercase()) } }, label = "Solo queste compagnie", placeholder = "Es. FR, U2, V7")
    ToggleRow("Escludi le tariffe Basic economy", form.excludeBasicEconomy) { v -> onChange { copy(excludeBasicEconomy = v) } }
    ToggleRow("Accetta scali con cambio volo a carico mio", form.selfTransfer) { v -> onChange { copy(selfTransfer = v) } }
    ToggleRow("Accetta il cambio di aeroporto durante lo scalo", form.airportChange) { v -> onChange { copy(airportChange = v) } }
    ToggleRow("Scali solo in area Schengen", form.schengenTransfers) { v -> onChange { copy(schengenTransfers = v) } }
    if (form.isRoundTrip) {
        Overline("Rientro a", Modifier.padding(top = 6.dp))
        OptionList(listOf(AirportChoice.SAME to "Stesso aeroporto di partenza", AirportChoice.GROUP to "Uno qualsiasi tra quelli di partenza"), form.returnAirport) { v -> onChange { copy(returnAirport = v) } }
        if (!form.anywhere) {
            Overline("Ripartenza da", Modifier.padding(top = 6.dp))
            OptionList(listOf(AirportChoice.SAME to "Stesso aeroporto di arrivo", AirportChoice.GROUP to "Uno qualsiasi tra le destinazioni scelte"), form.returnDeparture) { v -> onChange { copy(returnDeparture = v) } }
            HintText("Se riparti da un altro aeroporto, il trasferimento è a carico tuo.")
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
