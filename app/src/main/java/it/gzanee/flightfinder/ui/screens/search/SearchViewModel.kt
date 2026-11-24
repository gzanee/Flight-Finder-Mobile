package it.gzanee.flightfinder.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SearchValidationError
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.repository.PlannedSearch
import it.gzanee.flightfinder.domain.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Una ricerca lunga da confermare prima di avviarla. */
data class LongSearchPrompt(val search: PlannedSearch)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val airportRepository: AirportRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(SearchFormState())
    val form: StateFlow<SearchFormState> = _form.asStateFlow()

    private val _destinationSets = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val destinationSets: StateFlow<List<Pair<String, String>>> = _destinationSets.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _longSearch = MutableStateFlow<LongSearchPrompt?>(null)
    val longSearch: StateFlow<LongSearchPrompt?> = _longSearch.asStateFlow()

    init {
        viewModelScope.launch {
            _destinationSets.value = airportRepository.catalog().destinationSets.map { it.key to it.value.label }
        }
    }

    fun update(change: SearchFormState.() -> SearchFormState) = _form.update(change)

    fun addOrigin(place: PlaceChoice) = _form.update { it.copy(origins = it.origins + place) }

    /** Un paese include i suoi aeroporti: quelli già scelti uno per uno si tolgono. */
    fun addDestination(place: PlaceChoice) = _form.update { f ->
        val kept = if (place is PlaceChoice.Country) f.destinations.filterNot { it is PlaceChoice.Airport && it.code in place.codes } else f.destinations
        f.copy(anywhere = false, destinations = kept + place)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /** Ricarica una ricerca salvata nel modulo ("Modifica"). */
    fun edit(request: SearchRequest) = viewModelScope.launch {
        val catalog = airportRepository.catalog()
        _form.value = SearchFormState.from(request, catalog::city)
    }

    /** Valida e avvia; una ricerca lunga chiede conferma. `onStarted` riceve l'id della ricerca. */
    fun search(onStarted: (String) -> Unit) {
        if (searchRepository.activeId != null) {
            _errorMessage.value = "Una ricerca è già in corso: aprila da «Le tue ricerche» e fermala prima di avviarne un’altra."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val planned = searchRepository.plan(_form.value.toRequest())
                if (planned.estimatedMinutes >= LONG_SEARCH_MINUTES) _longSearch.value = LongSearchPrompt(planned)
                else onStarted(searchRepository.start(planned))
            } catch (e: FormError) {
                _errorMessage.value = e.message
            } catch (e: SearchValidationError) {
                _errorMessage.value = e.message
            } catch (e: IllegalStateException) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmLongSearch(onStarted: (String) -> Unit) {
        val prompt = _longSearch.value ?: return
        _longSearch.value = null
        viewModelScope.launch {
            runCatching { searchRepository.start(prompt.search) }
                .onSuccess(onStarted)
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun dismissLongSearch() {
        _longSearch.value = null
    }

    companion object {
        private const val LONG_SEARCH_MINUTES = 20
    }
}
