package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.ui.AppCard
import it.gzanee.flightfinder.app.ui.Hint
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Job
import kotlinx.coroutines.launch

private val LABELS = mapOf(
    "running" to "In corso", "cancelling" to "In chiusura", "cancelled" to "Fermata",
    "blocked" to "In pausa", "interrupted" to "Interrotta", "complete" to "Completata",
)

/** Searches saved on this phone, newest first. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = colors
    var items by remember { mutableStateOf<List<Job>?>(null) }
    var deleting by remember { mutableStateOf<Job?>(null) }
    var version by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version) {
        val jobs = AppState.jobs
        items = jobs.ids().mapNotNull { runCatching { jobs.get(it) }.getOrNull() }.sortedByDescending { it.createdAt }
    }
    Screen("Le tue ricerche", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val list = items ?: return@LazyColumn
            if (list.isEmpty()) item { Hint("Nessuna ricerca salvata. Le ricerche restano su questo telefono finché non le elimini.") }
            items(list, key = { it.id }) { job ->
                val r = job.request
                val status = if (job.running && AppState.jobs.activeId != job.id) "interrupted" else job.status
                val cheapest = job.results.values.minOfOrNull { it.price }
                AppCard(Modifier.combinedClickable(onClick = { onOpen(job.id) }, onLongClick = { deleting = job })) {
                    Row {
                        Text(
                            "${r.origins.joinToString(" + ", transform = Fmt::city)} → " +
                                if (r.destinations.isEmpty()) "Ovunque" else r.destinations.take(3).joinToString(", ", transform = Fmt::city) + if (r.destinations.size > 3) "…" else "",
                            color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, modifier = Modifier.weight(1f),
                        )
                        Text(LABELS[status] ?: status, color = if (status == "complete") c.good else c.warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Hint(Fmt.shortDay(r.startDate) + (if (r.startDate == r.endDate) "" else " – ${Fmt.shortDay(r.endDate)}") + " · " + if (r.roundTrip) "andata e ritorno" else "solo andata")
                    Hint(Fmt.count(job.results.size, "viaggio", "viaggi") + (cheapest?.let { " · da ${Fmt.money(it)}" } ?: ""))
                }
            }
            if (list.isNotEmpty()) item { Hint("Tieni premuto su una ricerca per eliminarla.") }
        }
    }
    deleting?.let { job ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare la ricerca?") },
            text = { Text("I risultati salvati di questa ricerca verranno cancellati dal telefono.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { runCatching { AppState.jobs.remove(job.id) }; version++ }
                }) { Text("Elimina", color = c.bad) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}
