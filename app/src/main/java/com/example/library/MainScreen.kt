package com.example.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.library.navigation.BottomNavigationBar
import com.example.library.navigation.NavGraph
import com.example.library.viewmodel.ThemeViewModel

@Composable
fun MainScreen(themeViewModel: ThemeViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            paddingValues = innerPadding,
            themeViewModel = themeViewModel
        )
    }
}
