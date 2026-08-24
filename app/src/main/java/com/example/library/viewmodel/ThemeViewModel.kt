package com.example.library.viewmodel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.data.ThemeDataStore
import com.example.library.data.ThemePreference
import com.example.library.data.ThemeRepository
import com.example.library.model.PageAnimationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages theme and reader preference persistence.
 *
 * Exposes [themePreference] and [isThemeLoaded] to allow MainActivity
 * to hold the splash screen until the initial theme preference is loaded.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ThemeRepository(ThemeDataStore(application))

    private val _isThemeLoaded = MutableStateFlow(false)
    val isThemeLoaded: StateFlow<Boolean> = _isThemeLoaded.asStateFlow()

    /** The user's saved theme preference. Defaults to [ThemePreference.SYSTEM]. */
    val themePreference: StateFlow<ThemePreference> = repository.themePreference
        .onEach { preference ->
            applyNightMode(preference)
            _isThemeLoaded.value = true
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
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
        applyNightMode(preference)
        viewModelScope.launch {
            repository.setTheme(preference)
        }
    }

    fun setPageAnimationType(type: PageAnimationType) {
        viewModelScope.launch {
            repository.setPageAnimationType(type)
        }
    }

    private fun applyNightMode(preference: ThemePreference) {
        val mode = when (preference) {
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
