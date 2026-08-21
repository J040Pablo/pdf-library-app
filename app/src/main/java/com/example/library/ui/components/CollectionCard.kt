package com.example.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.example.library.R
import com.example.library.model.Book
import com.example.library.model.Collection
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing

fun getEffectiveCollectionCover(
    collection: Collection,
    books: List<Book>
): String? {
    val firstBook = collection.bookIds
        .firstOrNull()
        ?.let { id -> books.firstOrNull { it.id == id } }

    return firstBook?.coverUrl
        ?: collection.coverUri
}

/**
 * A card that represents a single Collection in the library list.
 *
 * When the collection has a user-selected [Collection.coverUri] it is shown
 * as the card's leading image. Otherwise the stacked-book placeholder is used.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionCard(
    collection: Collection,
    books: List<Book>,
    childCollectionCount: Int = 0,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier
) {
    val bookCount = collection.bookIds.size
    val booksLabel = if (bookCount == 1) {
        stringResource(R.string.book_count_one)
    } else {
        stringResource(R.string.books_count_label, bookCount)
    }
    val metaText = if (childCollectionCount > 0) {
        val collectionsLabel = if (childCollectionCount == 1) {
            stringResource(R.string.collection_count_one)
        } else {
            stringResource(R.string.collections_count_label, childCollectionCount)
        }
        stringResource(R.string.collection_card_meta_books_and_subs, booksLabel, collectionsLabel)
    } else {
        booksLabel
    }

    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surfaceContainerHigh

    val clickModifier = if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(Dimens.CornerCardLarge)
                ) else Modifier
            ),
        shape = RoundedCornerShape(Dimens.CornerCardLarge),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ) else null
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.Medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            val effectiveCoverUri = getEffectiveCollectionCover(collection, books)

            CollectionCoverThumbnail(
                coverUri = effectiveCoverUri,
                books = books.take(3),
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
                frontCoverModifier = coverModifier
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (collection.description.isNotBlank()) {
                    Text(
                        text = collection.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Thumbnail used in [CollectionCard].
 * Shows real books of the collection stacked in depth BEHIND the Collection Cover (frontmost).
 */
@Composable
fun CollectionCoverThumbnail(
    coverUri: String?,
    books: List<Book>,
    modifier: Modifier = Modifier,
    frontCoverModifier: Modifier = Modifier
) {
    val realBooks = books.take(3)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        // Render depth stack BEHIND Collection Cover (rendered in reverse order: Book 3, Book 2, Book 1)
        realBooks.forEachIndexed { index, _ ->
            val reverseIndex = realBooks.size - 1 - index
            val stackedBook = realBooks[reverseIndex]

            val step = reverseIndex + 1
            val offsetX = (step * 8).dp
            val offsetY = (-step * 6).dp
            val scale = 1f - (step * 0.05f)

            Surface(
                modifier = Modifier
                    .fillMaxSize(0.80f)
                    .align(Alignment.BottomStart)
                    .offset(x = offsetX, y = offsetY)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(Dimens.CornerCoverInner)),
                tonalElevation = (4 - step).dp,
                shadowElevation = (3 - step).dp.coerceAtLeast(1.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (stackedBook.coverUrl != null) {
                    AsyncImage(
                        model = stackedBook.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BookCoverPlaceholder(
                        title = stackedBook.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // FRONTMOST ELEMENT: Collection Cover (Rendered LAST so it sits on top)
        Surface(
            modifier = Modifier
                .fillMaxSize(0.82f)
                .align(Alignment.BottomStart)
                .then(frontCoverModifier)
                .clip(RoundedCornerShape(Dimens.CornerCoverInner)),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "Collection cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CollectionCardPreview() {
    LibraryTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectionCard(
                collection = Collection("c1", "Craft & Engineering", "Books about writing better code", listOf("1", "2")),
                books = listOf(
                    Book("1", "Clean Code", "Robert C. Martin"),
                    Book("2", "Pragmatic Programmer", "Andy Hunt")
                ),
                onClick = {}
            )
            CollectionCard(
                collection = Collection("c3", "To Read", "", emptyList()),
                books = emptyList(),
                onClick = {}
            )
        }
    }
}
