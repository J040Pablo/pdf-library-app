package com.example.library.screens.collectiondetail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.library.R
import com.example.library.data.BookFiles
import com.example.library.data.BookRepository
import com.example.library.data.CollectionRepository
import com.example.library.data.LibraryImporter
import com.example.library.model.Book
import com.example.library.ui.components.BookCoverPlaceholder
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Spacing
import kotlinx.coroutines.launch

enum class AddBooksSheet {
    Options,
    Importing
}

@Composable
fun AddBooksOptionsDialog(
    onDismiss: () -> Unit,
    onImportNew: () -> Unit,
    onAddExisting: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_books_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onImportNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_new_book))
                }
                TextButton(
                    onClick = onAddExisting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_existing_book))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExistingBooksDialog(
    availableBooks: List<Book>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(availableBooks, searchQuery) {
        if (searchQuery.isBlank()) availableBooks
        else availableBooks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.author.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Medium),
            shape = RoundedCornerShape(Dimens.CornerCardLarge),
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.add_existing_book),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { onConfirm(selectedIds.toList()) },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.add_selected_books))
                        }
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_books)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large)
                        .padding(bottom = Spacing.SMedium),
                    shape = RoundedCornerShape(Dimens.CornerCardLarge)
                )

                if (availableBooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_books_available_to_add),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { book ->
                            val isSelected = book.id in selectedIds
                            ExistingBookPickerRow(
                                book = book,
                                isSelected = isSelected,
                                onClick = {
                                    selectedIds = if (isSelected) selectedIds - book.id
                                    else selectedIds + book.id
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExistingBookPickerRow(
    book: Book,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Large, vertical = Spacing.SMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BookCoverPlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun ImportingBooksDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.importing_into_collection)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {}
    )
}

/**
 * Hosts file pickers and import logic for adding books into [collectionId].
 */
@Composable
fun rememberCollectionBookImporter(
    collectionId: String,
    onBooksAdded: (addedCount: Int) -> Unit,
    onImportError: (String) -> Unit,
): CollectionBookImporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    val onBooksAddedState = rememberUpdatedState(onBooksAdded)
    val onImportErrorState = rememberUpdatedState(onImportError)
    val collectionIdState = rememberUpdatedState(collectionId)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                val knownHashes = BookRepository.books.value
                    .mapNotNull { it.contentHash }
                    .toMutableSet()
                val addedIds = mutableListOf<String>()
                for (uri in uris) {
                    val name = getDisplayName(context, uri) ?: "document.pdf"
                    if (!BookFiles.isSupportedImportName(name)) continue
                    when (val result = LibraryImporter.import(context, uri, name, knownHashes)) {
                        is LibraryImporter.ImportResult.Ok -> {
                            BookRepository.addBook(result.book)
                            result.book.contentHash?.let { knownHashes.add(it) }
                            addedIds.add(result.book.id)
                        }
                        is LibraryImporter.ImportResult.Duplicate -> {
                            BookRepository.books.value
                                .firstOrNull { it.title == result.existingTitle }
                                ?.id
                                ?.let { addedIds.add(it) }
                        }
                        is LibraryImporter.ImportResult.Err -> {
                            onImportErrorState.value(result.message)
                        }
                    }
                }
                if (addedIds.isNotEmpty()) {
                    val added = CollectionRepository.addBooksToCollection(
                        collectionIdState.value,
                        addedIds
                    )
                    onBooksAddedState.value(added)
                } else {
                    onBooksAddedState.value(0)
                }
            } finally {
                isImporting = false
            }
        }
    }

    return CollectionBookImporter(
        isImporting = isImporting,
        launchImport = {
            launcher.launch(
                arrayOf(
                    "application/pdf",
                    "application/x-cbr",
                    "application/vnd.comicbook-rar",
                    "application/x-rar-compressed",
                    "application/octet-stream"
                )
            )
        }
    )
}

data class CollectionBookImporter(
    val isImporting: Boolean,
    val launchImport: () -> Unit,
)

private fun getDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
    return uri.lastPathSegment
}
