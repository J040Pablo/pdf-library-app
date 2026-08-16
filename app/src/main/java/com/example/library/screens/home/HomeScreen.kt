package com.example.library.screens.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.ui.components.RecentBookCard
import com.example.library.ui.components.TopRatedBookCard
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.BookViewModel
import kotlin.math.cos
import kotlin.math.sin

// Custom 8-pointed "Sunny" shape for notifications (Figma style)
val SunnyShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f
            val minRadius = maxRadius * 0.85f
            val avgRadius = (maxRadius + minRadius) / 2f
            val amplitude = (maxRadius - minRadius) / 2f
            val numPoints = 8
            val numSegments = 80

            for (i in 0..numSegments) {
                val angle = (i.toFloat() / numSegments) * 2f * Math.PI.toFloat()
                val r = avgRadius + amplitude * cos(numPoints * angle)
                val x = center.x + r * cos(angle - (Math.PI / 2).toFloat())
                val y = center.y + r * sin(angle - (Math.PI / 2).toFloat())

                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSearchClick: () -> Unit,
    onBookClick: (String, String) -> Unit = { _, _ -> },
    paddingValues: PaddingValues = PaddingValues(Dimens.CornerSmall),
    viewModel: BookViewModel = viewModel()
) {
    val recentBooks by viewModel.recentBooks.collectAsState()
    val topRatedBooks by viewModel.topRatedBooks.collectAsState()

    var showNotifications by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Notification count — replace with real state/ViewModel when available
    val notificationCount = 0

    // Notification button colour — uses primary from MaterialTheme (adapts to light/dark)
    val notifButtonColor = MaterialTheme.colorScheme.primary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Notification Button — SunnyShape, primary colour
                    Box(
                        modifier = Modifier
                            .padding(end = Spacing.Medium)
                            .size(Dimens.NotificationButtonSize)
                            .clip(SunnyShape)
                            .background(notifButtonColor)
                            .clickable { showNotifications = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color(0xFFF8F5FF),
                            modifier = Modifier.size(Dimens.NotificationIconSize)
                        )

                        // Badge dot — only shown when there are unread notifications
                        if (notificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Spacing.SMedium),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(Dimens.BadgeDotSize)
                                        .background(
                                            MaterialTheme.colorScheme.error,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { screenPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = screenPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = Spacing.Medium,
                end = Spacing.Medium,
                // Scaffold bottom padding (nav bar insets) + extra breathing room
                bottom = paddingValues.calculateBottomPadding() + Spacing.Large + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            // Extra top breathing room before "Recents" — matches Figma vertical rhythm
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Recents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = Spacing.Large + 14.dp, bottom = Spacing.Small),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(end = Dimens.NotificationButtonSize + Spacing.Medium)
                ) {
                    items(recentBooks) { book ->
                        RecentBookCard(
                            book = book, 
                            onClick = { onBookClick(book.id, "recent") },
                            coverModifier = Modifier.sharedElement(
                                rememberSharedContentState(key = "recent-cover-${book.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                clipInOverlayDuringTransition = OverlayClip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.CornerCoverInner))
                            )
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Top Rated",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = Spacing.Large, bottom = Spacing.Small),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(topRatedBooks) { book ->
                TopRatedBookCard(
                    book = book,
                    onClick = { onBookClick(book.id, "top-rated") },
                    onBookmarkClick = { /* TODO */ },
                    coverModifier = Modifier.sharedElement(
                        rememberSharedContentState(key = "top-rated-cover-${book.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        clipInOverlayDuringTransition = OverlayClip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.CornerCoverInner))
                    )
                )
            }
        }
    }

    if (showNotifications) {
        ModalBottomSheet(
            onDismissRequest = { showNotifications = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outline
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.XLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.Large))
                Text(
                    text = "Nenhuma notificação.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.XXLarge))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun HomeScreenPreview() {
    com.example.library.ui.theme.LibraryTheme {
        androidx.compose.animation.SharedTransitionLayout {
            androidx.compose.animation.AnimatedVisibility(
                visible = true
            ) {
                HomeScreen(
                    animatedVisibilityScope = this,
                    onSearchClick = {}
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun HomeScreenDarkPreview() {
    com.example.library.ui.theme.LibraryTheme(darkTheme = true) {
        androidx.compose.animation.SharedTransitionLayout {
            androidx.compose.animation.AnimatedVisibility(
                visible = true
            ) {
                HomeScreen(
                    animatedVisibilityScope = this,
                    onSearchClick = {}
                )
            }
        }
    }
}
