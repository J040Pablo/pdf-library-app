package com.example.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.library.model.PageAnimationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemeDataStore(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_preference")
        private val PAGE_ANIMATION_TYPE = stringPreferencesKey("page_animation_type")
        private val DEFAULT_THEME = ThemePreference.SYSTEM
        /** Defaults to realistic fold so the supplied curl effect is the reading default. */
        private val DEFAULT_PAGE_ANIMATION = PageAnimationType.CURL_FOLD
    }

    /** Emits the current [ThemePreference] whenever it changes. */
    val themePreferenceFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val name = prefs[THEME_KEY] ?: DEFAULT_THEME.name
        runCatching { ThemePreference.valueOf(name) }.getOrDefault(DEFAULT_THEME)
    }

    /** Persists the selected [ThemePreference]. */
    suspend fun setThemePreference(preference: ThemePreference) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = preference.name
        }
    }

    /** Emits the reader's page-turn animation preference. Defaults to [PageAnimationType.CURL_FOLD]. */
    val pageAnimationTypeFlow: Flow<PageAnimationType> = context.dataStore.data.map { prefs ->
        val raw = prefs[PAGE_ANIMATION_TYPE] ?: DEFAULT_PAGE_ANIMATION.name
        runCatching { PageAnimationType.valueOf(raw) }.getOrDefault(DEFAULT_PAGE_ANIMATION)
    }

    suspend fun setPageAnimationType(type: PageAnimationType) {
        context.dataStore.edit { prefs ->
            prefs[PAGE_ANIMATION_TYPE] = type.name
        }
    }
}
