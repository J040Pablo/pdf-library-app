package com.example.library.data

import com.example.library.model.PageAnimationType
import kotlinx.coroutines.flow.Flow

class ThemeRepository(private val dataStore: ThemeDataStore) {

    /** Stream of the current [ThemePreference] saved by the user. */
    val themePreference: Flow<ThemePreference> = dataStore.themePreferenceFlow

    /** Persist a new [ThemePreference]. */
    suspend fun setTheme(preference: ThemePreference) {
        dataStore.setThemePreference(preference)
    }

    val pageAnimationType: Flow<PageAnimationType> = dataStore.pageAnimationTypeFlow

    suspend fun setPageAnimationType(type: PageAnimationType) {
        dataStore.setPageAnimationType(type)
    }
}
