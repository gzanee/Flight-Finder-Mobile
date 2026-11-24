package it.gzanee.flightfinder.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.model.AirlineMode
import it.gzanee.flightfinder.domain.model.AirportChoice
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.REAL_ROUTES
import it.gzanee.flightfinder.domain.model.Seat
import it.gzanee.flightfinder.domain.model.TripType
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.estimate
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.ChipGroup
import it.gzanee.flightfinder.ui.screens.components.CollapsibleSection
import it.gzanee.flightfinder.ui.screens.components.DateField
import it.gzanee.flightfinder.ui.screens.components.DaysPicker
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.FlightFinderTextField
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.OptionList
import it.gzanee.flightfinder.ui.screens.components.SegmentedControl
import it.gzanee.flightfinder.ui.screens.components.StepperRow
import it.gzanee.flightfinder.ui.screens.components.TimeField
import it.gzanee.flightfinder.ui.screens.components.ToggleRow
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme
import it.gzanee.flightfinder.ui.theme.HeroNavy
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
        action = HeaderAction(Icons.Outlined.History, "Le tue ricerche", onHistory),
        onToggleTheme = onToggleTheme,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero()
            TripCardSection(form, destinationSets, onChange, onAddPlace)
            TravelersSection(form, onChange)
            FlightFinderCard { MoreFilters(form, onChange) }
            errorMessage?.let { InfoBanner(it, BannerTone.BAD) }
            FlightFinderButton("Cerca voli", onSearch, Modifier.fillMaxWidth(), isLoading = isLoading)
            HintText(
                "Flight Finder confronta i prezzi pubblicati su Google Flights, dalla connessione di questo telefono. " +
                    "Prezzi e disponibilità possono cambiare: verifica sempre le condizioni sul sito del venditore prima di prenotare.",
            )
            TextButton(onClick = onDiagnostics) { Text("Prova la connessione a Google") }
        }
    }
}

@Composable
private fun Hero() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(HeroNavy).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Trova il volo giusto,\nal prezzo giusto.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
        Text(
            "Tutte le compagnie, da più aeroporti insieme. Hai date flessibili? Troviamo noi i giorni più convenienti.",
            color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 21.sp,
        )
    }
}

