package it.gzanee.flightfinder.ui.screens.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.domain.search.AirportCatalog
import it.gzanee.flightfinder.domain.search.PAGE_SIZE
import it.gzanee.flightfinder.domain.search.SearchView
import it.gzanee.flightfinder.ui.navigation.Screen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ResultsMode { DESTINATIONS, TRIPS }

data class ResultsUiState(
    val view: SearchView? = null,
    val catalog: AirportCatalog? = null,
    val sort: SortOrder = SortOrder.PRICE,
    val filters: ResultFilters = ResultFilters(),
    val offset: Int = 0,
    val focus: String? = null,
    /** Null finché l'utente non sceglie: per destinazione se ce n'è più d'una. */
    val mode: ResultsMode? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
) {
    val effectiveMode get() = mode ?: if ((view?.job?.plan?.candidateCount ?: 0) > 1) ResultsMode.DESTINATIONS else ResultsMode.TRIPS
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val airportRepository: AirportRepository,
) : ViewModel() {

    private val searchId: String = checkNotNull(savedStateHandle[Screen.Results.ARG_SEARCH_ID])

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(catalog = airportRepository.catalog()) }
            reload()
        }
        // L'avanzamento si salva circa ogni secondo: si ridisegna al massimo ogni secondo e mezzo.
        viewModelScope.launch {
            searchRepository.updates.filter { it.id == searchId }.sample(1_500).collect { reload() }
        }
    }

    private suspend fun reload() {
        val s = _uiState.value
        try {
            val view = searchRepository.view(searchId, s.sort, s.offset, s.filters, s.focus)
            _uiState.update { it.copy(view = view, errorMessage = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    private fun change(transform: (ResultsUiState) -> ResultsUiState) {
        _uiState.update(transform)
        viewModelScope.launch { reload() }
    }

    fun setSort(sort: SortOrder) = change { it.copy(sort = sort, offset = 0) }

    fun setMode(mode: ResultsMode) = change { it.copy(mode = mode) }

    fun setFilters(filters: ResultFilters) = change { it.copy(filters = filters, offset = 0, focus = null) }

    /** Apre i voli di una città (tutti i suoi aeroporti). */
    fun openDestination(codes: Set<String>) = change { it.copy(filters = it.filters.copy(destinations = codes), mode = ResultsMode.TRIPS, offset = 0, focus = null) }

    fun showAllDestinations() = change { it.copy(filters = it.filters.copy(destinations = emptySet()), mode = ResultsMode.DESTINATIONS) }

    /** Da un suggerimento: mostra il viaggio nella lista. */
    fun focusTrip(id: String) = change { it.copy(focus = id, mode = ResultsMode.TRIPS) }

    fun nextPage() = change { it.copy(offset = it.offset + PAGE_SIZE, focus = null) }

    fun previousPage() = change { it.copy(offset = maxOf(0, it.offset - PAGE_SIZE), focus = null) }

    fun cancel() = viewModelScope.launch {
        searchRepository.cancel(searchId)
        reload()
    }

    fun resume() = viewModelScope.launch {
        _uiState.update { it.copy(isBusy = true) }
        try {
            searchRepository.resume(searchId)
        } catch (e: IllegalStateException) {
            _uiState.update { it.copy(errorMessage = e.message) }
        } finally {
            _uiState.update { it.copy(isBusy = false) }
            reload()
        }
    }
}
