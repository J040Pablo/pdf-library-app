package com.example.library.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.library.screens.home.HomeScreen
import com.example.library.screens.profile.ProfileScreen
import com.example.library.screens.search.SearchScreen
import com.example.library.screens.library.LibraryScreen
import com.example.library.screens.upload.UploadScreen
import com.example.library.viewmodel.ThemeViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    themeViewModel: ThemeViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                paddingValues = paddingValues,
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                paddingValues = paddingValues,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Upload.route) {
            UploadScreen(paddingValues = paddingValues)
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                paddingValues = paddingValues,
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                paddingValues = paddingValues,
                themeViewModel = themeViewModel
            )
        }
    }
}
