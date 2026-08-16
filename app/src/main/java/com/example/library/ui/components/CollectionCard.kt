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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import coil.compose.AsyncImage
import com.example.library.model.Book
import com.example.library.model.Collection
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing

/**
 * A card that represents a single Collection in the library list.
 *
 * When the collection has a user-selected [Collection.coverUri] it is shown
 * as the card's leading image.  Otherwise the stacked-book placeholder is used.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionCard(
    collection: Collection,
    books: List<Book>,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bookCount = collection.bookIds.size

    val backgroundColor = if (isSelected) 
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else 
        MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            // Cover: custom image if set, otherwise stacked placeholder
            CollectionCoverThumbnail(
                coverUri = collection.coverUri,
                books = books.take(2),
                modifier = Modifier.size(width = 72.dp, height = 96.dp)
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
                    text = if (bookCount == 1) "1 book" else "$bookCount books",
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
 * Shows [coverUri] when available, falling back to [StackedCoverPreview].
 */
@Composable
fun CollectionCoverThumbnail(
    coverUri: String?,
    books: List<Book>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = "Collection cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.CornerCoverInner))
            )
        } else {
            StackedCoverPreview(books = books, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Shows up to 2 book covers stacked with a slight offset to convey
 * "folder of books" without a new image system.
 */
@Composable
fun StackedCoverPreview(
    books: List<Book>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (books.size >= 2) {
            BookCoverPlaceholder(
                title = books[1].title,
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .offset(x = 14.dp, y = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }
        val frontBook = books.firstOrNull()
        if (frontBook != null) {
            BookCoverPlaceholder(
                title = frontBook.title,
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
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
