package com.example.library.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Elevation
import com.example.library.ui.theme.Spacing

@Composable
fun BottomNavigationBar(
    navController: NavController,
    fullScreenExpansion: Float = 0f
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide smoothly during fullscreen cover expansion
    val progress = fullScreenExpansion.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                bottom = Spacing.Large,
                start = Spacing.XXLarge,
                end = Spacing.XXLarge
            )
            .graphicsLayer {
                alpha = 1f - progress
                translationY = progress * 150.dp.toPx()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = Elevation.High,
            tonalElevation = Elevation.None,
            modifier = Modifier.wrapContentSize()
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = Elevation.None,
                modifier = Modifier
                    .width(Dimens.BottomNavWidth)
                    .height(Dimens.BottomNavHeight)
            ) {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(Dimens.IconMedium)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}
