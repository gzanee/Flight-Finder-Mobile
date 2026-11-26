package it.gzanee.flightfinder.ui.screens.history

import it.gzanee.flightfinder.ui.i18n.engineText
import it.gzanee.flightfinder.ui.i18n.text
import it.gzanee.flightfinder.ui.i18n.plural
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.FlightFinderCard
import it.gzanee.flightfinder.ui.screens.components.FlightFinderScaffold
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.IconTile
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.screens.components.Tag
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.i18n.names
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType

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
    FlightFinderScaffold(title = stringResource(R.string.your_searches), subtitle = stringResource(R.string.saved_on_phone), onBack = onBack, onToggleTheme = onToggleTheme) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            errorMessage?.let { item { InfoBanner(engineText(it), BannerTone.BAD) } }
            val list = searches ?: return@LazyColumn
            if (list.isEmpty()) {
                item {
                    FlightFinderCard {
                        IconTile(Icons.Outlined.History)
                        Text(stringResource(R.string.no_saved), style = FlightFinderType.titleSm, color = colors.textPrimary)
                        HintText(stringResource(R.string.no_saved_hint))
                    }
                }
            } else {
                item { FieldLabel(plural(R.plurals.saved_searches, list.size), Modifier.padding(horizontal = 4.dp)) }
            }
            items(list, key = { it.job.id }) { saved ->
                val r = saved.job.request
                val done = saved.status == JobStatus.COMPLETE
                FlightFinderCard(
                    Modifier.combinedClickable(onClick = { onOpen(saved.job.id) }, onLongClick = { deleting = saved }),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), spacing = 12,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(if (r.isRoundTrip) Icons.Outlined.SyncAlt else Icons.Filled.FlightTakeoff, size = 42.dp, filled = saved.status.isRunning)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${r.origins.map { names.city(it) }.distinct().joinToString(" + ")} → " +
                                    if (r.destinations.isEmpty()) stringResource(R.string.anywhere) else r.destinations.map(names::city).distinct().let { cities -> cities.take(3).joinToString(", ") + if (cities.size > 3) "…" else "" },
                                style = FlightFinderType.titleSm, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                r.startDate.short() + (if (r.startDate == r.endDate) "" else " → ${r.endDate.short()}") + " · " + stringResource(if (r.isRoundTrip) R.string.round_trip_lower else R.string.one_way_lower),
                                style = FlightFinderType.label, color = colors.textTertiary,
                            )
                        }
                        saved.cheapest?.let {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.from_price), style = FlightFinderType.label, color = colors.textTertiary)
                                Text(it.asEuro(), style = FlightFinderType.price.copy(fontSize = FlightFinderType.title.fontSize), color = colors.textPrimary)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(saved.status.text(), dot = if (done) colors.good else colors.warn)
                        Tag(plural(R.plurals.trips, saved.job.results.size))
                    }
                }
            }
            if (list.isNotEmpty()) item { HintText(stringResource(R.string.long_press_delete)) }
        }
    }
    deleting?.let { saved ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(saved.job.id) }) { Text(stringResource(R.string.delete), color = colors.bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel), color = colors.textSecondary) } },
            containerColor = colors.surface,
        )
    }
}
