package it.gzanee.flightfinder.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.domain.search.AirportCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Una ricerca salvata come la mostra lo storico. */
data class SavedSearch(val job: SearchJob, val status: JobStatus, val cheapest: Double?)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val airportRepository: AirportRepository,
) : ViewModel() {

    private val _searches = MutableStateFlow<List<SavedSearch>?>(null)
    val searches: StateFlow<List<SavedSearch>?> = _searches.asStateFlow()

    private val _catalog = MutableStateFlow<AirportCatalog?>(null)
    val catalog: StateFlow<AirportCatalog?> = _catalog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _catalog.value = airportRepository.catalog()
        val active = searchRepository.activeId
        _searches.value = searchRepository.all().map { job ->
            // Una ricerca salvata "in corso" che non gira più è stata interrotta (app chiusa).
            val status = if (job.status.isRunning && job.id != active) JobStatus.INTERRUPTED else job.status
            SavedSearch(job, status, job.results.values.minOfOrNull { it.price })
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        try {
            searchRepository.remove(id)
        } catch (e: IllegalStateException) {
            _errorMessage.value = e.message
        }
        refresh()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
