package com.example.library.data

/**
 * Represents the user's chosen app theme.
 *
 * LIGHT  → always use light theme
 * DARK   → always use dark theme
 * SYSTEM → follow the OS setting (resolved in MainActivity via isSystemInDarkTheme())
 */
enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM
}
