package com.example.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.library.data.BookRepository
import com.example.library.data.ThemePreference
import com.example.library.ui.theme.LibraryTheme
import com.example.library.viewmodel.ThemeViewModel

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold splash screen on screen until initial theme preference is loaded from DataStore
        splashScreen.setKeepOnScreenCondition {
            !themeViewModel.isThemeLoaded.value
        }

        enableEdgeToEdge()

        BookRepository.initialize(applicationContext)

        setContent {
            val preference by themeViewModel.themePreference.collectAsState()

            val isDark = when (preference) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            LibraryTheme(darkTheme = isDark) {
                MainScreen(themeViewModel = themeViewModel)
            }
        }
    }
}