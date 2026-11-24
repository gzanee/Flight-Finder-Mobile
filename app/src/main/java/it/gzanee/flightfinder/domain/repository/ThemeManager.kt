package it.gzanee.flightfinder.domain.repository

import kotlinx.coroutines.flow.Flow

interface ThemeManager {
    val isDarkThemeFlow: Flow<Boolean?>
    suspend fun setDarkTheme(isDark: Boolean)
}
