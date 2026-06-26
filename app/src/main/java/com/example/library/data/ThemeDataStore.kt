package com.example.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemeDataStore(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_preference")
        private val DEFAULT = ThemePreference.SYSTEM
    }

    /** Emits the current [ThemePreference] whenever it changes. */
    val themePreferenceFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val name = prefs[THEME_KEY] ?: DEFAULT.name
        ThemePreference.valueOf(name)
    }

    /** Persists the selected [ThemePreference]. */
    suspend fun setThemePreference(preference: ThemePreference) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = preference.name
        }
    }
}
