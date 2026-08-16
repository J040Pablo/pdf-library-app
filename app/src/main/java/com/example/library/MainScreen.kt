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

@Composable
fun MainScreen(themeViewModel: ThemeViewModel) {
    val navController = rememberNavController()
    var fullScreenExpansion by remember { mutableFloatStateOf(0f) }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                fullScreenExpansion = fullScreenExpansion
            )
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
