package it.gzanee.flightfinder.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.gzanee.flightfinder.domain.repository.ConnectionTest
import it.gzanee.flightfinder.domain.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {
    private val _result = MutableStateFlow<ConnectionTest?>(null)
    val result: StateFlow<ConnectionTest?> = _result.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun run() = viewModelScope.launch {
        _isLoading.value = true
        _result.value = null
        _result.value = searchRepository.testConnection()
        _isLoading.value = false
    }
}
