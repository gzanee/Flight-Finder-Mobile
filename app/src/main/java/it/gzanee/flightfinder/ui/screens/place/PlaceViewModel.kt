package it.gzanee.flightfinder.ui.screens.place

import it.gzanee.flightfinder.ui.format.isItalian
import it.gzanee.flightfinder.ui.i18n.countryName
import it.gzanee.flightfinder.ui.i18n.names
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
        val names = catalog.names()
        val english = !isItalian()
        val needle = AirportCatalog.normalize(q)
        // In inglese si cercano anche le città col nome inglese ("Venice", "Munich"); la ricerca del portale resta quella.
        val englishCodes = if (english) catalog.englishCities.filterValues { AirportCatalog.normalize(it).startsWith(needle) }.keys.sorted() else emptyList()
        val codes = (englishCodes + catalog.search(q, 12).map { it.code }).distinct().filter { catalog.exists(it) && it !in request.taken }.take(12)
        val airports = codes.map { code -> PlaceHit.Airport(code, if (english) names.airport(code) else catalog.name(code).orEmpty(), names.city(code)) }
        if (!request.countries) return airports
        val countries = catalog.countries(request.origins)
            .filter { (code, _) ->
                // Il nome del paese nella lingua dell'app, oppure quello italiano del portale.
                listOfNotNull(countryName(code), catalog.countryNames[code]).any { AirportCatalog.normalize(it).startsWith(needle) } || code == needle
            }
            .map { (code, group) -> PlaceHit.Country(code, countryName(code), group.linked, group.airports.map { it.copy(city = names.city(it.code)) }) }
            .filter { it.key !in request.taken }
            .sortedBy { it.name }
            .take(3)
        return countries + airports
    }
}
