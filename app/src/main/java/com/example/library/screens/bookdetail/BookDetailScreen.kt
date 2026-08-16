package com.example.library.screens.bookdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.library.data.BookRepository
import com.example.library.model.Book
import com.example.library.model.Chapter
import com.example.library.ui.components.BookCoverPlaceholder
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Elevation
import com.example.library.ui.theme.Spacing
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

private val FullscreenActivationDistance = 280.dp

private const val FullscreenOpenThreshold = 0.55f
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
    modifier: Modifier = Modifier,
    onFullScreenExpansionChanged: (Float) -> Unit = {},
    onChapterClick: (Chapter) -> Unit = {}
) {
    val books by BookRepository.books.collectAsState()
    val liveBook = remember(books, book.id) { books.firstOrNull { it.id == book.id } ?: book }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }

    val expandedHeaderHeight = 390.dp
    val topBarHeight = 64.dp

    var expansionValue by remember { mutableFloatStateOf(0f) }
    var isFullscreenOpen by remember { mutableStateOf(false) }
    var isPullDownStartedAtTop by remember { mutableStateOf(false) }

    val animatableExpansion = remember { Animatable(0f) }

    fun animateExpansionTo(target: Float) {
        coroutineScope.launch {
            animatableExpansion.snapTo(expansionValue)
            animatableExpansion.animateTo(target, animationSpec = FullscreenExpandSpring) {
                expansionValue = value
            }
        }
    }

    LaunchedEffect(expansionValue) {
        onFullScreenExpansionChanged(expansionValue)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Only collapse expanding cover during manual user drag upward
                if (source == NestedScrollSource.UserInput && expansionValue > 0f && available.y < 0f) {
                    val deltaPx = available.y
                    val distancePx = with(density) { FullscreenActivationDistance.toPx() }
                    val newVal = (expansionValue + deltaPx / distancePx).coerceIn(0f, 1f)
                    val consumedY = (newVal - expansionValue) * distancePx
                    expansionValue = newVal
                    if (newVal < FullscreenCloseThreshold) {
                        isFullscreenOpen = false
                    }
                    if (newVal == 0f) {
                        isPullDownStartedAtTop = false
                    }
                    return Offset(0f, consumedY)
                }

                // Rapid drag upward when closed must never activate expansion
                if (available.y < 0f && expansionValue == 0f) {
                    isPullDownStartedAtTop = false
                }

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Ignore flings/inertia for cover expansion
                if (source != NestedScrollSource.UserInput) {
                    isPullDownStartedAtTop = false
                    return Offset.Zero
                }

                // Reset pull down flag if list was scrolled upward during this gesture
                if (consumed.y > 0f) {
                    isPullDownStartedAtTop = false
                }

                val isAtAbsoluteTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

                // Cover expansion ONLY starts when user initiates a pull DOWNWARD at top of list via UserInput
                if (isAtAbsoluteTop && available.y > 0f) {
                    if (consumed.y == 0f || isPullDownStartedAtTop || expansionValue > 0f) {
                        isPullDownStartedAtTop = true
                        val deltaPx = available.y
                        val distancePx = with(density) { FullscreenActivationDistance.toPx() }
                        val newVal = (expansionValue + deltaPx / distancePx).coerceIn(0f, 1f)
                        val consumedY = (newVal - expansionValue) * distancePx
                        expansionValue = newVal
                        if (newVal >= FullscreenOpenThreshold) {
                            isFullscreenOpen = true
                        }
                        return Offset(0f, consumedY)
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isPullDownStartedAtTop = false
                val current = expansionValue
                if (current > 0f && current < 1f) {
                    val target = if (current >= FullscreenOpenThreshold) 1f else 0f
                    isFullscreenOpen = target == 1f
                    animateExpansionTo(target)
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        val screenWidthPx = with(density) { LocalContext.current.resources.displayMetrics.widthPixels.toFloat() }
        val screenHeightPx = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toFloat() }

        val statusBarPaddingPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val topBarHeightPx = with(density) { topBarHeight.toPx() }
        val headerDropPx = with(density) { (expandedHeaderHeight - topBarHeight).toPx() } - statusBarPaddingPx

        val collapseProgress = if (listState.firstVisibleItemIndex > 0) 1f
        else min(1f, max(0f, listState.firstVisibleItemScrollOffset / max(1f, headerDropPx)))

        val e = expansionValue.coerceIn(0f, 1f)

        val currentReadingChapter = remember(liveBook) {
            if (liveBook.progress > 0f || liveBook.currentPage > 0) {
                liveBook.chapters.firstOrNull { ch ->
                    val start = ch.startPage
                    val end = ch.endPage ?: (liveBook.pageCount - 1).coerceAtLeast(start)
                    liveBook.currentPage in start..end
                }
            } else null
        }

        // ---- Chapters list ----
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.height(expandedHeaderHeight + 24.dp))
            }

            items(liveBook.chapters) { chapter ->
                val isCurrentReadingChapter = currentReadingChapter?.id == chapter.id
                val savedPageInChapter = if (isCurrentReadingChapter) {
                    (liveBook.currentPage - chapter.startPage).coerceAtLeast(0)
                } else 0

                ChapterItem(
                    chapter = chapter,
                    isCurrentReadingChapter = isCurrentReadingChapter,
                    savedPageInChapter = savedPageInChapter,
                    onClick = { onChapterClick(chapter) },
                    onToggleRead = {
                        BookRepository.toggleChapterReadState(liveBook.id, chapter.id)
                    },
                    onToggleBookmark = {
                        BookRepository.toggleChapterBookmark(liveBook.id, chapter.id)
                    }
                )
            }

            item {
                // Bottom spacing so scrollable content is fully visible above floating action button & navbar
                Spacer(modifier = Modifier.height(140.dp))
            }
        }

        // ---- Sticky top bar background ----
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
                contentDescription = "Voltar",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // ---- Top End Action Buttons (Bookmark & Edit) ----
        Row(
            modifier = Modifier
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(end = 8.dp)
                .align(Alignment.TopEnd)
                .alpha(1f - e),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { BookRepository.toggleBookmark(liveBook.id) }
            ) {
                Icon(
                    imageVector = if (liveBook.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (liveBook.isBookmarked) "Remover dos salvos" else "Salvar livro",
                    tint = if (liveBook.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { showEditDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar Livro",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---- Cover geometry ----
        val backBtnWidthPx = with(density) { 56.dp.toPx() }
        val coverWidthMax = 150.dp
        val coverHeightMax = 220.dp
        val coverWidthMin = 36.dp
        val coverHeightMin = 52.dp

        val coverWidthPx = with(density) { (coverWidthMax - (coverWidthMax - coverWidthMin) * collapseProgress).toPx() }
        val coverHeightPx = with(density) { (coverHeightMax - (coverHeightMax - coverHeightMin) * collapseProgress).toPx() }

        val coverTopMargin = statusBarPaddingPx + with(density) { 16.dp.toPx() }

        val coverLeftExpanded = (screenWidthPx - coverWidthPx) / 2f
        val coverTopExpanded = coverTopMargin

        val coverLeftCollapsed = backBtnWidthPx + with(density) { 8.dp.toPx() }
        val coverTopCollapsed = statusBarPaddingPx + (topBarHeightPx - coverHeightPx) / 2f

        val currentCoverLeft = coverLeftExpanded + (coverLeftCollapsed - coverLeftExpanded) * collapseProgress
        val currentCoverTop = coverTopExpanded + (coverTopCollapsed - coverTopExpanded) * collapseProgress

        val fsLeft = currentCoverLeft * (1f - e)
        val fsTop = currentCoverTop * (1f - e)
        val fsWidth = coverWidthPx + (screenWidthPx - coverWidthPx) * e
        val fsHeight = coverHeightPx + (screenHeightPx - coverHeightPx) * e

        val coverCornerRadius = Dimens.CornerCoverInner * (1f - e)

        Box(
            modifier = Modifier
                .offset { IntOffset(fsLeft.toInt(), fsTop.toInt()) }
                .size(
                    width = with(density) { fsWidth.toDp() },
                    height = with(density) { fsHeight.toDp() }
                )
                .clip(RoundedCornerShape(coverCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(isFullscreenOpen) {
                    if (isFullscreenOpen) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val current = expansionValue
                                val target = if (current < FullscreenCloseThreshold) 0f else 1f
                                isFullscreenOpen = target == 1f
                                animateExpansionTo(target)
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val distancePx = with(density) { FullscreenActivationDistance.toPx() }
                                val newVal = (expansionValue + dragAmount / distancePx).coerceIn(0f, 1f)
                                expansionValue = newVal
                            }
                        )
                    }
                }
        ) {
            val sharedKey = "${origin}-cover-${liveBook.id}"
            val sharedModifier = if (animatedVisibilityScope != null) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = sharedKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ -> tween(durationMillis = 400) },
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(Dimens.CornerCoverInner))
                )
            } else Modifier

            if (liveBook.coverUrl != null) {
                AsyncImage(
                    model = liveBook.coverUrl,
                    contentDescription = liveBook.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(sharedModifier)
                )
            } else {
                BookCoverPlaceholder(
                    title = liveBook.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(sharedModifier)
                )
            }
        }

        // ---- Title ----
        val titleTopMargin = statusBarPaddingPx + with(density) { (16.dp + coverHeightMax + 16.dp).toPx() }
        var titleHeightPx by remember { mutableStateOf(0f) }

        Text(
            text = liveBook.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (collapseProgress > 0.8f) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val collapsedLeft = backBtnWidthPx + with(density) { 8.dp.toPx() } + with(density) { coverWidthMin.toPx() } + with(density) { 16.dp.toPx() }
                    val rightPadding = with(density) { 56.dp.toPx() }

                    val expandedMaxWidth = (screenWidthPx - with(density) { 32.dp.toPx() }).toInt()
                    val collapsedMaxWidth = ((screenWidthPx - collapsedLeft - rightPadding) / 0.85f).toInt()
                    val currentMaxWidth = expandedMaxWidth + ((collapsedMaxWidth - expandedMaxWidth) * collapseProgress).toInt()

                    val placeable = measurable.measure(constraints.copy(maxWidth = currentMaxWidth.coerceAtLeast(0)))

                    val width = placeable.width
                    val height = placeable.height

                    if (collapseProgress == 0f || titleHeightPx == 0f) {
                        titleHeightPx = height.toFloat()
                    }

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
        Text(
            text = liveBook.author,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val width = placeable.width
                    val expandedLeft = (screenWidthPx - width) / 2f

                    val spacingPx = with(density) { 12.dp.toPx() }
                    val effectiveTitleHeight = if (titleHeightPx > 0f) titleHeightPx else with(density) { 36.dp.toPx() }
                    val dynamicAuthorTopMargin = titleTopMargin + effectiveTitleHeight + spacingPx

                    layout(width, placeable.height) {
                        placeable.placeRelative(expandedLeft.toInt(), dynamicAuthorTopMargin.toInt())
                    }
                }
                .graphicsLayer {
                    alpha = (1f - (collapseProgress * 2f)).coerceIn(0f, 1f) * (1f - e)
                }
        )

       // ---- COMPACT FLOATING READING ACTION PILL BUTTON ----
        val hasStartedReading = liveBook.progress > 0f || liveBook.currentPage > 0
        val buttonText = if (hasStartedReading) "Continuar" else "Começar"

        val targetChapter = remember(liveBook) {
            if (hasStartedReading) {
                liveBook.chapters.firstOrNull { ch ->
                    val start = ch.startPage
                    val end = ch.endPage ?: (liveBook.pageCount - 1).coerceAtLeast(start)
                    liveBook.currentPage in start..end
                } ?: liveBook.chapters.firstOrNull()
                    ?: Chapter("1", "Capítulo 1", "")
            } else {
                liveBook.chapters.firstOrNull()
                    ?: Chapter("1", "Capítulo 1", "")
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 20.dp,
                    bottom = 100.dp
                )
                .alpha(1f - e)
        ) {
            Surface(
                onClick = { onChapterClick(targetChapter) },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (hasStartedReading)
                            Icons.Default.PlayArrow
                        else
                            Icons.Default.AutoStories,
                        contentDescription = buttonText,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // ---- EDIT BOOK DIALOG ----
        if (showEditDialog) {
            EditBookDialog(
                book = liveBook,
                onDismiss = { showEditDialog = false },
                onSave = { updatedBook ->
                    BookRepository.updateBook(updatedBook)
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
fun ChapterItem(
    chapter: Chapter,
    isCurrentReadingChapter: Boolean = false,
    savedPageInChapter: Int = 0,
    onClick: () -> Unit = {},
    onToggleRead: () -> Unit = {},
    onToggleBookmark: () -> Unit = {}
) {
    val cardColor = if (isCurrentReadingChapter) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderStroke = if (isCurrentReadingChapter) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card)
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.Medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleRead,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (chapter.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (chapter.isRead) "Marcar como não lido" else "Marcar como lido",
                    tint = if (chapter.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Capítulo ${chapter.id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isCurrentReadingChapter) {
                        Text(
                            text = "▶ Continuar da pág. ${savedPageInChapter + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = chapter.durationOrPages,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (chapter.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (chapter.isBookmarked) "Remover dos salvos" else "Salvar capítulo",
                    tint = if (chapter.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EditBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (Book) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var coverUrl by remember { mutableStateOf(book.coverUrl) }
    var titleError by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coverUrl = it.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Editar Livro",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.Small),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Capa",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        BookCoverPlaceholder(title = title.ifEmpty { "Capa" })
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Small))

                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alterar Capa")
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = it.isBlank()
                    },
                    label = { Text("Título do Livro") },
                    isError = titleError,
                    supportingText = {
                        if (titleError) {
                            Text("O título não pode ser vazio", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.Small))

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Nome do Autor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val updated = book.copy(
                        title = title.trim(),
                        author = author.trim(),
                        coverUrl = coverUrl
                    )
                    onSave(updated)
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}