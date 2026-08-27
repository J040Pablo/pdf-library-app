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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

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

    var lastTopLevelRoute by remember { mutableStateOf(Screen.Home.route) }

    val selectedRoute = resolveSelectedTabRoute(currentRoute, lastTopLevelRoute)
    val selectedIndex = bottomNavItems.indexOfFirst { it.route == selectedRoute }
        .coerceAtLeast(0)

    SideEffect {
        val top = bottomNavItems.firstOrNull { it.route == currentRoute }?.route
            ?: if (
                currentRoute != null &&
                (currentRoute.startsWith("collection") ||
                    currentRoute.startsWith("create_collection") ||
                    currentRoute.startsWith("book_picker"))
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

                    val indicatorX = remember { Animatable(Float.NaN) }
                    val targetX = (itemWidthPx - indicatorPx) / 2f + itemWidthPx * selectedIndex
                    var previousSelectedIndex by remember { mutableStateOf(selectedIndex) }

                    // Destination bounce: compress then spring up when selection changes.
                    val destinationBounce = remember { Animatable(1f) }
                    LaunchedEffect(selectedIndex) {
                        destinationBounce.snapTo(0.86f)
                        destinationBounce.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.42f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }

                    // Travel duration scales with tab distance so the indicator
                    // visibly passes intermediate icons on multi-tab jumps.
                    LaunchedEffect(selectedIndex, itemWidthPx) {
                        if (indicatorX.value.isNaN()) {
                            indicatorX.snapTo(targetX)
                        } else {
                            val tabDistance = abs(selectedIndex - previousSelectedIndex)
                                .coerceAtLeast(1)
                            val travelMs = 220 + (tabDistance - 1) * 90
                            indicatorX.animateTo(
                                targetValue = targetX,
                                animationSpec = tween(
                                    durationMillis = travelMs,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                        previousSelectedIndex = selectedIndex
                    }

                    val indicatorCenterX = if (indicatorX.value.isNaN()) {
                        targetX + indicatorPx / 2f
                    } else {
                        indicatorX.value + indicatorPx / 2f
                    }

                    val primary = MaterialTheme.colorScheme.primary
                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

                    if (!indicatorX.value.isNaN()) {
                        // Soft primary glow — clipped circle, no rectangular shadow.
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { (indicatorX.value - 6.dp.toPx()).toDp() },
                                    y = IndicatorTopPadding - 2.dp
                                )
                                .size(IndicatorSize + 12.dp)
                                .graphicsLayer { alpha = 0.35f }
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            primary.copy(alpha = 0.45f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        // Active indicator pill — circle only (no elevation shadow = no square artifact).
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { indicatorX.value.toDp() },
                                    y = IndicatorTopPadding
                                )
                                .size(IndicatorSize)
                                .clip(CircleShape)
                                .background(primaryContainer)
                        )
                    }

                    Row(Modifier.fillMaxSize()) {
                        bottomNavItems.forEachIndexed { index, screen ->
                            val isSelected = index == selectedIndex

                            // Wave: subtle scale as the indicator center passes this tab.
                            val slotCenterX = itemWidthPx * index + itemWidthPx / 2f
                            val distance = abs(indicatorCenterX - slotCenterX) / itemWidthPx
                            val wave = exp(-(distance.pow(2) / 0.22f)).toFloat()
                            val waveScale = 1f + wave * 0.07f

                            val baseScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.1f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "navBaseScale$index"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                label = "navContentColor$index"
                            )

                            val iconScale = if (isSelected) {
                                baseScale * destinationBounce.value
                            } else {
                                // Intermediate tabs get a gentle wave only — never "selected".
                                waveScale.coerceIn(1f, 1.08f)
                            }

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

private fun resolveSelectedTabRoute(currentRoute: String?, lastTopLevel: String): String {
    if (currentRoute == null) return lastTopLevel
    bottomNavItems.firstOrNull { it.route == currentRoute }?.let { return it.route }
    if (currentRoute.startsWith("collection") ||
        currentRoute.startsWith("create_collection") ||
        currentRoute.startsWith("book_picker")
    ) {
        return Screen.Library.route
    }
    return lastTopLevel
}

private fun navigateToBottomTab(navController: NavController, screen: Screen) {
    val currentRoute = navController.currentDestination?.route
    if (currentRoute == screen.route) return

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