package com.example.library.screens.bookpicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.library.R
import com.example.library.model.Book
import com.example.library.ui.components.BookCoverPlaceholder
import com.example.library.ui.components.isFinished
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Spacing

enum class BookPickerFilter {
    ALL,
    READING,
    UNREAD,
    COMPLETED,
    FAVORITES,
    RECENT
}

enum class BookPickerMode {
    /** Return selected IDs to the previous screen (create/edit collection). */
    SELECT,
    /** Append selected IDs to an existing collection, then pop. */
    ADD_TO_COLLECTION
}

/**
 * Full-screen multi-select book picker with search, filters, and a grid of covers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPickerScreen(
    books: List<Book>,
    initialSelectedIds: Set<String>,
    title: String,
    subtitle: String? = null,
    confirmLabel: String,
    mode: BookPickerMode = BookPickerMode.SELECT,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit,
    onImportBook: (() -> Unit)? = null,
    onCreateCollection: (() -> Unit)? = null,
) {
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(BookPickerFilter.ALL) }
    var selectedIds by remember(initialSelectedIds) { mutableStateOf(initialSelectedIds) }

    val filtered = remember(books, searchQuery, filter) {
        books
            .asSequence()
            .filter { book ->
                searchQuery.isBlank() ||
                    book.title.contains(searchQuery, ignoreCase = true) ||
                    book.author.contains(searchQuery, ignoreCase = true)
            }
            .filter { book ->
                when (filter) {
                    BookPickerFilter.ALL -> true
                    BookPickerFilter.READING -> book.progress > 0f && !book.isFinished()
                    BookPickerFilter.UNREAD -> book.progress <= 0.001f
                    BookPickerFilter.COMPLETED -> book.isFinished()
                    BookPickerFilter.FAVORITES -> book.isBookmarked
                    BookPickerFilter.RECENT -> true // applied via sort below
                }
            }
            .let { seq ->
                if (filter == BookPickerFilter.RECENT) {
                    // Recently added ≈ reverse repository order (last items first).
                    seq.toList().asReversed()
                } else {
                    seq.toList()
                }
            }
    }

    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { selectedIds = emptySet() }) {
                            Text(stringResource(R.string.clear_selection))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                        .padding(bottom = bottomPad),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { onConfirm(selectedIds.toList()) },
                        enabled = selectedIds.isNotEmpty() || mode == BookPickerMode.SELECT,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    ) {
                        Text(
                            if (selectedIds.isEmpty()) confirmLabel
                            else stringResource(
                                R.string.add_n_selected_books,
                                selectedIds.size
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Selection counter
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.n_books_selected, selectedIds.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = Spacing.Large,
                            vertical = Spacing.SMedium
                        )
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_books_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(top = Spacing.SMedium),
                shape = RoundedCornerShape(Dimens.CornerCardLarge)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.Large, vertical = Spacing.SMedium),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookPickerFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(filterLabel(f)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            when {
                books.isEmpty() && mode == BookPickerMode.ADD_TO_COLLECTION -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_books_available_to_add),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                books.isEmpty() -> {
                    BookPickerEmptyState(
                        onImportBook = onImportBook,
                        onCreateCollection = onCreateCollection,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_books_match_filters),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.Large,
                            vertical = Spacing.SMedium
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.SMedium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { book ->
                            val isSelected = book.id in selectedIds
                            BookPickerGridItem(
                                book = book,
                                isSelected = isSelected,
                                onClick = {
                                    selectedIds = if (isSelected) {
                                        selectedIds - book.id
                                    } else {
                                        selectedIds + book.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun filterLabel(filter: BookPickerFilter): String = when (filter) {
    BookPickerFilter.ALL -> stringResource(R.string.filter_all)
    BookPickerFilter.READING -> stringResource(R.string.filter_reading)
    BookPickerFilter.UNREAD -> stringResource(R.string.filter_unread)
    BookPickerFilter.COMPLETED -> stringResource(R.string.filter_completed)
    BookPickerFilter.FAVORITES -> stringResource(R.string.filter_favorites)
    BookPickerFilter.RECENT -> stringResource(R.string.filter_recent)
}

@Composable
private fun BookPickerGridItem(
    book: Book,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pickerItemScale"
    )
    val shape = RoundedCornerShape(Dimens.CornerCard)

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(shape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = shape
                        )
                    } else Modifier
                )
        ) {
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BookCoverPlaceholder(
                    title = book.title,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Progress strip
            if (book.progress > 0f && !book.isFinished()) {
                LinearProgressIndicator(
                    progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                )
            }

            if (book.isFinished()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = stringResource(R.string.read_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            if (book.isBookmarked && !isSelected) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BookPickerEmptyState(
    onImportBook: (() -> Unit)?,
    onCreateCollection: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.EmptyIconContainer)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconHuge),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Large))
        Text(
            text = stringResource(R.string.book_picker_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Small))
        Text(
            text = stringResource(R.string.book_picker_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Large))
        if (onImportBook != null) {
            Button(
                onClick = onImportBook,
                shape = RoundedCornerShape(Dimens.CornerCard)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_book))
            }
        }
        if (onCreateCollection != null) {
            Spacer(modifier = Modifier.height(Spacing.SMedium))
            OutlinedButton(
                onClick = onCreateCollection,
                shape = RoundedCornerShape(Dimens.CornerCard)
            ) {
                Text(stringResource(R.string.create_collection))
            }
        }
    }
}

/**
 * Horizontal shelf preview of selected covers — updates live while building a collection.
 */
@Composable
fun CollectionShelfPreview(
    books: List<Book>,
    collectionName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = collectionName.ifBlank { stringResource(R.string.your_bookshelf) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Spacing.SMedium))
        if (books.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(Dimens.CornerCard),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.shelf_preview_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy((-12).dp)
            ) {
                books.take(12).forEach { book ->
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(92.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        if (book.coverUrl != null) {
                            AsyncImage(
                                model = book.coverUrl,
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            BookCoverPlaceholder(
                                title = book.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = stringResource(R.string.n_books_selected, books.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
