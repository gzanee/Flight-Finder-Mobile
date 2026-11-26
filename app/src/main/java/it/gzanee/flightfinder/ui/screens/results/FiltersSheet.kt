package it.gzanee.flightfinder.ui.screens.results

import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.ResultPage
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import it.gzanee.flightfinder.ui.screens.components.ButtonStyle
import it.gzanee.flightfinder.ui.screens.components.ChipGroup
import it.gzanee.flightfinder.ui.screens.components.FieldLabel
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import it.gzanee.flightfinder.ui.screens.components.FlightFinderButton
import it.gzanee.flightfinder.ui.screens.components.FlightFinderChip
import it.gzanee.flightfinder.ui.screens.components.FlightFinderTextField
import it.gzanee.flightfinder.ui.screens.components.HintText
import it.gzanee.flightfinder.ui.screens.components.InfoBanner
import it.gzanee.flightfinder.ui.screens.components.OptionList
import it.gzanee.flightfinder.ui.screens.components.PlaceNames
import it.gzanee.flightfinder.ui.screens.components.SegmentedControl
import it.gzanee.flightfinder.ui.screens.components.TimeField
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

/** Una città per opzione, con tutti i suoi aeroporti (es. Londra: LGW, LTN, STN). */
private fun cityOptions(codes: List<String>, names: PlaceNames): List<Pair<Set<String>, String>> =
    codes.groupBy(names::city).map { (city, list) -> list.toSet() to "$city (${list.sorted().joinToString(", ")})" }.sortedBy { it.second }

/** Filtri sui viaggi salvati: lavorano su tutti i risultati, senza nuove richieste a Google. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(page: ResultPage, filters: ResultFilters, roundTrip: Boolean, names: PlaceNames, onDismiss: () -> Unit, onApply: (ResultFilters) -> Unit) {
    var draft by remember { mutableStateOf(filters) }
    var maxPrice by remember { mutableStateOf(filters.maxPrice?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val maxPriceError = stringResource(R.string.max_price_error)
    val cities = cityOptions(page.facets.destinations, names)
    val facets = page.facets
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.filter_title), style = FlightFinderType.headline, color = colors.textPrimary)
            HintText(stringResource(R.string.filter_hint, page.resultCount))
            FlightFinderTextField(maxPrice, { maxPrice = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } }, label = stringResource(R.string.max_price), placeholder = stringResource(R.string.any), suffix = "€", numeric = true)
            if (roundTrip) {
                FieldLabel(stringResource(R.string.time_at_destination), Modifier.padding(top = 8.dp))
                SegmentedControl(listOf(null to stringResource(R.string.all_f), 24.0 to "24 h", 36.0 to "36 h", 48.0 to "48 h", 60.0 to "60 h"), draft.minHours, { draft = draft.copy(minHours = it) })
                TimeField(stringResource(R.string.home_before), draft.homeBefore, placeholder = stringResource(R.string.any_time), onClear = { draft = draft.copy(homeBefore = null) }) { draft = draft.copy(homeBefore = it) }
            }
            if (facets.origins.size > 1) {
                FieldLabel(stringResource(R.string.departing_from), Modifier.padding(top = 8.dp))
                OptionList(listOf(null to stringResource(R.string.all_airports)) + facets.origins.map { it to "${names.city(it)} ($it)" }, draft.origin) { draft = draft.copy(origin = it) }
            }
            if (cities.size > 1) {
                FieldLabel(stringResource(R.string.destination), Modifier.padding(top = 8.dp))
                OptionList(listOf(emptySet<String>() to stringResource(R.string.all_f)) + cities, draft.destinations) { draft = draft.copy(destinations = it) }
            }
            if (facets.airlines.size > 1) {
                FieldLabel(stringResource(R.string.airline_every_flight), Modifier.padding(top = 8.dp))
                OptionList(listOf(null to stringResource(R.string.all_f)) + facets.airlines.map { it.code to it.name }, draft.airline) { draft = draft.copy(airline = it) }
            }
            if (cities.size > 1) {
                FieldLabel(stringResource(R.string.exclude_destinations), Modifier.padding(top = 8.dp))
                ChipGroup {
                    for ((codes, label) in cities) {
                        val excluded = draft.excluded.containsAll(codes)
                        FlightFinderChip(label, selected = excluded, onClick = {
                            draft = draft.copy(excluded = if (excluded) draft.excluded - codes else draft.excluded + codes)
                        })
                    }
                }
            }
            error?.let { InfoBanner(it, BannerTone.BAD) }
            FlightFinderButton(stringResource(R.string.show_results), leadingIcon = Icons.Outlined.Check, onClick = {
                val price = maxPrice.replace(',', '.').trim()
                val parsed = if (price.isEmpty()) null else price.toDoubleOrNull()
                if (price.isNotEmpty() && parsed == null) {
                    error = maxPriceError
                } else {
                    onApply(draft.copy(maxPrice = parsed))
                    onDismiss()
                }
            }, modifier = Modifier.fillMaxWidth())
            FlightFinderButton(stringResource(R.string.reset_filters), {
                onApply(ResultFilters())
                onDismiss()
            }, Modifier.fillMaxWidth(), ButtonStyle.GHOST)
        }
    }
}
