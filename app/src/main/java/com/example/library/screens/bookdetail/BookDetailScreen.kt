package com.example.library.screens.bookdetail

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.library.model.Book
import com.example.library.model.Chapter
import com.example.library.ui.components.BookCoverPlaceholder
import com.example.library.ui.components.RecentBookCard
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Elevation
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

// How far the user needs to pull past the top before the cover is fully
// expanded into the fullscreen viewer.
private val FullscreenActivationDistance = 280.dp

// On release: if expansion is past this fraction, commit to opening
// fullscreen (spring the rest of the way). Below it, spring back to normal.
private const val FullscreenOpenThreshold = 0.55f

// Once open, dragging back below this fraction commits to closing.
private const val FullscreenCloseThreshold = 0.55f

private val FullscreenExpandSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.BookDetailScreen(
    book: Book,
    origin: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Header dimensions (used for the existing scroll-to-collapse behavior)
    val expandedHeaderHeight = 360.dp
    val topBarHeight = 64.dp

    // 0f = cover docked in its normal header spot, 1f = cover fills the
    // entire screen as a dedicated fullscreen viewer. Driven first by
    // pulling past the top of the chapters list, then (once open) directly
    // by a drag-to-dismiss gesture on the cover itself.
    val expansion = remember { Animatable(0f) }
    var isFullscreenOpen by remember { mutableStateOf(false) }

    val activationDistancePx = with(density) { FullscreenActivationDistance.toPx() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If the cover is mid-expansion and the user starts scrolling
                // the list again, let that gesture close the expansion first
                // instead of scrolling chapters underneath it.
                if (!isFullscreenOpen && expansion.value > 0f && available.y < 0f) {
                    val consumePx = min(expansion.value * activationDistancePx, -available.y)
                    coroutineScope.launch {
                        expansion.snapTo((expansion.value - consumePx / activationDistancePx).coerceIn(0f, 1f))
                    }
                    return Offset(0f, -consumePx)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!isFullscreenOpen &&
                    available.y > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                ) {
                    coroutineScope.launch {
                        val next = (expansion.value + available.y / activationDistancePx).coerceIn(0f, 1f)
                        expansion.snapTo(next)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isFullscreenOpen && expansion.value > 0f) {
                    if (expansion.value >= FullscreenOpenThreshold) {
                        expansion.animateTo(1f, FullscreenExpandSpring)
                        isFullscreenOpen = true
                    } else {
                        expansion.animateTo(0f, FullscreenExpandSpring)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        val statusBarPaddingPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val topBarHeightPx = with(density) { topBarHeight.toPx() }
        val headerDropPx = with(density) { (expandedHeaderHeight - topBarHeight).toPx() } - statusBarPaddingPx

        // collapseProgress: 0f = fully expanded header, 1f = fully collapsed
        // into the small top bar. Driven by normal scrolling through chapters
        // — unrelated to the fullscreen expansion below, and mutually
        // exclusive with it (this is only ever >0 once firstVisibleItemScrollOffset
        // has moved past 0, which the NestedScrollConnection above only
        // allows once `expansion` has fully returned to 0).
        val collapseProgress = if (listState.firstVisibleItemIndex > 0) 1f
        else min(1f, max(0f, listState.firstVisibleItemScrollOffset / max(1f, headerDropPx)))

        val e = expansion.value

        // ---- Chapters list ----
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.height(expandedHeaderHeight + 64.dp))
            }
            items(book.chapters) { chapter ->
                ChapterItem(chapter = chapter)
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ---- Sticky top bar background (fades in on collapse, fades out on expansion) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .alpha(collapseProgress * (1f - e))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                .align(Alignment.TopCenter)
        )

        // ---- Back button ----
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(8.dp)
                .align(Alignment.TopStart)
                .alpha(1f - e)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // ---- Cover geometry ----
        val backBtnWidthPx = with(density) { 56.dp.toPx() }
        val coverWidthMax = 150.dp
        val coverHeightMax = 220.dp
        val coverWidthMin = 44.dp
        val coverHeightMin = 64.dp

        // Docked bounds: same header-collapse math as before (scroll-driven).
        val dockedScale = 1f - (1f - (coverWidthMin / coverWidthMax)) * collapseProgress
        val dockedWidthPx = with(density) { coverWidthMax.toPx() } * dockedScale
        val dockedHeightPx = with(density) { coverHeightMax.toPx() } * dockedScale

        val originCoverX = (screenWidthPx - with(density) { coverWidthMax.toPx() }) / 2f
        val originCoverY = statusBarPaddingPx + with(density) { 60.dp.toPx() }
        val originCoverCenterX = originCoverX + with(density) { coverWidthMax.toPx() } / 2f
        val originCoverCenterY = originCoverY + with(density) { coverHeightMax.toPx() } / 2f

        val targetCoverCenterX = backBtnWidthPx + with(density) { 8.dp.toPx() } + with(density) { coverWidthMin.toPx() } / 2f
        val targetCoverCenterY = statusBarPaddingPx + topBarHeightPx / 2f

        val dockedCenterX = originCoverCenterX + (targetCoverCenterX - originCoverCenterX) * collapseProgress
        val dockedCenterY = originCoverCenterY + (targetCoverCenterY - originCoverCenterY) * collapseProgress

        val dockedLeft = dockedCenterX - dockedWidthPx / 2f
        val dockedTop = dockedCenterY - dockedHeightPx / 2f

        // Fullscreen bounds: the entire screen.
        // NOTE: we interpolate actual offset + size here (real layout bounds),
        // NOT a graphicsLayer scale. That's the deliberate choice that avoids
        // the "zoom"/stretched look — the cover genuinely resizes, the same
        // way a Google Photos-style expand transition does, instead of being
        // visually stretched past its native bounds.
        val coverLeftPx = dockedLeft + (0f - dockedLeft) * e
        val coverTopPx = dockedTop + (0f - dockedTop) * e
        val coverWidthPx = dockedWidthPx + (screenWidthPx - dockedWidthPx) * e
        val coverHeightPx = dockedHeightPx + (screenHeightPx - dockedHeightPx) * e
        val coverCornerRadius = Dimens.CornerCoverInner * (1f - e)

        // ---- Scrim, only present while expanding/open ----
        if (e > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(e)
                    .background(Color.Black)
            )
        }

        // ---- Cover: single instance, real bounds animation ----
        Box(
            modifier = Modifier
                .offset { IntOffset(coverLeftPx.toInt(), coverTopPx.toInt()) }
                .size(
                    width = with(density) { coverWidthPx.toDp() },
                    height = with(density) { coverHeightPx.toDp() }
                )
                .clip(RoundedCornerShape(coverCornerRadius))
                .then(
                    if (isFullscreenOpen) {
                        Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (expansion.value >= FullscreenCloseThreshold) {
                                            // Didn't drag far enough — spring back open.
                                            expansion.animateTo(1f, FullscreenExpandSpring)
                                        } else {
                                            expansion.animateTo(0f, FullscreenExpandSpring)
                                            isFullscreenOpen = false
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch { expansion.animateTo(1f, FullscreenExpandSpring) }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                // Dragging in EITHER direction shrinks it back toward
                                // its docked position — a common pattern for
                                // dismissing fullscreen image viewers.
                                coroutineScope.launch {
                                    val next = (expansion.value - abs(dragAmount) / activationDistancePx)
                                        .coerceIn(0f, 1f)
                                    expansion.snapTo(next)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .sharedElement(
                    rememberSharedContentState(key = "${origin}-cover-${book.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ -> tween(durationMillis = 400) }
                )
        ) {
            BookCoverPlaceholder(
                title = book.title,
                modifier = Modifier.fillMaxSize()
                // NOTE: once this is a real Image(...), pass contentScale = ContentScale.Crop
                // so it fills these interpolated bounds cleanly at every step.
            )
        }

        // ---- Title ----
        val titleTopMargin = originCoverY + with(density) { coverHeightMax.toPx() } + with(density) { 32.dp.toPx() }

        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (collapseProgress > 0.8f) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val collapsedLeft = backBtnWidthPx + with(density) { 8.dp.toPx() } + with(density) { coverWidthMin.toPx() } + with(density) { 16.dp.toPx() }
                    val rightPadding = with(density) { 16.dp.toPx() }

                    val expandedMaxWidth = (screenWidthPx - with(density) { 32.dp.toPx() }).toInt()
                    val collapsedMaxWidth = ((screenWidthPx - collapsedLeft - rightPadding) / 0.85f).toInt()
                    val currentMaxWidth = expandedMaxWidth + ((collapsedMaxWidth - expandedMaxWidth) * collapseProgress).toInt()

                    val placeable = measurable.measure(constraints.copy(maxWidth = currentMaxWidth.coerceAtLeast(0)))

                    val width = placeable.width
                    val height = placeable.height

                    val expandedLeft = (screenWidthPx - width) / 2f
                    val expandedTop = titleTopMargin

                    val collapsedTop = statusBarPaddingPx + (topBarHeightPx - height) / 2f

                    val currentLeft = expandedLeft + (collapsedLeft - expandedLeft) * collapseProgress
                    val currentTop = expandedTop + (collapsedTop - expandedTop) * collapseProgress

                    layout(width, height) {
                        placeable.placeRelative(currentLeft.toInt(), currentTop.toInt())
                    }
                }
                .graphicsLayer {
                    val titleScaleMin = 0.85f
                    val titleScale = 1f - (1f - titleScaleMin) * collapseProgress
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = 1f - e
                }
        )

        // ---- Author ----
        val authorTopMargin = titleTopMargin + with(density) { 56.dp.toPx() }
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val width = placeable.width
                    val expandedLeft = (screenWidthPx - width) / 2f

                    layout(width, placeable.height) {
                        placeable.placeRelative(expandedLeft.toInt(), authorTopMargin.toInt())
                    }
                }
                .graphicsLayer {
                    alpha = (1f - (collapseProgress * 2f)).coerceIn(0f, 1f) * (1f - e)
                }
        )
    }
}

@Composable
fun ChapterItem(chapter: Chapter) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
            .clickable { },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card)
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.Medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chapter ${chapter.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = chapter.durationOrPages,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, device = "id:pixel_7_pro")
@Composable
fun BookDetailPreviewHost() {
    LibraryTheme {
        SharedTransitionLayout {

            val mockChaptersShort = listOf(
                Chapter("1", "Introduction", "10 pages"),
                Chapter("2", "Getting Started", "15 pages"),
                Chapter("3", "Next Steps", "20 pages")
            )

            val mockChapters = listOf(
                Chapter("1", "The Architecture of UI", "24 pages"),
                Chapter("2", "State Management Patterns", "18 pages"),
                Chapter("3", "Gestures and Meaningful Motion", "30 pages"),
                Chapter("4", "Navigating the Unknown", "22 pages"),
                Chapter("5", "Shared Elements in Practice", "35 pages")
            )

            val mockChaptersLong = (1..30).map { i ->
                Chapter(i.toString(), "Chapter $i that explains something deep", "${i * 5} pages")
            }

            val mockBooks = listOf(
                Book(id = "book1", title = "1984", author = "George Orwell", chapters = mockChaptersShort, progress = 0.85f),
                Book(id = "book2", title = "Clean Code", author = "Robert C. Martin", chapters = mockChapters, progress = 0.45f),
                Book(
                    id = "book3",
                    title = "The Extraordinary Adventures of a Software Engineer in the Land of Artificial Intelligence",
                    author = "Alan Turing",
                    chapters = mockChaptersLong,
                    progress = 0.10f
                )
            )

            var selectedBook by remember { mutableStateOf<Book?>(null) }

            AnimatedVisibility(
                visible = selectedBook == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(mockBooks) { book ->
                        Box(modifier = Modifier.clickable { selectedBook = book }) {
                            val originStr = "recent"
                            RecentBookCard(
                                book = book,
                                onClick = { selectedBook = book },
                                coverModifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "${originStr}-cover-${book.id}"),
                                    animatedVisibilityScope = this@AnimatedVisibility
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedBook != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 200 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { 200 })
            ) {
                selectedBook?.let { book ->
                    BookDetailScreen(
                        book = book,
                        origin = "recent",
                        animatedVisibilityScope = this@AnimatedVisibility,
                        onBackClick = { selectedBook = null }
                    )
                }
            }
        }
    }
}