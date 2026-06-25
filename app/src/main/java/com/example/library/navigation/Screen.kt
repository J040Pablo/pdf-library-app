package com.example.library.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val hasBadge: Boolean = false
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Upload : Screen("upload", "Upload", Icons.Default.FileUpload)
    object Library : Screen("library", "Library", Icons.Default.AutoStories)
    object Profile : Screen("profile", "Profile", Icons.Default.Person, hasBadge = true)
    object Search : Screen("search", "Search", Icons.Default.Search)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Upload,
    Screen.Library,
    Screen.Profile
)
