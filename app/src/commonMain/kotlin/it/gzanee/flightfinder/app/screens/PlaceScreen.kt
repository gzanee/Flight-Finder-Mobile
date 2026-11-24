package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.core.Place
import it.gzanee.flightfinder.app.ui.Hint
import it.gzanee.flightfinder.app.ui.Notice
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Airports
import it.gzanee.flightfinder.engine.MAX_DESTINATIONS
import it.gzanee.flightfinder.engine.MAX_ORIGINS

private sealed interface Hit {
    data class Airport(val code: String, val name: String, val city: String) : Hit
    data class Country(val country: String, val name: String, val linked: Boolean, val airports: List<Airports.Place>) : Hit
}

/** Add an origin or a destination: city, airport, IATA code or (for destinations) a whole country. */
@Composable
fun PlaceScreen(kind: String, onBack: () -> Unit) {
    val c = colors
    val isOrigin = kind == "origins"
    val form by AppState.form.collectAsState()
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val originCodes = form.origins.map { (it as Place.Airport).code }
    val countries = remember(originCodes, isOrigin) { if (isOrigin) emptyMap() else Airports.countries(originCodes) }
    val chosen = if (isOrigin) form.origins else form.destinations
    val hits: List<Hit> = remember(query, form, countries) {
        val q = query.trim()
        if (q.length < 2) return@remember emptyList()
        val taken = (if (isOrigin) originCodes else form.destinationCodes()).toSet()
        val airports = Airports.search(q, 12).filter { it.code !in taken }.map { Hit.Airport(it.code, it.name, it.city) }
        if (isOrigin) return@remember airports
        val needle = Airports.normalize(q)
        val nations = countries.map { (country, g) -> Hit.Country(country, Airports.countryNames[country] ?: country.uppercase(), g.linked, g.airports) }
            .filter { Airports.normalize(it.name).startsWith(needle) || it.country == needle }
            .filter { h -> chosen.none { it is Place.Country && it.country == h.country } }
            .sortedBy { it.name }
            .take(3)
        nations + airports
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun add(hit: Hit) {
        val f = AppState.form.value
        when (hit) {
            is Hit.Country -> {
                val codes = hit.airports.map { it.code }
                if ((f.destinationCodes() + codes).toSet().size > MAX_DESTINATIONS) {
                    error = "Hai raggiunto il numero massimo di aeroporti."
                    return
                }
                // The country already includes its airports.
                val kept = f.destinations.filterNot { it is Place.Airport && it.code in codes }
                AppState.form.value = f.copy(anywhere = false, destinations = kept + Place.Country(hit.country, hit.name, codes))
            }
            is Hit.Airport -> {
                if (chosen.size >= if (isOrigin) MAX_ORIGINS else MAX_DESTINATIONS) {
                    error = "Hai raggiunto il numero massimo di aeroporti."
                    return
                }
                val place = Place.Airport(hit.code, hit.city.ifEmpty { hit.name })
                AppState.form.value = if (isOrigin) f.copy(origins = f.origins + place) else f.copy(anywhere = false, destinations = f.destinations + place)
            }
        }
        query = ""
        error = null
    }

    Screen(if (isOrigin) "Da dove parti" else "Dove vuoi andare", onBack, actions = { TextButton(onClick = onBack) { Text("Fine", fontWeight = FontWeight.Bold) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(if (isOrigin) "Città, aeroporto o codice" else "Città, aeroporto, codice o paese", color = c.faint) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            error?.let { Notice(it, "bad") }
            if (chosen.isNotEmpty()) {
                Hint("Scelti: " + chosen.joinToString(", ") { if (it is Place.Country) "${it.label} (${it.codes.size})" else "${it.label} ${(it as Place.Airport).code}" })
            }
            if (query.trim().length >= 2 && hits.isEmpty()) Hint("Nessun aeroporto trovato. Prova con il nome inglese o il codice IATA.")
            LazyColumn {
                items(hits, key = { if (it is Hit.Country) "@${it.country}" else (it as Hit.Airport).code }) { hit ->
                    Row(Modifier.fillMaxWidth().clickable { add(hit) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            when (hit) {
                                is Hit.Country -> {
                                    Text(hit.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (hit.linked) "Tutto il paese · ${Fmt.count(hit.airports.size, "aeroporto collegato", "aeroporti collegati")}"
                                        else "Senza voli diretti noti · ${hit.airports.map { it.city }.distinct().joinToString(", ")}",
                                        color = c.muted, fontSize = 13.sp, maxLines = 1,
                                    )
                                }
                                is Hit.Airport -> {
                                    Text(hit.city.ifEmpty { hit.name }, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text(hit.name, color = c.muted, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                        Text(
                            if (hit is Hit.Country) Fmt.countryFlag(hit.country) else (hit as Hit.Airport).code,
                            color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = if (hit is Hit.Country) 22.sp else 15.sp,
                        )
                    }
                    HorizontalDivider(color = c.border)
                }
            }
        }
    }
}
