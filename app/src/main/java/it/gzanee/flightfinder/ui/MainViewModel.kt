package it.gzanee.flightfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.domain.repository.ThemeManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    searchRepository: SearchRepository,
) : ViewModel() {

    /** Vero mentre una ricerca gira: lo schermo resta acceso (Android ferma le app in background). */
    val isSearching: StateFlow<Boolean> = searchRepository.updates
        .map { it.status.isRunning }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Scelta dell'utente; null finché non sceglie, e vale il tema di sistema. */
    val isDarkTheme: StateFlow<Boolean?> = themeManager.isDarkThemeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun toggleTheme(currentlyDark: Boolean) {
        viewModelScope.launch { themeManager.setDarkTheme(!currentlyDark) }
    }
}
