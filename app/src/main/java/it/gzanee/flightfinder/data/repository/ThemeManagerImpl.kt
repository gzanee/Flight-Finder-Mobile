package it.gzanee.flightfinder.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import it.gzanee.flightfinder.domain.repository.ThemeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

class ThemeManagerImpl(private val context: Context) : ThemeManager {
    companion object {
        private val IS_DARK_KEY = booleanPreferencesKey("is_dark_theme")
    }

    /** Null finché l'utente non sceglie: vale il tema di sistema. */
    override val isDarkThemeFlow: Flow<Boolean?> = context.themeDataStore.data.map { preferences -> preferences[IS_DARK_KEY] }

    override suspend fun setDarkTheme(isDark: Boolean) {
        context.themeDataStore.edit { preferences -> preferences[IS_DARK_KEY] = isDark }
    }
}
