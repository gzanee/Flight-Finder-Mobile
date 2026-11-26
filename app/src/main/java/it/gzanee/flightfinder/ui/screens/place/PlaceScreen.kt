package it.gzanee.flightfinder.ui.screens.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.screens.components.Hairline
import it.gzanee.flightfinder.ui.screens.components.ListRow
import it.gzanee.flightfinder.ui.screens.components.RoundIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.search.MAX_DESTINATIONS
import it.gzanee.flightfinder.domain.search.MAX_ORIGINS
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.flagOf
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ChipGroup
import it.gzanee.flightfinder.ui.screens.components.FlagTile
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.IconTile
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.fieldColors
import it.gzanee.flightfinder.ui.screens.search.PlaceChoice
import it.gzanee.flightfinder.ui.screens.search.PlaceKind
import it.gzanee.flightfinder.ui.screens.search.SearchViewModel
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme
import it.gzanee.flightfinder.ui.theme.FlightFinderType

/** Aggiunge una partenza o una destinazione: città, aeroporto, codice IATA o (per le destinazioni) un paese intero. */
@Composable
fun PlaceScreen(
    kind: PlaceKind,
    searchViewModel: SearchViewModel,
    viewModel: PlaceViewModel,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val form by searchViewModel.form.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hits by viewModel.hits.collectAsStateWithLifecycle()
    val isOrigin = kind == PlaceKind.ORIGIN
    val chosen = if (isOrigin) form.origins else form.destinations
    val taken = if (isOrigin) form.origins.flatMap { it.codes }.toSet() else (form.destinationCodes() + form.destinations.map { (it as? PlaceChoice.Country)?.let { c -> "@${c.country}" } ?: "" }).toSet()
    LaunchedEffect(taken) { viewModel.configure(countries = !isOrigin, origins = form.origins.flatMap { it.codes }, taken = taken) }

    val limitReached = if (isOrigin) chosen.size >= MAX_ORIGINS else form.destinationCodes().size >= MAX_DESTINATIONS
    PlaceScreenContent(
        isOrigin = isOrigin,
        query = query,
        hits = hits,
        chosen = chosen,
        limitReached = limitReached,
        onQueryChange = viewModel::onQueryChange,
        onPick = { hit ->
            val place = when (hit) {
                is PlaceHit.Airport -> PlaceChoice.Airport(hit.code, hit.city.ifEmpty { hit.name })
                is PlaceHit.Country -> PlaceChoice.Country(hit.country, hit.name, hit.airports.map { it.code })
            }
            if (isOrigin) searchViewModel.addOrigin(place) else searchViewModel.addDestination(place)
            viewModel.onQueryChange("")
        },
        onBack = onBack,
        onToggleTheme = onToggleTheme,
    )
}

@Composable
fun PlaceScreenContent(
    isOrigin: Boolean,
    query: String,
    hits: List<PlaceHit>,
    chosen: List<PlaceChoice>,
    limitReached: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    onPick: (PlaceHit) -> Unit = {},
    onBack: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    FlightFinderScaffold(
        title = if (isOrigin) "Da dove parti" else "Dove vuoi andare",
        subtitle = if (isOrigin) "Aeroporti di partenza" else "Città, aeroporti o paesi interi",
        onBack = onBack,
        onToggleTheme = onToggleTheme,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = FlightFinderType.body.copy(fontSize = FlightFinderType.titleSm.fontSize, color = colors.textPrimary),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textSecondary) },
                    placeholder = { Text(if (isOrigin) "Cerca aeroporto, città o codice IATA" else "Cerca città, paese o aeroporto", style = FlightFinderType.body, color = colors.textTertiary) },
                    shape = CircleShape,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (limitReached) item { InfoBanner("Hai raggiunto il numero massimo di aeroporti.", BannerTone.BAD) }
            if (chosen.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldLabel("Selezionati (${chosen.size})")
                        ChipGroup {
                            chosen.forEach {
                                FlightFinderChip(it.label, detail = if (it is PlaceChoice.Country) "${it.codes.size} aeroporti" else it.codes.single())
                            }
                        }
                    }
                }
            }
            if (query.trim().length >= 2 && hits.isEmpty()) item { HintText("Nessun aeroporto trovato. Prova con il nome inglese o il codice IATA.") }
            if (hits.isNotEmpty()) {
                item {
                    FlightFinderCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), spacing = 0) {
                        hits.forEachIndexed { i, hit ->
                            if (i > 0) Hairline()
                            HitRow(hit, enabled = !limitReached) { onPick(hit) }
                        }
                    }
                }
            }
        }
        FlightFinderButton(
            if (chosen.isEmpty()) "Fatto" else "Conferma · ${count(chosen.size, if (isOrigin) "partenza" else "meta", if (isOrigin) "partenze" else "mete")}",
            onBack, Modifier.imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth().heightIn(min = 56.dp),
            trailingIcon = Icons.Filled.Check,
        )
    }
}

@Composable
private fun HitRow(hit: PlaceHit, enabled: Boolean, onClick: () -> Unit) {
    ListRow(
        title = when (hit) {
            is PlaceHit.Country -> hit.name
            is PlaceHit.Airport -> "${hit.city.ifEmpty { hit.name }}  ${hit.code}"
        },
        detail = when (hit) {
            is PlaceHit.Country ->
                if (hit.linked) "Tutto il paese · ${count(hit.airports.size, "aeroporto collegato", "aeroporti collegati")}"
                else "Senza voli diretti noti · " + hit.airports.map { it.city }.distinct().joinToString(", ")
            is PlaceHit.Airport -> hit.name
        },
        leading = {
            when (hit) {
                is PlaceHit.Country -> FlagTile(flagOf(hit.country))
                is PlaceHit.Airport -> IconTile(Icons.Outlined.LocalAirport, size = 42.dp)
            }
        },
        onClick = if (enabled) onClick else null,
    ) {
        RoundIconButton(Icons.Filled.Add, "Aggiungi", onClick, size = 34.dp, enabled = enabled)
    }
}

@DevicePreview
@Composable
fun PlaceScreenPreview() {
    FlightFinderTheme {
        PlaceScreenContent(
            isOrigin = false,
            query = "spa",
            hits = listOf(
                PlaceHit.Country("es", "Spagna", linked = true, airports = emptyList()),
                PlaceHit.Airport("SPU", "Split Airport", "Spalato"),
            ),
            chosen = listOf(PlaceChoice.Airport("BCN", "Barcellona")),
        )
    }
}
