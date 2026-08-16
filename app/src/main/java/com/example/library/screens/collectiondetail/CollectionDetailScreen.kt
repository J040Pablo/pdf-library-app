package com.example.library.screens.collectiondetail

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.model.Book
import com.example.library.model.Collection
import com.example.library.ui.components.LibraryBookItem
import com.example.library.ui.components.CollectionCoverThumbnail
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.CollectionViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CollectionDetailScreen(
    collectionId: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBookClick: (bookId: String, origin: String) -> Unit,
    onAddBooksClick: () -> Unit,
    onEditClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: CollectionViewModel = viewModel()
) {
    val allCollections by viewModel.collections.collectAsState()
    val collection = allCollections.firstOrNull { it.id == collectionId }
        ?: return // Shouldn't happen; guard against race

    val books = viewModel.getBooksForCollection(collection)

    CollectionDetailContent(
        collection = collection,
        books = books,
        animatedVisibilityScope = animatedVisibilityScope,
        onBookClick = onBookClick,
        onAddBooksClick = onAddBooksClick,
        onEditClick = onEditClick,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.CollectionDetailContent(
    collection: Collection,
    books: List<Book>,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBookClick: (bookId: String, origin: String) -> Unit,
    onAddBooksClick: () -> Unit,
    onEditClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            if (selectedBookIds.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedBookIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedBookIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // Example action for removing books from this collection
                            selectedBookIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Collection")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },

    ) { padding ->
        if (books.isEmpty()) {
            CollectionEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddClick = onAddBooksClick
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header: stacked cover + description
                item {
                    CollectionHeader(collection = collection, books = books)
                }

                items(books, key = { it.id }) { book ->
                    LibraryBookItem(
                        book = book,
                        isSelected = book.id in selectedBookIds,
                        onClick = {
                            if (selectedBookIds.isNotEmpty()) {
                                selectedBookIds = if (book.id in selectedBookIds) {
                                    selectedBookIds - book.id
                                } else {
                                    selectedBookIds + book.id
                                }
                            } else {
                                onBookClick(book.id, "collection-detail")
                            }
                        },
                        onLongClick = {
                            selectedBookIds = selectedBookIds + book.id
                        },
                        coverModifier = Modifier.sharedElement(
                            rememberSharedContentState(key = "collection-detail-cover-${book.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(collection: Collection, books: List<Book>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Show custom cover if set, otherwise stacked book thumbnails
        CollectionCoverThumbnail(
            coverUri = collection.coverUri,
            books = books.take(2),
            modifier = Modifier.size(width = 180.dp, height = 240.dp)
        )

        if (collection.description.isNotBlank()) {
            Text(
                text = collection.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        val countText = when (books.size) {
            1 -> "1 book"
            else -> "${books.size} books"
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
            "This collection is empty",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add books from your library to get started",
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
            Text("Add Books", style = MaterialTheme.typography.titleMedium)
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
                        Book("3", "Kotlin in Action", "Dmitry Jemerov", progress = 0.75f),
                        Book("4", "Refactoring", "Martin Fowler", progress = 0.60f),
                        Book("5", "Design Patterns", "Gang of Four", progress = 0.10f)
                    ),
                    animatedVisibilityScope = this,
                    onBookClick = { _, _ -> },
                    onAddBooksClick = {},
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
                    animatedVisibilityScope = this,
                    onBookClick = { _, _ -> },
                    onAddBooksClick = {},
                    onEditClick = {},
                    onBackClick = {}
                )
            }
        }
    }
}