@Composable
private fun TripCardSection(
    form: SearchFormState,
    destinationSets: List<Pair<String, String>>,
    onChange: (SearchFormState.() -> SearchFormState) -> Unit,
    onAddPlace: (PlaceKind) -> Unit,
) = FlightFinderCard {
    SegmentedControl(listOf(TripType.ROUND_TRIP to "Andata e ritorno", TripType.ONE_WAY to "Solo andata"), form.tripType, { v -> onChange { copy(tripType = v) } })
    SegmentedControl(listOf(DateMode.FIXED to "Date esatte", DateMode.FLEXIBLE to "Date flessibili"), form.dateMode, { v -> onChange { withDateMode(v) } })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlightFinderButton("Un weekend", { onChange { weekend() } }, Modifier.weight(1f), ButtonStyle.SECONDARY)
        FlightFinderButton("Qualsiasi giorno", { onChange { anyDay() } }, Modifier.weight(1f), ButtonStyle.SECONDARY)
    }
    FieldLabel("Da")
    PlaceChips(form.origins, onRemove = { p -> onChange { copy(origins = origins - p) } }, onAdd = { onAddPlace(PlaceKind.ORIGIN) })
    ToggleRow("A: ovunque", form.anywhere) { v -> onChange { copy(anywhere = v) } }
    if (form.anywhere) {
        OptionList(listOf(REAL_ROUTES to "Tutte le mete raggiungibili") + destinationSets, form.destinationSet) { v -> onChange { copy(destinationSet = v) } }
    } else {
        PlaceChips(form.destinations, onRemove = { p -> onChange { copy(destinations = destinations - p) } }, onAdd = { onAddPlace(PlaceKind.DESTINATION) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(if (form.isFlexible) "Dal" else "Partenza", form.startDate, LocalDate.now(), Modifier.weight(1f)) { v -> onChange { withStartDate(v) } }
        if (form.isRoundTrip || form.isFlexible) {
            DateField(if (form.isFlexible) "Al" else "Ritorno", form.endDate, form.startDate, Modifier.weight(1f)) { v -> onChange { copy(endDate = v) } }
        }
    }
    if (form.isFlexible) {
        HintText(
            if (form.isRoundTrip) "Confrontiamo tutte le partenze e i ritorni possibili in questo periodo, nei giorni e con le notti che scegli."
            else "Confrontiamo tutte le partenze possibili in questo periodo, nei giorni che scegli.",
        )
    }
    FieldLabel("Andata")
    if (form.isFlexible) DaysPicker(form.departureDays) { v -> onChange { copy(departureDays = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField("dalle", form.departureMin, Modifier.weight(1f)) { v -> onChange { copy(departureMin = v) } }
        TimeField("alle", form.departureMax, Modifier.weight(1f)) { v -> onChange { copy(departureMax = v) } }
    }
    if (form.isRoundTrip) {
        FieldLabel("Ritorno")
        if (form.isFlexible) DaysPicker(form.returnDays) { v -> onChange { copy(returnDays = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("dalle", form.returnMin, Modifier.weight(1f)) { v -> onChange { copy(returnMin = v) } }
            TimeField("alle", form.returnMax, Modifier.weight(1f)) { v -> onChange { copy(returnMax = v) } }
        }
    }
    if (form.isRoundTrip && form.isFlexible) {
        StepperRow("Notti, minimo", form.minNights, 0..30) { n -> onChange { copy(minNights = n, maxNights = maxOf(n, maxNights)) } }
        StepperRow("Notti, massimo", form.maxNights, 0..30) { n -> onChange { copy(maxNights = n, minNights = minOf(n, minNights)) } }
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
    FlightFinderChip("+ Aggiungi", onClick = onAdd)
}

@Composable
private fun TravelersSection(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = FlightFinderCard {
    val people = listOfNotNull(
        count(form.adults, "adulto", "adulti"),
        form.children.takeIf { it > 0 }?.let { count(it, "bambino", "bambini") },
        form.infants.takeIf { it > 0 }?.let { count(it, "neonato", "neonati") },
    ).joinToString(", ")
    CollapsibleSection("Viaggiatori", summary = "$people · ${form.seat.label}") {
        StepperRow("Adulti", form.adults, 1..9, "12 anni e più") { n -> onChange { copy(adults = n) } }
        StepperRow("Bambini", form.children, 0..8, "2–11 anni, con posto") { n -> onChange { copy(children = n) } }
        StepperRow("Neonati", form.infants, 0..8, "sotto i 2 anni, in braccio") { n -> onChange { copy(infants = n) } }
        FieldLabel("Classe")
        OptionList(Seat.entries.map { it to it.label }, form.seat) { v -> onChange { copy(seat = v) } }
        FieldLabel("Bagaglio")
        OptionList(Baggage.entries.map { it to it.label }, form.baggage) { v -> onChange { copy(baggage = v) } }
    }
    FlightFinderTextField(
        form.budget, { v -> onChange { copy(budget = v.filter { it.isDigit() || it == ',' || it == '.' }) } },
        label = if (form.isRoundTrip) "Budget A/R" else "Budget", placeholder = "Nessun limite", suffix = "€", numeric = true,
    )
    FieldLabel("Scali")
    SegmentedControl(listOf(0 to "Diretti", 1 to "1 scalo", 2 to "2 scali", 3 to "Qualsiasi"), form.maxStops, { v -> onChange { copy(maxStops = v) } })
}

@Composable
private fun MoreFilters(form: SearchFormState, onChange: (SearchFormState.() -> SearchFormState) -> Unit) = CollapsibleSection("Altri filtri") {
    FieldLabel("Arrivi")
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
    FieldLabel("Durata dei voli (ore)")
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
    FieldLabel("Compagnie e scali")
    SegmentedControl(listOf(AirlineMode.ALL to "Tutte le compagnie", AirlineMode.LOW_COST to "Solo low cost"), form.airlineMode, { v -> onChange { copy(airlineMode = v) } })
    FlightFinderTextField(form.airlines, { v -> onChange { copy(airlines = v.uppercase()) } }, label = "Solo queste compagnie", placeholder = "Es. FR, U2, V7")
    ToggleRow("Escludi le tariffe Basic economy", form.excludeBasicEconomy) { v -> onChange { copy(excludeBasicEconomy = v) } }
    ToggleRow("Accetta scali con cambio volo a carico mio", form.selfTransfer) { v -> onChange { copy(selfTransfer = v) } }
    ToggleRow("Accetta il cambio di aeroporto durante lo scalo", form.airportChange) { v -> onChange { copy(airportChange = v) } }
    ToggleRow("Scali solo in area Schengen", form.schengenTransfers) { v -> onChange { copy(schengenTransfers = v) } }
    if (form.isRoundTrip) {
        FieldLabel("Rientro a")
        OptionList(listOf(AirportChoice.SAME to "Stesso aeroporto di partenza", AirportChoice.GROUP to "Uno qualsiasi tra quelli di partenza"), form.returnAirport) { v -> onChange { copy(returnAirport = v) } }
        if (!form.anywhere) {
            FieldLabel("Ripartenza da")
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
