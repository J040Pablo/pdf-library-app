package com.example.library.screens.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.R
import com.example.library.ui.components.RecentBookCard
import com.example.library.ui.components.TopRatedBookCard
import com.example.library.ui.components.reorderableItemGesture
import com.example.library.ui.theme.Dimens
import kotlin.math.roundToInt
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.BookViewModel
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.example.library.ui.components.AnimatedHamburgerIcon
import com.example.library.ui.components.HomeNavigationDrawer
import kotlinx.coroutines.launch

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

data class DragState(
    val itemId: String,
    val initialIndex: Int,
    val currentIndex: Int,
    val pointerOffset: Offset
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSearchClick: () -> Unit,
    onBookClick: (String, String) -> Unit = { _, _ -> },
    onEditBookClick: (String) -> Unit = {},
    onBooksClick: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues(Dimens.CornerSmall),
    viewModel: BookViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val recentBooks by viewModel.recentBooks.collectAsState()
    val topRatedBooks by viewModel.topRatedBooks.collectAsState()

    var localTopRatedBooks by remember(topRatedBooks) { mutableStateOf(topRatedBooks) }
    var topRatedDragState by remember { mutableStateOf<DragState?>(null) }
    var dragReadyTopRatedId by remember { mutableStateOf<String?>(null) }

    var localRecentBooks by remember(recentBooks) { mutableStateOf(recentBooks) }
    var recentDragState by remember { mutableStateOf<DragState?>(null) }
    var dragReadyRecentId by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current

    // ── Selection state ───────────────────────────────────────────────────────
    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var showNotifications by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Notification count — replace with real state/ViewModel when available
    val notificationCount = 0

    // Notification button colour — uses primary from MaterialTheme (adapts to light/dark)
    val notifButtonColor = MaterialTheme.colorScheme.primary

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteConfirmation) {
        val count = selectedBookIds.size
        val fallbackTitle = stringResource(R.string.this_book)
        val title = if (count == 1) {
            stringResource(R.string.delete_book_title)
        } else {
            stringResource(R.string.delete_books_title)
        }
        val message = if (count == 1) {
            val bookTitle = recentBooks.firstOrNull { it.id == selectedBookIds.first() }?.title
                ?: topRatedBooks.firstOrNull { it.id == selectedBookIds.first() }?.title
                ?: fallbackTitle
            stringResource(R.string.delete_book_confirmation, bookTitle)
        } else {
            stringResource(R.string.delete_books_confirmation, count)
        }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeBooks(selectedBookIds)
                        selectedBookIds = emptySet()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    HomeNavigationDrawer(
        drawerState = drawerState,
        onHomeClick = {
            coroutineScope.launch { drawerState.close() }
        },
        onBooksClick = onBooksClick,
        onLibraryClick = onNavigateToLibrary,
        onSettingsClick = onNavigateToSettings
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (selectedBookIds.isNotEmpty()) {
                    // ── Selection-mode TopAppBar ───────────────────────────────────
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.selected_count, selectedBookIds.size),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedBookIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_selection))
                            }
                        },
                        actions = {
                            // Edit — only when exactly one book is selected
                            if (selectedBookIds.size == 1) {
                                IconButton(onClick = {
                                    val id = selectedBookIds.first()
                                    selectedBookIds = emptySet()
                                    onEditBookClick(id)
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                }
                            }
                            // Delete — always available in selection mode
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    // ── Normal TopAppBar ──────────────────────────────────────────
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
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
                            AnimatedHamburgerIcon(
                                isOpen = drawerState.isOpen,
                                onClick = {
                                    coroutineScope.launch {
                                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                    }
                                }
                            )
                        },
                        actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search)
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
                                contentDescription = stringResource(R.string.notifications),
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
        }
    ) { screenPadding ->
        if (recentBooks.isEmpty() && topRatedBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = screenPadding.calculateTopPadding())
                    .padding(horizontal = Spacing.XXLarge),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.XXLarge))

                    Text(
                        text = stringResource(R.string.no_books_yet),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Text(
                        text = stringResource(R.string.no_books_yet_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = screenPadding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = Spacing.Medium,
                    end = Spacing.Medium,
                    bottom = paddingValues.calculateBottomPadding() + Spacing.Large + 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.recent),
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
                        items(localRecentBooks, key = { it.id }) { book ->
                            val isSelected = book.id in selectedBookIds
                            val isDragging = recentDragState?.itemId == book.id
                            val isDragReady = dragReadyRecentId == book.id

                            RecentBookCard(
                                book = book,
                                isSelected = isSelected,
                                onClick = null,
                                onBookmarkClick = { viewModel.toggleBookmark(book.id) },
                                modifier = Modifier
                                    .then(if (isDragging) Modifier else Modifier.animateItem())
                                    .zIndex(if (isDragging) 100f else if (isDragReady) 10f else 0f)
                                    .reorderableItemGesture(
                                        itemId = book.id,
                                        isSelectedModeActive = selectedBookIds.isNotEmpty(),
                                        onTap = {
                                            if (selectedBookIds.isNotEmpty()) {
                                                selectedBookIds = if (isSelected) selectedBookIds - book.id else selectedBookIds + book.id
                                            } else {
                                                onBookClick(book.id, "recent")
                                            }
                                        },
                                        onLongPress = {
                                            selectedBookIds = selectedBookIds + book.id
                                        },
                                        onDragReady = { id: String ->
                                            dragReadyRecentId = if (id.isNotEmpty()) id else null
                                        },
                                        onDragStart = { id: String ->
                                            val initIdx = localRecentBooks.indexOfFirst { it.id == id }
                                            if (initIdx != -1) {
                                                recentDragState = DragState(
                                                    itemId = id,
                                                    initialIndex = initIdx,
                                                    currentIndex = initIdx,
                                                    pointerOffset = Offset.Zero
                                                )
                                            }
                                            dragReadyRecentId = null
                                        },
                                        onDrag = { dragAmount: Offset ->
                                            val state = recentDragState ?: return@reorderableItemGesture
                                            val newPointerOffset = state.pointerOffset + dragAmount
                                            val itemStepPx = with(density) { (160.dp + Spacing.Medium).toPx() }

                                            val indexDelta = (newPointerOffset.x / itemStepPx).roundToInt()
                                            val targetIndex = (state.initialIndex + indexDelta).coerceIn(0, localRecentBooks.lastIndex)

                                            if (targetIndex != state.currentIndex) {
                                                val updated = localRecentBooks.toMutableList()
                                                val movedItem = updated.removeAt(state.currentIndex)
                                                updated.add(targetIndex, movedItem)
                                                localRecentBooks = updated

                                                recentDragState = state.copy(
                                                    currentIndex = targetIndex,
                                                    pointerOffset = newPointerOffset
                                                )
                                            } else {
                                                recentDragState = state.copy(pointerOffset = newPointerOffset)
                                            }
                                        },
                                        onDragEnd = {
                                            recentDragState = null
                                            dragReadyRecentId = null
                                            viewModel.updateBookOrder(localRecentBooks)
                                        }
                                    )
                                    .graphicsLayer {
                                        if (isDragging && recentDragState != null) {
                                            val state = recentDragState!!
                                            val itemStepPx = (160.dp + Spacing.Medium).toPx()
                                            val slotShiftX = (state.currentIndex - state.initialIndex) * itemStepPx

                                            translationX = state.pointerOffset.x - slotShiftX
                                            translationY = state.pointerOffset.y
                                            shadowElevation = 8.dp.toPx()
                                            scaleX = 1.04f
                                            scaleY = 1.04f
                                        } else if (isDragReady) {
                                            shadowElevation = 4.dp.toPx()
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                        }
                                    },
                                coverModifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "recent-cover-${book.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(durationMillis = 400) },
                                    clipInOverlayDuringTransition = OverlayClip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.CornerCoverInner))
                                )
                            )
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.top_rated),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = Spacing.Large, bottom = Spacing.Small),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(localTopRatedBooks, key = { it.id }) { book ->
                    val isSelected = book.id in selectedBookIds
                    val isDragging = topRatedDragState?.itemId == book.id
                    val isDragReady = dragReadyTopRatedId == book.id

                    TopRatedBookCard(
                        book = book,
                        isSelected = isSelected,
                        onClick = null,
                        onBookmarkClick = { viewModel.toggleBookmark(book.id) },
                        modifier = Modifier
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 100f else if (isDragReady) 10f else 0f)
                            .reorderableItemGesture(
                                itemId = book.id,
                                isSelectedModeActive = selectedBookIds.isNotEmpty(),
                                onTap = {
                                    if (selectedBookIds.isNotEmpty()) {
                                        selectedBookIds = if (isSelected) selectedBookIds - book.id else selectedBookIds + book.id
                                    } else {
                                        onBookClick(book.id, "top-rated")
                                    }
                                },
                                onLongPress = {
                                    selectedBookIds = selectedBookIds + book.id
                                },
                                onDragReady = { id: String ->
                                    dragReadyTopRatedId = if (id.isNotEmpty()) id else null
                                },
                                onDragStart = { id: String ->
                                    val initIdx = localTopRatedBooks.indexOfFirst { it.id == id }
                                    if (initIdx != -1) {
                                        topRatedDragState = DragState(
                                            itemId = id,
                                            initialIndex = initIdx,
                                            currentIndex = initIdx,
                                            pointerOffset = Offset.Zero
                                        )
                                    }
                                    dragReadyTopRatedId = null
                                },
                                onDrag = { dragAmount: Offset ->
                                    val state = topRatedDragState ?: return@reorderableItemGesture
                                    val newPointerOffset = state.pointerOffset + dragAmount
                                    val itemStepX = with(density) { 170.dp.toPx() }
                                    val itemStepY = with(density) { 276.dp.toPx() }

                                    val initialRow = state.initialIndex / 2
                                    val initialCol = state.initialIndex % 2

                                    val rowDelta = (newPointerOffset.y / itemStepY).roundToInt()
                                    val colDelta = (newPointerOffset.x / itemStepX).roundToInt()

                                    val targetRow = (initialRow + rowDelta).coerceAtLeast(0)
                                    val targetCol = (initialCol + colDelta).coerceIn(0, 1)

                                    val targetIndex = (targetRow * 2 + targetCol).coerceIn(0, localTopRatedBooks.lastIndex)

                                    if (targetIndex != state.currentIndex) {
                                        val updated = localTopRatedBooks.toMutableList()
                                        val movedItem = updated.removeAt(state.currentIndex)
                                        updated.add(targetIndex, movedItem)
                                        localTopRatedBooks = updated

                                        topRatedDragState = state.copy(
                                            currentIndex = targetIndex,
                                            pointerOffset = newPointerOffset
                                        )
                                    } else {
                                        topRatedDragState = state.copy(pointerOffset = newPointerOffset)
                                    }
                                },
                                onDragEnd = {
                                    topRatedDragState = null
                                    dragReadyTopRatedId = null
                                    viewModel.updateBookOrder(localTopRatedBooks)
                                }
                            )
                            .graphicsLayer {
                                if (isDragging && topRatedDragState != null) {
                                    val state = topRatedDragState!!
                                    val itemStepX = 170.dp.toPx()
                                    val itemStepY = 276.dp.toPx()

                                    val currentRow = state.currentIndex / 2
                                    val currentCol = state.currentIndex % 2
                                    val initialRow = state.initialIndex / 2
                                    val initialCol = state.initialIndex % 2

                                    val slotShiftX = (currentCol - initialCol) * itemStepX
                                    val slotShiftY = (currentRow - initialRow) * itemStepY

                                    translationX = state.pointerOffset.x - slotShiftX
                                    translationY = state.pointerOffset.y - slotShiftY
                                    shadowElevation = 8.dp.toPx()
                                    scaleX = 1.04f
                                    scaleY = 1.04f
                                } else if (isDragReady) {
                                    shadowElevation = 4.dp.toPx()
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            },
                        coverModifier = Modifier.sharedElement(
                            rememberSharedContentState(key = "top-rated-cover-${book.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> tween(durationMillis = 400) },
                            clipInOverlayDuringTransition = OverlayClip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.CornerCoverInner))
                        )
                    )
                }
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
                    text = stringResource(R.string.notifications),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.Large))
                Text(
                    text = stringResource(R.string.no_notifications),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.XXLarge))
            }
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
