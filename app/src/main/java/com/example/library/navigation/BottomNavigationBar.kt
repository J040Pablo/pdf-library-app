package com.example.library.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Elevation
import com.example.library.ui.theme.Spacing

/** Compact M3-style active indicator behind the icon only. */
private val IndicatorSize = 40.dp
private val IndicatorTopPadding = 4.dp

@Composable
fun BottomNavigationBar(
    navController: NavController,
    fullScreenExpansion: Float = 0f
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Cached only for nested screens (search/detail/reading) so a parent tab stays highlighted.
    var lastTopLevelRoute by remember { mutableStateOf(Screen.Home.route) }

    val selectedRoute = resolveSelectedTabRoute(currentRoute, lastTopLevelRoute)
    val selectedIndex = bottomNavItems.indexOfFirst { it.route == selectedRoute }
        .coerceAtLeast(0)

    SideEffect {
        val top = bottomNavItems.firstOrNull { it.route == currentRoute }?.route
            ?: if (
                currentRoute != null &&
                (currentRoute.startsWith("collection") ||
                    currentRoute.startsWith("create_collection"))
            ) {
                Screen.Library.route
            } else {
                null
            }
        if (top != null && top != lastTopLevelRoute) {
            lastTopLevelRoute = top
        }
    }

    val isReadingScreen = currentRoute?.startsWith("reading") == true
    val progress = if (isReadingScreen) 1f else fullScreenExpansion.coerceIn(0f, 1f)
    val latestNav = rememberUpdatedState(navController)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                bottom = 6.dp,
                start = Spacing.XXLarge,
                end = Spacing.XXLarge
            )
            .graphicsLayer {
                alpha = 1f - progress
                translationY = progress * 150.dp.toPx()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (progress < 0.98f) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = Elevation.High,
                tonalElevation = Elevation.None,
                modifier = Modifier.wrapContentSize()
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .width(Dimens.BottomNavWidth)
                        .height(Dimens.BottomNavHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    val itemCount = bottomNavItems.size
                    val itemWidth = maxWidth / itemCount
                    val density = LocalDensity.current
                    val indicatorPx = with(density) { IndicatorSize.toPx() }
                    val itemWidthPx = with(density) { itemWidth.toPx() }

                    // Visual-only: slides directly from current X to target X.
                    // Does not drive selection or navigation.
                    val indicatorX = remember { Animatable(Float.NaN) }
                    val targetX = (itemWidthPx - indicatorPx) / 2f + itemWidthPx * selectedIndex

                    LaunchedEffect(selectedIndex, itemWidthPx) {
                        if (indicatorX.value.isNaN()) {
                            indicatorX.snapTo(targetX)
                        } else {
                            indicatorX.animateTo(
                                targetValue = targetX,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }

                    if (!indicatorX.value.isNaN()) {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { indicatorX.value.toDp() },
                                    y = IndicatorTopPadding
                                )
                                .size(IndicatorSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                    }

                    Row(Modifier.fillMaxSize()) {
                        bottomNavItems.forEachIndexed { index, screen ->
                            val isSelected = index == selectedIndex
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.08f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "navIconScale$index"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                label = "navContentColor$index"
                            )

                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = progress < 0.98f,
                                        onClick = {
                                            navigateToBottomTab(latestNav.value, screen)
                                        }
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = IndicatorTopPadding)
                                        .size(IndicatorSize),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        tint = contentColor,
                                        modifier = Modifier
                                            .size(Dimens.IconMedium)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                }
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Selected tab always mirrors the real destination when on a bottom-nav root.
 * [lastTopLevel] is only a fallback for nested routes (search, book detail, …).
 */
private fun resolveSelectedTabRoute(currentRoute: String?, lastTopLevel: String): String {
    if (currentRoute == null) return lastTopLevel
    bottomNavItems.firstOrNull { it.route == currentRoute }?.let { return it.route }
    if (currentRoute.startsWith("collection") || currentRoute.startsWith("create_collection")) {
        return Screen.Library.route
    }
    return lastTopLevel
}

/**
 * Bottom-tab navigation without intermediate destinations or double-taps.
 *
 * Prefer [NavController.popBackStack] when the tab is already on the stack
 * (the usual case for Home). Only [NavController.navigate] when the tab is absent.
 */
private fun navigateToBottomTab(navController: NavController, screen: Screen) {
    val currentRoute = navController.currentDestination?.route
    if (currentRoute == screen.route) return

    // Upload/Library/Profile → Home (and any tab already under the stack): one pop, done.
    if (navController.popBackStack(screen.route, inclusive = false)) {
        return
    }

    navController.navigate(screen.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
