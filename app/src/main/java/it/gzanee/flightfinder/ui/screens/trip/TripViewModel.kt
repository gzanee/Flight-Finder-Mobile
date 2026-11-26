package it.gzanee.flightfinder.ui.screens.trip

import it.gzanee.flightfinder.ui.i18n.UiText
import it.gzanee.flightfinder.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.model.RecheckResult
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.domain.search.AirportCatalog
import it.gzanee.flightfinder.ui.components.BannerMessage
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.navigation.Screen
import it.gzanee.flightfinder.ui.screens.components.BannerTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripUiState(
    val trip: Trip? = null,
    val isLoaded: Boolean = false,
    val catalog: AirportCatalog? = null,
    val isRechecking: Boolean = false,
    val message: BannerMessage? = null,
)

@HiltViewModel
class TripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val airportRepository: AirportRepository,
) : ViewModel() {
    private val searchId: String = checkNotNull(savedStateHandle[Screen.Trip.ARG_SEARCH_ID])
    private val tripId: String = checkNotNull(savedStateHandle[Screen.Trip.ARG_TRIP_ID])

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = airportRepository.catalog()
            val trip = runCatching { searchRepository.trip(searchId, tripId) }.getOrNull()
            _uiState.update { it.copy(trip = trip, catalog = catalog, isLoaded = true) }
        }
    }

    /** "Aggiorna prezzo": cerca di nuovo gli stessi voli su Google. */
    fun recheck() = viewModelScope.launch {
        _uiState.update { it.copy(isRechecking = true, message = null) }
        try {
            when (val result = searchRepository.recheck(searchId, tripId)) {
                is RecheckResult.Confirmed -> {
                    val message = if (result.priceChanged) BannerMessage(UiText.of(R.string.price_changed, result.result.price.asEuro()), BannerTone.WARN) else BannerMessage(UiText.of(R.string.price_confirmed), BannerTone.GOOD)
                    _uiState.update { it.copy(trip = searchRepository.trip(searchId, tripId), message = message) }
                }
                is RecheckResult.NotConfirmed -> _uiState.update { it.copy(trip = null, message = BannerMessage(UiText.Engine(result.message), BannerTone.BAD)) }
                is RecheckResult.OutsideFilters -> _uiState.update { it.copy(trip = null, message = BannerMessage(UiText.Engine(result.message), BannerTone.BAD)) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = BannerMessage(UiText.of(R.string.recheck_failed, UiText.Engine(e.message.orEmpty())), BannerTone.BAD)) }
        } finally {
            _uiState.update { it.copy(isRechecking = false) }
        }
    }
}
