package com.example.library.screens.collectiondetail

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.R
import com.example.library.model.Book
import com.example.library.model.Collection
import com.example.library.screens.home.DragState
import com.example.library.ui.components.CollectionCard
import com.example.library.ui.components.CollectionCoverThumbnail
import com.example.library.ui.components.LibraryBookItem
import com.example.library.ui.components.getEffectiveCollectionCover
import com.example.library.ui.components.reorderableItemGesture
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.CollectionViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CollectionDetailScreen(
    collectionId: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBookClick: (bookId: String, origin: String) -> Unit,
    onCollectionClick: (String) -> Unit = {},
    onBreadcrumbClick: (String?) -> Unit = {},
    onCreateSubcollectionClick: () -> Unit = {},
    onAddExistingBooks: () -> Unit = {},
    pendingBooksAddedCount: Int? = null,
    onPendingBooksAddedConsumed: () -> Unit = {},
    onEditClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: CollectionViewModel = viewModel()
) {
    val allCollections by viewModel.collections.collectAsState()
    val collection = allCollections.firstOrNull { it.id == collectionId }
        ?: return

    val books = viewModel.getBooksForCollection(collection)
    val children = remember(allCollections, collectionId) {
        allCollections.filter { it.parentId == collectionId }
    }
    val ancestors = remember(allCollections, collectionId) {
        viewModel.ancestorsOf(collectionId)
    }
    val childCountByParent = remember(allCollections) {
        allCollections.groupingBy { it.parentId }.eachCount()
    }
    val moveDestinations = remember(allCollections, collectionId) {
        allCollections.filter { it.id != collectionId }
    }

    var addBooksSheet by remember { mutableStateOf<AddBooksSheet?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun showAddedSnackbar(added: Int) {
        if (added <= 0) return
        scope.launch {
            val message = if (added == 1) {
                context.getString(R.string.books_added_to_collection_one)
            } else {
                context.getString(R.string.books_added_to_collection, added)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(pendingBooksAddedCount) {
        val count = pendingBooksAddedCount ?: return@LaunchedEffect
        showAddedSnackbar(count)
        onPendingBooksAddedConsumed()
    }

    val importer = rememberCollectionBookImporter(
        collectionId = collectionId,
        onBooksAdded = { added ->
            addBooksSheet = null
            showAddedSnackbar(added)
        },
        onImportError = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    )

    LaunchedEffect(importer.isImporting) {
        if (importer.isImporting) {
            addBooksSheet = AddBooksSheet.Importing
        } else if (addBooksSheet == AddBooksSheet.Importing) {
            addBooksSheet = null
        }
    }

    when (addBooksSheet) {
        AddBooksSheet.Options -> AddBooksOptionsDialog(
            onDismiss = { addBooksSheet = null },
            onImportNew = {
                addBooksSheet = null
                importer.launchImport()
            },
            onAddExisting = {
                addBooksSheet = null
                onAddExistingBooks()
            }
        )
        AddBooksSheet.Importing -> ImportingBooksDialog()
        null -> Unit
    }

    CollectionDetailContent(
        collection = collection,
        books = books,
        children = children,
        ancestors = ancestors,
        childCountByParent = childCountByParent,
        moveDestinations = moveDestinations,
        animatedVisibilityScope = animatedVisibilityScope,
        snackbarHostState = snackbarHostState,
        onBookClick = onBookClick,
        onCollectionClick = onCollectionClick,
        onBreadcrumbClick = onBreadcrumbClick,
        onAddBooksClick = { addBooksSheet = AddBooksSheet.Options },
        onCreateSubcollectionClick = onCreateSubcollectionClick,
        onEditClick = onEditClick,
        onBackClick = onBackClick,
        onSiblingOrderChange = { viewModel.updateSiblingOrder(collectionId, it) },
        onBookOrderChange = { ids -> viewModel.updateBookOrder(collectionId, ids) },
        onRemoveBooks = { ids -> viewModel.removeBooksFromCollection(collectionId, ids) },
        onMoveBooks = { ids, toId -> viewModel.moveBooks(ids, collectionId, toId) },
        getBooksForCollection = { viewModel.getBooksForCollection(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.CollectionDetailContent(
    collection: Collection,
    books: List<Book>,
    children: List<Collection>,
    ancestors: List<Collection>,
    childCountByParent: Map<String?, Int>,
    moveDestinations: List<Collection>,
    animatedVisibilityScope: AnimatedVisibilityScope,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onBookClick: (bookId: String, origin: String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onBreadcrumbClick: (String?) -> Unit,
    onAddBooksClick: () -> Unit,
    onCreateSubcollectionClick: () -> Unit,
    onEditClick: () -> Unit,
    onBackClick: () -> Unit,
    onSiblingOrderChange: (List<Collection>) -> Unit = {},
    onBookOrderChange: (List<String>) -> Unit = {},
    onRemoveBooks: (Set<String>) -> Unit = {},
    onMoveBooks: (Set<String>, String) -> Unit = { _, _ -> },
    getBooksForCollection: (Collection) -> List<Book> = { emptyList() },
) {
    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }
    var showMoveDialog by remember { mutableStateOf(false) }

    var localChildren by remember(children) { mutableStateOf(children) }
    var localBooks by remember(books) { mutableStateOf(books) }

    var childDragState by remember { mutableStateOf<DragState?>(null) }
    var dragReadyChildId by remember { mutableStateOf<String?>(null) }
    var bookDragState by remember { mutableStateOf<DragState?>(null) }
    var dragReadyBookId by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val isCompletelyEmpty = localBooks.isEmpty() && localChildren.isEmpty()

    if (showMoveDialog) {
        MoveBooksDialog(
            destinations = moveDestinations,
            onDismiss = { showMoveDialog = false },
            onSelect = { destinationId ->
                onMoveBooks(selectedBookIds, destinationId)
                selectedBookIds = emptySet()
                showMoveDialog = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedBookIds.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.selected_count, selectedBookIds.size),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedBookIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        if (moveDestinations.isNotEmpty()) {
                            IconButton(onClick = { showMoveDialog = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DriveFileMove,
                                    contentDescription = stringResource(R.string.move_books)
                                )
                            }
                        }
                        IconButton(onClick = {
                            onRemoveBooks(selectedBookIds)
                            selectedBookIds = emptySet()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove_from_collection)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = collection.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateSubcollectionClick) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.new_subcollection)
                            )
                        }
                        IconButton(onClick = onEditClick) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_collection)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = padding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                CollectionBreadcrumbs(
                    ancestors = ancestors,
                    currentName = collection.name,
                    onBreadcrumbClick = onBreadcrumbClick
                )
            }

            item {
                CollectionHeader(
                    collection = collection,
                    books = localBooks,
                    childCount = localChildren.size,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            if (isCompletelyEmpty) {
                item {
                    CollectionEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.XLarge),
                        onAddClick = onAddBooksClick,
                        onCreateSubcollectionClick = onCreateSubcollectionClick
                    )
                }
            } else {
                if (localChildren.isNotEmpty()) {
                    item {
                        SectionLabel(
                            text = stringResource(R.string.subcollections),
                            modifier = Modifier.padding(
                                start = Spacing.Large,
                                end = Spacing.Large,
                                top = Spacing.Small,
                                bottom = Spacing.SMedium
                            )
                        )
                    }

                    items(localChildren, key = { "child-${it.id}" }) { child ->
                        val childBooks = getBooksForCollection(child)
                        val isDragging = childDragState?.itemId == child.id
                        val isDragReady = dragReadyChildId == child.id

                        CollectionCard(
                            collection = child,
                            books = childBooks,
                            childCollectionCount = childCountByParent[child.id] ?: 0,
                            onClick = null,
                            modifier = Modifier
                                .padding(horizontal = Spacing.Medium)
                                .then(if (isDragging) Modifier else Modifier.animateItem())
                                .zIndex(if (isDragging) 100f else if (isDragReady) 10f else 0f)
                                .reorderableItemGesture(
                                    itemId = child.id,
                                    isSelectedModeActive = selectedBookIds.isNotEmpty(),
                                    onTap = { onCollectionClick(child.id) },
                                    onLongPress = {},
                                    onDragReady = { id ->
                                        dragReadyChildId = id.ifEmpty { null }
                                    },
                                    onDragStart = { id ->
                                        val initIdx = localChildren.indexOfFirst { it.id == id }
                                        if (initIdx != -1) {
                                            childDragState = DragState(
                                                itemId = id,
                                                initialIndex = initIdx,
                                                currentIndex = initIdx,
                                                pointerOffset = Offset.Zero
                                            )
                                        }
                                        dragReadyChildId = null
                                    },
                                    onDrag = { dragAmount ->
                                        val state = childDragState ?: return@reorderableItemGesture
                                        val newPointerOffset = state.pointerOffset + dragAmount
                                        val itemStepY = with(density) { (100.dp + 8.dp).toPx() }
                                        val indexDelta = (newPointerOffset.y / itemStepY).roundToInt()
                                        val targetIndex = (state.initialIndex + indexDelta)
                                            .coerceIn(0, localChildren.lastIndex)

                                        if (targetIndex != state.currentIndex) {
                                            val updated = localChildren.toMutableList()
                                            val moved = updated.removeAt(state.currentIndex)
                                            updated.add(targetIndex, moved)
                                            localChildren = updated
                                            childDragState = state.copy(
                                                currentIndex = targetIndex,
                                                pointerOffset = newPointerOffset
                                            )
                                        } else {
                                            childDragState = state.copy(pointerOffset = newPointerOffset)
                                        }
                                    },
                                    onDragEnd = {
                                        childDragState = null
                                        dragReadyChildId = null
                                        onSiblingOrderChange(localChildren)
                                    }
                                )
                                .graphicsLayer {
                                    if (isDragging && childDragState != null) {
                                        val state = childDragState!!
                                        val itemStepY = (100.dp + 8.dp).toPx()
                                        val slotShiftY =
                                            (state.currentIndex - state.initialIndex) * itemStepY
                                        translationX = state.pointerOffset.x
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
                                rememberSharedContentState(key = "collection-cover-${child.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(durationMillis = 400) },
                                clipInOverlayDuringTransition = OverlayClip(
                                    RoundedCornerShape(Dimens.CornerCoverInner)
                                )
                            )
                        )
                    }
                }

                if (localBooks.isNotEmpty()) {
                    item {
                        SectionLabel(
                            text = stringResource(R.string.books_in_collection),
                            modifier = Modifier.padding(
                                start = Spacing.Large,
                                end = Spacing.Large,
                                top = Spacing.Medium,
                                bottom = Spacing.SMedium
                            )
                        )
                    }

                    items(localBooks, key = { "book-${it.id}" }) { book ->
                        val isSelected = book.id in selectedBookIds
                        val isDragging = bookDragState?.itemId == book.id
                        val isDragReady = dragReadyBookId == book.id

                        LibraryBookItem(
                            book = book,
                            isSelected = isSelected,
                            onClick = null,
                            modifier = Modifier
                                .then(if (isDragging) Modifier else Modifier.animateItem())
                                .zIndex(if (isDragging) 100f else if (isDragReady) 10f else 0f)
                                .reorderableItemGesture(
                                    itemId = book.id,
                                    isSelectedModeActive = selectedBookIds.isNotEmpty(),
                                    onTap = {
                                        if (selectedBookIds.isNotEmpty()) {
                                            selectedBookIds = if (isSelected) {
                                                selectedBookIds - book.id
                                            } else {
                                                selectedBookIds + book.id
                                            }
                                        } else {
                                            onBookClick(book.id, "collection-detail")
                                        }
                                    },
                                    onLongPress = {
                                        selectedBookIds = selectedBookIds + book.id
                                    },
                                    onDragReady = { id ->
                                        dragReadyBookId = id.ifEmpty { null }
                                    },
                                    onDragStart = { id ->
                                        val initIdx = localBooks.indexOfFirst { it.id == id }
                                        if (initIdx != -1) {
                                            bookDragState = DragState(
                                                itemId = id,
                                                initialIndex = initIdx,
                                                currentIndex = initIdx,
                                                pointerOffset = Offset.Zero
                                            )
                                        }
                                        dragReadyBookId = null
                                    },
                                    onDrag = { dragAmount ->
                                        val state = bookDragState ?: return@reorderableItemGesture
                                        val newPointerOffset = state.pointerOffset + dragAmount
                                        val itemStepY = with(density) { (140.dp + 4.dp).toPx() }
                                        val indexDelta = (newPointerOffset.y / itemStepY).roundToInt()
                                        val targetIndex = (state.initialIndex + indexDelta)
                                            .coerceIn(0, localBooks.lastIndex)

                                        if (targetIndex != state.currentIndex) {
                                            val updated = localBooks.toMutableList()
                                            val moved = updated.removeAt(state.currentIndex)
                                            updated.add(targetIndex, moved)
                                            localBooks = updated
                                            bookDragState = state.copy(
                                                currentIndex = targetIndex,
                                                pointerOffset = newPointerOffset
                                            )
                                        } else {
                                            bookDragState = state.copy(pointerOffset = newPointerOffset)
                                        }
                                    },
                                    onDragEnd = {
                                        bookDragState = null
                                        dragReadyBookId = null
                                        onBookOrderChange(localBooks.map { it.id })
                                    }
                                )
                                .graphicsLayer {
                                    if (isDragging && bookDragState != null) {
                                        val state = bookDragState!!
                                        val itemStepY = (140.dp + 4.dp).toPx()
                                        val slotShiftY =
                                            (state.currentIndex - state.initialIndex) * itemStepY
                                        translationX = state.pointerOffset.x
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
                                rememberSharedContentState(key = "collection-detail-cover-${book.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(durationMillis = 400) },
                                clipInOverlayDuringTransition = OverlayClip(
                                    RoundedCornerShape(Dimens.CornerCard)
                                )
                            )
                        )
                    }
                } else if (localChildren.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = onAddBooksClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.Large)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_books))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionBreadcrumbs(
    ancestors: List<Collection>,
    currentName: String,
    onBreadcrumbClick: (String?) -> Unit,
) {
    val separator = stringResource(R.string.breadcrumb_separator)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Large, vertical = Spacing.SMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.library_root),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onBreadcrumbClick(null) }
        )

        ancestors.forEach { ancestor ->
            Text(
                text = separator,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ancestor.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onBreadcrumbClick(ancestor.id) }
            )
        }

        Text(
            text = separator,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = currentName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
private fun MoveBooksDialog(
    destinations: List<Collection>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_destination)) },
        text = {
            if (destinations.isEmpty()) {
                Text(stringResource(R.string.no_collections_yet))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(destinations, key = { it.id }) { destination ->
                        Text(
                            text = destination.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(destination.id) }
                                .padding(vertical = Spacing.SMedium, horizontal = Spacing.Small)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SharedTransitionScope.CollectionHeader(
    collection: Collection,
    books: List<Book>,
    childCount: Int,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val effectiveCoverUri = getEffectiveCollectionCover(collection, books)
    val booksLabel = if (books.size == 1) {
        stringResource(R.string.book_count_one)
    } else {
        stringResource(R.string.books_count_label, books.size)
    }
    val countText = if (childCount > 0) {
        val collectionsLabel = if (childCount == 1) {
            stringResource(R.string.collection_count_one)
        } else {
            stringResource(R.string.collections_count_label, childCount)
        }
        stringResource(R.string.collection_card_meta_books_and_subs, booksLabel, collectionsLabel)
    } else {
        booksLabel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        CollectionCoverThumbnail(
            coverUri = effectiveCoverUri,
            books = books.take(3),
            modifier = Modifier.size(width = 180.dp, height = 240.dp),
            frontCoverModifier = Modifier.sharedElement(
                rememberSharedContentState(key = "collection-cover-${collection.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 400) },
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(Dimens.CornerCoverInner))
            )
        )

        if (collection.description.isNotBlank()) {
            Text(
                text = collection.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        SuggestionChip(
            onClick = {},
            label = { Text(countText, style = MaterialTheme.typography.labelMedium) }
        )

        HorizontalDivider(modifier = Modifier.padding(top = Spacing.Small))
    }
}

@Composable
private fun CollectionEmptyState(
    onAddClick: () -> Unit,
    onCreateSubcollectionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            stringResource(R.string.collection_empty_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.collection_empty_subtitle),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_books), style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCreateSubcollectionClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.new_subcollection),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// ---- Previews ----

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, device = "id:pixel_7_pro")
@Composable
fun CollectionDetailPopulatedPreview() {
    LibraryTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                CollectionDetailContent(
                    collection = Collection(
                        "c1", "Craft & Engineering",
                        "Books about writing better code",
                        listOf("1", "2", "3")
                    ),
                    books = listOf(
                        Book("1", "Clean Code", "Robert C. Martin", progress = 0.45f),
                        Book("2", "The Pragmatic Programmer", "Andy Hunt", progress = 0.20f),
                        Book("3", "Kotlin in Action", "Dmitry Jemerov", progress = 0.75f)
                    ),
                    children = listOf(
                        Collection("c2", "Architecture", parentId = "c1")
                    ),
                    ancestors = emptyList(),
                    childCountByParent = mapOf("c1" to 1),
                    moveDestinations = emptyList(),
                    animatedVisibilityScope = this,
                    onBookClick = { _, _ -> },
                    onCollectionClick = {},
                    onBreadcrumbClick = {},
                    onAddBooksClick = {},
                    onCreateSubcollectionClick = {},
                    onEditClick = {},
                    onBackClick = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, device = "id:pixel_7_pro")
@Composable
fun CollectionDetailEmptyPreview() {
    LibraryTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                CollectionDetailContent(
                    collection = Collection("c3", "To Read", "", emptyList()),
                    books = emptyList(),
                    children = emptyList(),
                    ancestors = emptyList(),
                    childCountByParent = emptyMap(),
                    moveDestinations = emptyList(),
                    animatedVisibilityScope = this,
                    onBookClick = { _, _ -> },
                    onCollectionClick = {},
                    onBreadcrumbClick = {},
                    onAddBooksClick = {},
                    onCreateSubcollectionClick = {},
                    onEditClick = {},
                    onBackClick = {}
                )
            }
        }
    }
}
