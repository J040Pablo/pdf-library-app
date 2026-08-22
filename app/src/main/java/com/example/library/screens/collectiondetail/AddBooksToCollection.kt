package com.example.library.screens.collectiondetail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.library.R
import com.example.library.data.BookFiles
import com.example.library.data.BookRepository
import com.example.library.data.CollectionRepository
import com.example.library.data.LibraryImporter
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
