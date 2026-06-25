package com.example.library.navigation

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

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onSearchClick = {
                navController.navigate(Screen.Search.route)
            })
        }
        composable(Screen.Search.route) {
            SearchScreen()
        }
        composable(Screen.Upload.route) {
            UploadScreen()
        }
        composable(Screen.Library.route) {
            LibraryScreen(onSearchClick = {
                navController.navigate(Screen.Search.route)
            })
        }
        composable(Screen.Profile.route) {
            ProfileScreen()
        }
    }
}
