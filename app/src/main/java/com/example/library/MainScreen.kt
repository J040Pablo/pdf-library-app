package com.example.library

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.library.navigation.BottomNavigationBar
import com.example.library.navigation.NavGraph
import com.example.library.navigation.Screen
import com.example.library.ui.components.HomeNavigationDrawer
import com.example.library.viewmodel.ThemeViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(themeViewModel: ThemeViewModel) {
    val navController = rememberNavController()
    var fullScreenExpansion by remember { mutableFloatStateOf(0f) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val shouldHideNavbar = currentRoute == null ||
        currentRoute.startsWith("reading") ||
        currentRoute.startsWith("create_collection") ||
        currentRoute.startsWith("book_picker") ||
        currentRoute.startsWith("edit_book")

    val isHomeScreen = currentRoute == Screen.Home.route

    HomeNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isHomeScreen,
        onHomeClick = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Home.route) {
                navController.navigate(Screen.Home.route) {
                    launchSingleTop = true
                }
            }
        },
        onBooksClick = {
            coroutineScope.launch { drawerState.close() }
        },
        onLibraryClick = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Library.route) {
                navController.navigate(Screen.Library.route) {
                    launchSingleTop = true
                }
            }
        },
        onSettingsClick = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Profile.route) {
                navController.navigate(Screen.Profile.route) {
                    launchSingleTop = true
                }
            }
        }
    ) {
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
                drawerState = drawerState,
                onFullScreenExpansionChanged = { fullScreenExpansion = it }
            )
        }
    }
}
