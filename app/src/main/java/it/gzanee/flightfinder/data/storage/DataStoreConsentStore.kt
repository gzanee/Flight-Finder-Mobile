package it.gzanee.flightfinder.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.gzanee.flightfinder.data.google.ConsentStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(name = "google_consent")

/** Il cookie di consenso di Google, rinnovato ogni settimana come nel portale. */
class DataStoreConsentStore(private val context: Context) : ConsentStore {
    companion object {
        private val COOKIES_KEY = stringPreferencesKey("cookies")
        private val SAVED_AT_KEY = longPreferencesKey("saved_at")
        private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
        private val serializer = MapSerializer(String.serializer(), String.serializer())
    }

    override suspend fun load(): Map<String, String>? {
        val preferences = context.consentDataStore.data.first()
        val savedAt = preferences[SAVED_AT_KEY] ?: return null
        if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) return null
        return preferences[COOKIES_KEY]?.let { Json.decodeFromString(serializer, it) }
    }

    override suspend fun save(cookies: Map<String, String>) {
        context.consentDataStore.edit { preferences ->
            preferences[COOKIES_KEY] = Json.encodeToString(serializer, cookies)
            preferences[SAVED_AT_KEY] = System.currentTimeMillis()
        }
    }
}
