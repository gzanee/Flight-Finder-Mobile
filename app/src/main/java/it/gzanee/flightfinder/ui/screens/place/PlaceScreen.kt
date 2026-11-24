package it.gzanee.flightfinder.ui.screens.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.search.MAX_DESTINATIONS
import it.gzanee.flightfinder.domain.search.MAX_ORIGINS
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.flagOf
import it.gzanee.flightfinder.ui.preview.DevicePreview
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HeaderAction
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.search.PlaceChoice
import it.gzanee.flightfinder.ui.screens.search.PlaceKind
import it.gzanee.flightfinder.ui.screens.search.SearchViewModel
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme

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
        subtitle = null,
        onBack = onBack,
        action = HeaderAction(Icons.Filled.Check, "Fine", onBack),
        onToggleTheme = onToggleTheme,
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(if (isOrigin) "Città, aeroporto o codice" else "Città, aeroporto, codice o paese", color = colors.textTertiary) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (limitReached) InfoBanner("Hai raggiunto il numero massimo di aeroporti.", BannerTone.BAD)
            if (chosen.isNotEmpty()) {
                HintText("Scelti: " + chosen.joinToString(", ") { if (it is PlaceChoice.Country) "${it.label} (${it.codes.size})" else "${it.label} ${it.codes.single()}" })
            }
            if (query.trim().length >= 2 && hits.isEmpty()) HintText("Nessun aeroporto trovato. Prova con il nome inglese o il codice IATA.")
            LazyColumn {
                items(hits, key = { it.key }) { hit ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !limitReached) { onPick(hit) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            when (hit) {
                                is PlaceHit.Country -> {
                                    Text(hit.name, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (hit.linked) "Tutto il paese · ${count(hit.airports.size, "aeroporto collegato", "aeroporti collegati")}"
                                        else "Senza voli diretti noti · ${hit.airports.map { it.city }.distinct().joinToString(", ")}",
                                        color = colors.textSecondary, fontSize = 13.sp, maxLines = 1,
                                    )
                                }
                                is PlaceHit.Airport -> {
                                    Text(hit.city.ifEmpty { hit.name }, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text(hit.name, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                        Text(
                            when (hit) {
                                is PlaceHit.Country -> flagOf(hit.country)
                                is PlaceHit.Airport -> hit.code
                            },
                            color = colors.brand, fontWeight = FontWeight.ExtraBold, fontSize = if (hit is PlaceHit.Country) 22.sp else 15.sp,
                        )
                    }
                    HorizontalDivider(color = colors.border)
                }
            }
        }
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
