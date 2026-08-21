package com.example.library.screens.reading

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.R
import com.example.library.data.BookFiles
import com.example.library.model.Book
import com.example.library.model.BookFormat
import com.example.library.model.Chapter
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.ReadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    bookId: String,
    chapterId: String,
    onBackClick: () -> Unit,
    viewModel: ReadingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(bookId, chapterId) {
        viewModel.load(bookId, chapterId)
    }

    val book = uiState.book
    val chapter = uiState.chapter

    // Handle back button priority
    BackHandler {
        when {
            uiState.zoomScale > 1f -> viewModel.resetZoom()
            !uiState.isControlsVisible -> viewModel.setControlsVisible(true)
            else -> onBackClick()
        }
    }

    if (book == null || chapter == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.totalPages <= 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.Large),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.no_chapter_content),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Button(onClick = onBackClick) {
                    Text(stringResource(R.string.back_to_book))
                }
            }
        }
        return
    }

    val isZoomed = uiState.zoomScale > 1.05f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val zoomScaleAnim = remember { Animatable(1f) }
        val zoomOffsetXAnim = remember { Animatable(0f) }
        val zoomOffsetYAnim = remember { Animatable(0f) }

        LaunchedEffect(uiState.zoomScale, uiState.zoomOffset) {
            if ((zoomScaleAnim.value - uiState.zoomScale).absoluteValue > 0.01f && !zoomScaleAnim.isRunning) {
                zoomScaleAnim.snapTo(uiState.zoomScale)
                zoomOffsetXAnim.snapTo(uiState.zoomOffset.x)
                zoomOffsetYAnim.snapTo(uiState.zoomOffset.y)
            }
        }

        var showDownloadSheet by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        // Page content with Pager & Zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.toggleControls()
                        },
                        onLongPress = {
                            showDownloadSheet = true
                        },
                        onDoubleTap = { tapOffset ->
                            coroutineScope.launch {
                                if (zoomScaleAnim.value > 1.05f) {
                                    launch { zoomScaleAnim.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { zoomOffsetXAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { zoomOffsetYAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                    viewModel.resetZoom()
                                } else {
                                    val targetScale = 2.5f
                                    val centerX = containerWidth / 2f
                                    val centerY = containerHeight / 2f
                                    val maxOffsetX = (containerWidth * (targetScale - 1f)) / 2f
                                    val maxOffsetY = (containerHeight * (targetScale - 1f)) / 2f
                                    val targetOffsetX = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX)
                                    val targetOffsetY = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)

                                    launch { zoomScaleAnim.animateTo(targetScale, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { zoomOffsetXAnim.animateTo(targetOffsetX, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { zoomOffsetYAnim.animateTo(targetOffsetY, tween(250, easing = FastOutSlowInEasing)) }
                                    viewModel.setZoom(targetScale, Offset(targetOffsetX, targetOffsetY))
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val currentlyZoomed = zoomScaleAnim.value > 1.05f
                            val isMultiTouch = event.changes.size > 1

                            if (currentlyZoomed || isMultiTouch) {
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val currentScale = zoomScaleAnim.value
                                    val newScale = (currentScale * zoomChange).coerceIn(1f, 3.5f)

                                    if (newScale <= 1.05f && zoomChange < 1f) {
                                        coroutineScope.launch {
                                            zoomScaleAnim.snapTo(1f)
                                            zoomOffsetXAnim.snapTo(0f)
                                            zoomOffsetYAnim.snapTo(0f)
                                        }
                                        viewModel.resetZoom()
                                    } else {
                                        val maxOffsetX = (containerWidth * (newScale - 1f)).coerceAtLeast(0f) / 2f
                                        val maxOffsetY = (containerHeight * (newScale - 1f)).coerceAtLeast(0f) / 2f

                                        val rawOffsetX = zoomOffsetXAnim.value + panChange.x
                                        val rawOffsetY = zoomOffsetYAnim.value + panChange.y

                                        val newOffsetX = applyPanResistance(rawOffsetX, -maxOffsetX, maxOffsetX)
                                        val newOffsetY = applyPanResistance(rawOffsetY, -maxOffsetY, maxOffsetY)

                                        coroutineScope.launch {
                                            zoomScaleAnim.snapTo(newScale)
                                            zoomOffsetXAnim.snapTo(newOffsetX)
                                            zoomOffsetYAnim.snapTo(newOffsetY)
                                        }
                                        viewModel.setZoom(newScale, Offset(newOffsetX, newOffsetY))
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Gesture finished: smoothly animate back to boundary limits if overstretched
                        val currentScale = zoomScaleAnim.value
                        if (currentScale > 1.05f) {
                            val maxOffsetX = (containerWidth * (currentScale - 1f)).coerceAtLeast(0f) / 2f
                            val maxOffsetY = (containerHeight * (currentScale - 1f)).coerceAtLeast(0f) / 2f

                            val boundedX = zoomOffsetXAnim.value.coerceIn(-maxOffsetX, maxOffsetX)
                            val boundedY = zoomOffsetYAnim.value.coerceIn(-maxOffsetY, maxOffsetY)

                            if ((zoomOffsetXAnim.value - boundedX).absoluteValue > 0.5f || (zoomOffsetYAnim.value - boundedY).absoluteValue > 0.5f) {
                                coroutineScope.launch {
                                    launch {
                                        zoomOffsetXAnim.animateTo(
                                            boundedX,
                                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                    launch {
                                        zoomOffsetYAnim.animateTo(
                                            boundedY,
                                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            key(chapter.id) {
                val nextChapter = book.chapters.getOrNull(uiState.chapterIndex + 1)

                val pagerState = rememberPagerState(
                    initialPage = uiState.currentPage.coerceIn(0, uiState.totalPages - 1),
                    pageCount = { uiState.totalPages + 1 }
                )

                // Sync PagerState with ViewModel for valid chapter pages
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage < uiState.totalPages && pagerState.currentPage != uiState.currentPage) {
                        viewModel.goToPage(pagerState.currentPage)
                    }
                }

                LaunchedEffect(uiState.currentPage) {
                    if (uiState.currentPage < uiState.totalPages && pagerState.currentPage != uiState.currentPage) {
                        pagerState.scrollToPage(uiState.currentPage.coerceIn(0, uiState.totalPages - 1))
                    }
                }

                // Seamless continuous reading: auto-advance to next chapter when settling on transition page
                LaunchedEffect(pagerState.settledPage) {
                    if (pagerState.settledPage == uiState.totalPages && nextChapter != null) {
                        kotlinx.coroutines.delay(400)
                        viewModel.goToNextChapter()
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !isZoomed,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScaleAnim.value
                            scaleY = zoomScaleAnim.value
                            translationX = zoomOffsetXAnim.value
                            translationY = zoomOffsetYAnim.value
                        }
                ) { pageIndex ->
                    if (pageIndex < uiState.totalPages) {
                        PageContentView(
                            book = book,
                            chapter = chapter,
                            pageIndex = pageIndex,
                            totalPagesInChapter = uiState.totalPages
                        )
                    } else {
                        ChapterTransitionCard(
                            book = book,
                            chapter = chapter,
                            chapterIndex = uiState.chapterIndex,
                            nextChapter = nextChapter,
                            onNextChapterClick = { viewModel.goToNextChapter() },
                            onBackToDetailClick = onBackClick
                        )
                    }
                }
            }
        }

        // ---- HEADER CONTROLS ----
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.Small))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.Small))

                    Text(
                        text = "${uiState.currentPage + 1} / ${uiState.totalPages}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { viewModel.toggleBookmark() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.save_reading_position),
                            tint = if (uiState.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            (context as? Activity)?.let { viewModel.toggleOrientation(it) }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = stringResource(R.string.rotate_screen),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---- FOOTER CONTROLS ----
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                ) {
                    if (uiState.totalPages > 1) {
                        Slider(
                            value = (uiState.currentPage + 1).toFloat(),
                            onValueChange = { newValue ->
                                val targetPage = (newValue.toInt() - 1).coerceIn(0, uiState.totalPages - 1)
                                viewModel.goToPage(targetPage)
                            },
                            valueRange = 1f..uiState.totalPages.toFloat(),
                            steps = max(0, uiState.totalPages - 2),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page / Prev chapter button
                        val isFirstPage = uiState.currentPage == 0
                        val hasPrevChapter = uiState.chapterIndex > 0

                        OutlinedButton(
                            onClick = {
                                if (!isFirstPage) {
                                    viewModel.previousPage()
                                } else if (hasPrevChapter) {
                                    viewModel.goToPreviousChapter()
                                }
                            },
                            enabled = !isFirstPage || hasPrevChapter,
                            contentPadding = PaddingValues(horizontal = Spacing.Medium, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isFirstPage) {
                                    stringResource(R.string.previous_chapter)
                                } else {
                                    stringResource(R.string.previous_page)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFirstPage && hasPrevChapter) {
                                    stringResource(R.string.prev_chapter_short)
                                } else {
                                    stringResource(R.string.previous)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.page_of,
                                uiState.currentPage + 1,
                                uiState.totalPages
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Next page / Next chapter button
                        val isLastPage = uiState.currentPage == uiState.totalPages - 1
                        val hasNextChapter = uiState.chapterIndex < (book.chapters.size - 1)

                        Button(
                            onClick = {
                                if (!isLastPage) {
                                    viewModel.nextPage()
                                } else if (hasNextChapter) {
                                    viewModel.goToNextChapter()
                                }
                            },
                            enabled = !isLastPage || hasNextChapter,
                            contentPadding = PaddingValues(horizontal = Spacing.Medium, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isLastPage && hasNextChapter) {
                                    stringResource(R.string.next_chapter_short)
                                } else {
                                    stringResource(R.string.next)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = if (isLastPage) {
                                    stringResource(R.string.next_chapter)
                                } else {
                                    stringResource(R.string.next_page)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showDownloadSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDownloadSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = stringResource(R.string.page_options, uiState.currentPage + 1),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    Surface(
                        onClick = {
                            showDownloadSheet = false
                            val savedMsg = context.getString(R.string.page_saved_gallery)
                            val errorMsg = context.getString(R.string.error_saving_page)
                            coroutineScope.launch(Dispatchers.IO) {
                                val bmp = generatePageBitmap(context, book, chapter, uiState.currentPage)
                                if (bmp != null) {
                                    val success = saveBitmapToGallery(
                                        context = context,
                                        bitmap = bmp,
                                        bookTitle = book.title,
                                        chapterTitle = chapter.title,
                                        pageNumber = uiState.currentPage + 1
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(savedMsg)
                                            }
                                        } else {
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(errorMsg)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.download_page),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Spacing.Medium))
                            Column {
                                Text(
                                    text = stringResource(R.string.download_page),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(R.string.save_page_png),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}

// ── Page Content Renderer Component ─────────────────────────────────────────

@Composable
private fun PageContentView(
    book: Book,
    chapter: Chapter,
    pageIndex: Int,
    totalPagesInChapter: Int
) {
    val context = LocalContext.current
    val globalPage = chapter.startPage + pageIndex
    val pdfFile = remember(book.id) {
        BookFiles.pdfFile(context, book.id).takeIf { it.exists() }
    }
    val comicPage = remember(book.id, globalPage) {
        BookFiles.comicPageFile(context, book.id, globalPage).takeIf { it.exists() }
    }

    when {
        pdfFile != null -> {
            var bitmap by remember(pdfFile.absolutePath, globalPage) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(pdfFile.absolutePath, globalPage) {
                withContext(Dispatchers.IO) {
                    try {
                        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                if (globalPage < renderer.pageCount) {
                                    renderer.openPage(globalPage).use { page ->
                                        val renderW = (page.width * 2).coerceAtLeast(1080)
                                        val renderH = (page.height * 2).coerceAtLeast(1440)
                                        val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        bitmap = bmp
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        bitmap = null
                    }
                }
            }

            PageBitmapView(
                bitmap = bitmap,
                contentDescription = stringResource(
                    R.string.page_of_chapter,
                    pageIndex + 1,
                    chapter.title
                )
            )
        }
        comicPage != null || book.format == com.example.library.model.BookFormat.COMIC -> {
            var bitmap by remember(book.id, globalPage) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(book.id, globalPage) {
                withContext(Dispatchers.IO) {
                    val pageFile = BookFiles.comicPageFile(context, book.id, globalPage)
                    bitmap = try {
                        if (pageFile.exists()) {
                            android.graphics.BitmapFactory.decodeFile(pageFile.absolutePath)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            PageBitmapView(
                bitmap = bitmap,
                contentDescription = stringResource(
                    R.string.page_of_chapter,
                    pageIndex + 1,
                    chapter.title
                )
            )
        }
        else -> {
            EbookPageTextContentView(
                book = book,
                chapter = chapter,
                pageIndex = pageIndex,
                totalPagesInChapter = totalPagesInChapter
            )
        }
    }
}

@Composable
private fun PageBitmapView(
    bitmap: Bitmap?,
    contentDescription: String,
) {
    if (bitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

// ── Ebook Mock Text Page View ────────────────────────────────────────────────

@Composable
private fun EbookPageTextContentView(
    book: Book,
    chapter: Chapter,
    pageIndex: Int,
    totalPagesInChapter: Int
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large)
                .padding(top = 70.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.chapter_page_label, chapter.title, pageIndex + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                val sampleText = generateSamplePageText(book.title, chapter.title, pageIndex)

                Text(
                    text = sampleText.first, // Section Heading
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                Text(
                    text = sampleText.second, // Body paragraph 1
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(Spacing.Small))

                Text(
                    text = sampleText.third, // Body paragraph 2
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${pageIndex + 1} / $totalPagesInChapter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Generates rich structured text for mock/sample book pages. */
private fun generateSamplePageText(
    bookTitle: String,
    chapterTitle: String,
    pageIndex: Int
): Triple<String, String, String> {
    val heading = when (pageIndex % 4) {
        0 -> "1. Fundamental Principles of $chapterTitle"
        1 -> "2. Design Patterns and Clean Architecture"
        2 -> "3. Advanced Development Practices"
        else -> "4. Performance and Maintainability Considerations"
    }

    val body1 = when (pageIndex % 4) {
        0 -> "In \"$bookTitle\", clarity and simplicity are foundational pillars. Writing readable code is not just an aesthetic preference, but an essential need for modern development teams. Every function and class should have a clear, well-defined responsibility."
        1 -> "Applying good abstractions isolates system complexity into modular components. When dealing with software architecture, separating concerns ensures that changes in one layer do not unduly affect other parts of the system."
        2 -> "Automated testing and continuous refactoring go hand in hand. By maintaining a reliable suite of unit and integration tests, developers gain the confidence needed to evolve the code without introducing unwanted regressions."
        else -> "Premature optimization is the root of many problems in software development. First, make the code correct and understandable. Then measure real performance with appropriate tools before applying complex optimization techniques."
    }

    val body2 = when (pageIndex % 3) {
        0 -> "Mastering these concepts requires constant practice and attention to detail. As you progress through $chapterTitle, notice how design choices directly reflect in the final quality of the product."
        1 -> "Organizing file and package structure intuitively drastically reduces the cognitive load for new team members, enabling faster onboarding and a more harmonious workflow."
        else -> "In summary, technical excellence is a continuous journey. Small daily improvements to the codebase accumulate into a significant impact over the project's lifecycle."
    }

    return Triple(heading, body1, body2)
}

/** Applies elastic resistance (freio) when pan offsets drag past minimum/maximum boundary limits. */
private fun applyPanResistance(
    value: Float,
    min: Float,
    max: Float,
    resistance: Float = 0.25f
): Float {
    return when {
        value < min -> min + (value - min) * resistance
        value > max -> max + (value - max) * resistance
        else -> value
    }
}

/** Renders the full page content (PDF or Ebook) to a Bitmap without UI overlays. */
private suspend fun generatePageBitmap(
    context: Context,
    book: Book,
    chapter: Chapter,
    pageIndex: Int
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val globalPage = chapter.startPage + pageIndex
        val pdfFile = BookFiles.pdfFile(context, book.id).takeIf { it.exists() }
        if (pdfFile != null) {
            var generatedBmp: Bitmap? = null
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (globalPage < renderer.pageCount) {
                        renderer.openPage(globalPage).use { page ->
                            val renderW = (page.width * 2).coerceAtLeast(1080)
                            val renderH = (page.height * 2).coerceAtLeast(1440)
                            val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            generatedBmp = bmp
                        }
                    }
                }
            }
            generatedBmp
        } else if (book.format == BookFormat.COMIC ||
            BookFiles.comicPageFile(context, book.id, globalPage).exists()
        ) {
            val pageFile = BookFiles.comicPageFile(context, book.id, globalPage)
            if (pageFile.exists()) {
                android.graphics.BitmapFactory.decodeFile(pageFile.absolutePath)
            } else null
        } else {
            val width = 1080
            val height = 1920
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(android.graphics.Color.WHITE)

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 46f
            }
            val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#1A73E8")
                textSize = 54f
                isFakeBoldText = true
            }
            val metaPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.GRAY
                textSize = 36f
            }

            canvas.drawText("Page ${pageIndex + 1} • ${chapter.title}", 80f, 120f, metaPaint)
            val textTriple = generateSamplePageText(book.title, chapter.title, pageIndex)
            canvas.drawText(textTriple.first, 80f, 240f, titlePaint)

            var yPos = 340f
            for (line in textTriple.second.chunked(42)) {
                canvas.drawText(line, 80f, yPos, paint)
                yPos += 64f
            }
            yPos += 40f
            for (line in textTriple.third.chunked(42)) {
                canvas.drawText(line, 80f, yPos, paint)
                yPos += 64f
            }

            canvas.drawText("${book.author} • Page ${pageIndex + 1}", 80f, height - 100f, metaPaint)
            bmp
        }
    } catch (_: Exception) {
        null
    }
}

private fun sanitizeFileName(text: String): String {
    return text.trim()
        .replace(Regex("[/\\\\:*?\"<>|\\r\\n\\t]"), "_")
        .replace(Regex("\\s+"), "_")
        .take(30)
        .ifBlank { "Document" }
}

/** Saves page PNG image into Android MediaStore Pictures/PDF Library directory. */
private fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    bookTitle: String,
    chapterTitle: String,
    pageNumber: Int
): Boolean {
    val sanitizedBook = sanitizeFileName(bookTitle)
    val sanitizedChapter = sanitizeFileName(chapterTitle)
    val fileName = "${sanitizedBook}_${sanitizedChapter}_Page_$pageNumber.png"

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PDF Library")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

    return try {
        val success = resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: false

        if (!success) {
            resolver.delete(uri, null, null)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

@Composable
fun ChapterTransitionCard(
    book: Book,
    chapter: Chapter,
    chapterIndex: Int,
    nextChapter: Chapter?,
    onNextChapterClick: () -> Unit,
    onBackToDetailClick: () -> Unit
) {
    val isLastChapter = nextChapter == null
    val iconScale = remember { Animatable(0.7f) }

    LaunchedEffect(isLastChapter) {
        iconScale.snapTo(0.7f)
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.XLarge),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = Spacing.Medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.XXLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLastChapter) Icons.Default.EmojiEvents else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Large))

                Text(
                    text = if (isLastChapter) {
                        stringResource(R.string.end_of_book)
                    } else {
                        stringResource(R.string.chapter_completed, chapterIndex + 1)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                HorizontalDivider(
                    modifier = Modifier
                        .width(140.dp)
                        .padding(vertical = Spacing.Small),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                if (!isLastChapter && nextChapter != null) {
                    Text(
                        text = stringResource(R.string.next_chapter_label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(Spacing.Small))

                    Text(
                        text = nextChapter.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.XXLarge))

                    Button(
                        onClick = onNextChapterClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.enter_next_chapter),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Small))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.next)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.finished_book, book.title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.XXLarge))

                    Button(
                        onClick = onBackToDetailClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.back_to_details),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
