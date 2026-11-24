package it.gzanee.flightfinder.ui.screens.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.search.AirportCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un risultato dell'autocomplete: un aeroporto o un paese intero. */
sealed interface PlaceHit {
    val key: String

    data class Airport(val code: String, val name: String, val city: String) : PlaceHit {
        override val key get() = code
    }

    data class Country(val country: String, val name: String, val linked: Boolean, val airports: List<AirportCatalog.Place>) : PlaceHit {
        override val key get() = "@$country"
    }
}

@HiltViewModel
class PlaceViewModel @Inject constructor(
    private val airportRepository: AirportRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val catalog = MutableStateFlow<AirportCatalog?>(null)
    private val request = MutableStateFlow(PlaceRequest())

    /** Per le destinazioni: le origini scelte (per i paesi collegati) e i codici già presi. */
    data class PlaceRequest(val countries: Boolean = false, val origins: List<String> = emptyList(), val taken: Set<String> = emptySet())

    init {
        viewModelScope.launch { catalog.value = airportRepository.catalog() }
    }

    fun configure(countries: Boolean, origins: List<String>, taken: Set<String>) {
        request.value = PlaceRequest(countries, origins, taken)
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val hits: StateFlow<List<PlaceHit>> = combine(_query, catalog, request) { q, c, r -> if (c == null) emptyList() else search(c, q, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun search(catalog: AirportCatalog, query: String, request: PlaceRequest): List<PlaceHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val airports = catalog.search(q, 12).filter { it.code !in request.taken }.map { PlaceHit.Airport(it.code, it.name, it.city) }
        if (!request.countries) return airports
        val needle = AirportCatalog.normalize(q)
        val countries = catalog.countries(request.origins)
            .map { (code, group) -> PlaceHit.Country(code, catalog.countryNames[code] ?: code.uppercase(), group.linked, group.airports) }
            .filter { AirportCatalog.normalize(it.name).startsWith(needle) || it.country == needle }
            .filter { it.key !in request.taken }
            .sortedBy { it.name }
            .take(3)
        return countries + airports
    }
}
