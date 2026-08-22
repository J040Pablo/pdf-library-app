package com.example.library

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.library.navigation.BottomNavigationBar
import com.example.library.navigation.NavGraph
import com.example.library.viewmodel.ThemeViewModel

import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun MainScreen(themeViewModel: ThemeViewModel) {
    val navController = rememberNavController()
    var fullScreenExpansion by remember { mutableFloatStateOf(0f) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val shouldHideNavbar = currentRoute == null ||
        currentRoute.startsWith("reading") ||
        currentRoute.startsWith("create_collection") ||
        currentRoute.startsWith("book_picker") ||
        currentRoute.startsWith("edit_book")

    Scaffold(
        bottomBar = {
            if (!shouldHideNavbar) {
                BottomNavigationBar(
                    navController = navController,
                    fullScreenExpansion = fullScreenExpansion
                )
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            paddingValues = innerPadding,
            themeViewModel = themeViewModel,
            onFullScreenExpansionChanged = { fullScreenExpansion = it }
        )
    }
}
