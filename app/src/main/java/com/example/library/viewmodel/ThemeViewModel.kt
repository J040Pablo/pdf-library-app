package com.example.library.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.data.ThemeDataStore
import com.example.library.data.ThemePreference
import com.example.library.data.ThemeRepository
import com.example.library.model.PageAnimationType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages theme and reader preference persistence.
 *
 * Deliberately exposes only [ThemePreference] — NOT a Boolean.
 * The SYSTEM → Boolean resolution is handled in MainActivity using
 * isSystemInDarkTheme(), keeping this ViewModel 100% Compose-API-free.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ThemeRepository(ThemeDataStore(application))

    /** The user's saved theme preference. Defaults to [ThemePreference.SYSTEM]. */
    val themePreference: StateFlow<ThemePreference> = repository.themePreference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemePreference.SYSTEM
        )

    /** Reader page-turn effect. Defaults to [PageAnimationType.CURL_FOLD]. */
    val pageAnimationType: StateFlow<PageAnimationType> = repository.pageAnimationType
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PageAnimationType.CURL_FOLD
        )

    /** Called from the UI (e.g. ProfileScreen settings). */
    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch {
            repository.setTheme(preference)
        }
    }

    fun setPageAnimationType(type: PageAnimationType) {
        viewModelScope.launch {
            repository.setPageAnimationType(type)
        }
    }
}
