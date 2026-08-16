package com.example.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.library.data.BookRepository
import com.example.library.data.ThemePreference
import com.example.library.ui.theme.LibraryTheme
import com.example.library.viewmodel.ThemeViewModel

class MainActivity : ComponentActivity() {

    // ThemeViewModel uses AndroidViewModel — no factory needed when using viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: status bar and nav bar are rendered transparently.
        // Their icon colours are adjusted in LibraryTheme's SideEffect.
        enableEdgeToEdge()

        // Kick off DataStore load before the first composition.
        BookRepository.initialize(applicationContext)

        setContent {
            val preference by themeViewModel.themePreference.collectAsState()

            // Resolve SYSTEM preference here — the only place isSystemInDarkTheme() is called.
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