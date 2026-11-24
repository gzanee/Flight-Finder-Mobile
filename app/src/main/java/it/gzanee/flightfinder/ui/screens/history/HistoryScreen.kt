package it.gzanee.flightfinder.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.results.names
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onToggleTheme: () -> Unit) {
    val searches by viewModel.searches.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    HistoryScreenContent(searches, catalog.names(), errorMessage, onBack, onOpen, viewModel::delete, onToggleTheme)
}

/** Le ricerche salvate sul telefono, dalla più recente. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreenContent(
    searches: List<SavedSearch>?,
    names: PlaceNames,
    errorMessage: String? = null,
    onBack: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    var deleting by remember { mutableStateOf<SavedSearch?>(null) }
    FlightFinderScaffold(title = "Le tue ricerche", subtitle = null, onBack = onBack, onToggleTheme = onToggleTheme) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            errorMessage?.let { item { InfoBanner(it, BannerTone.BAD) } }
            val list = searches ?: return@LazyColumn
            if (list.isEmpty()) item { HintText("Nessuna ricerca salvata. Le ricerche restano su questo telefono finché non le elimini.") }
            items(list, key = { it.job.id }) { saved ->
                val r = saved.job.request
                FlightFinderCard(Modifier.combinedClickable(onClick = { onOpen(saved.job.id) }, onLongClick = { deleting = saved })) {
                    Row {
                        Text(
                            "${r.origins.joinToString(" + ") { names.city(it) }} → " +
                                if (r.destinations.isEmpty()) "Ovunque" else r.destinations.take(3).joinToString(", ") { names.city(it) } + if (r.destinations.size > 3) "…" else "",
                            color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, modifier = Modifier.weight(1f),
                        )
                        Text(saved.status.label, color = if (saved.status == JobStatus.COMPLETE) colors.good else colors.warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    HintText(r.startDate.short() + (if (r.startDate == r.endDate) "" else " – ${r.endDate.short()}") + " · " + if (r.isRoundTrip) "andata e ritorno" else "solo andata")
                    HintText(count(saved.job.results.size, "viaggio", "viaggi") + (saved.cheapest?.let { " · da ${it.asEuro()}" } ?: ""))
                }
            }
            if (list.isNotEmpty()) item { HintText("Tieni premuto su una ricerca per eliminarla.") }
        }
    }
    deleting?.let { saved ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare la ricerca?") },
            text = { Text("I risultati salvati di questa ricerca verranno cancellati dal telefono.") },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(saved.job.id) }) { Text("Elimina", color = colors.bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}
