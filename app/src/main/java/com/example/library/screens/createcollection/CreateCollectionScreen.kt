package com.example.library.screens.createcollection

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.library.R
import com.example.library.data.BookFiles
import com.example.library.data.BookRepository
import com.example.library.data.LibraryImporter
import com.example.library.model.Book
import com.example.library.model.Collection
import com.example.library.screens.bookpicker.CollectionShelfPreview
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.CollectionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCollectionScreen(
    collectionId: String? = null,
    parentId: String? = null,
    incomingSelectedIds: List<String>? = null,
    onIncomingSelectedConsumed: () -> Unit = {},
    onBrowseLibrary: (currentSelectedIds: Set<String>) -> Unit = {},
    onSave: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CollectionViewModel = viewModel()
) {
    val allBooks by viewModel.allBooks.collectAsState()
    val collectionToEdit = collectionId?.let { viewModel.getCollectionById(it) }

    CreateCollectionContent(
        allBooks = allBooks,
        collectionToEdit = collectionToEdit,
        isSubcollection = parentId != null && collectionToEdit == null,
        incomingSelectedIds = incomingSelectedIds,
        onIncomingSelectedConsumed = onIncomingSelectedConsumed,
        onBrowseLibrary = onBrowseLibrary,
        onSave = { name, description, selectedIds, coverUri ->
            if (collectionToEdit != null) {
                viewModel.updateCollection(collectionToEdit.id, name, description, selectedIds, coverUri)
            } else {
                viewModel.createCollection(name, description, selectedIds, coverUri, parentId = parentId)
            }
            onSave()
        },
        onCancel = onCancel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateCollectionContent(
    allBooks: List<Book>,
    collectionToEdit: Collection? = null,
    isSubcollection: Boolean = false,
    incomingSelectedIds: List<String>? = null,
    onIncomingSelectedConsumed: () -> Unit = {},
    onBrowseLibrary: (currentSelectedIds: Set<String>) -> Unit = {},
    onSave: (name: String, description: String, selectedBookIds: List<String>, coverUri: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(collectionToEdit) { mutableStateOf(collectionToEdit?.name ?: "") }
    var description by remember(collectionToEdit) { mutableStateOf(collectionToEdit?.description ?: "") }
    var selectedIds by remember(collectionToEdit) { mutableStateOf((collectionToEdit?.bookIds ?: emptyList()).toSet()) }
    var coverUri by remember(collectionToEdit) { mutableStateOf<String?>(collectionToEdit?.coverUri) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(incomingSelectedIds) {
        if (incomingSelectedIds != null) {
            selectedIds = incomingSelectedIds.toSet()
            onIncomingSelectedConsumed()
        }
    }

    val selectedBooks = remember(allBooks, selectedIds) {
        allBooks.filter { it.id in selectedIds }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        coverUri = uri?.toString()
    }

    val bookImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                val knownHashes = BookRepository.books.value
                    .mapNotNull { it.contentHash }
                    .toMutableSet()
                for (uri in uris) {
                    val displayName = getCreateDisplayName(context, uri) ?: "document.pdf"
                    if (!BookFiles.isSupportedImportName(displayName)) continue
                    when (val result = LibraryImporter.import(context, uri, displayName, knownHashes)) {
                        is LibraryImporter.ImportResult.Ok -> {
                            BookRepository.addBook(result.book)
                            result.book.contentHash?.let { knownHashes.add(it) }
                            selectedIds = selectedIds + result.book.id
                        }
                        is LibraryImporter.ImportResult.Duplicate -> {
                            BookRepository.books.value
                                .firstOrNull { it.title == result.existingTitle }
                                ?.id
                                ?.let { selectedIds = selectedIds + it }
                        }
                        is LibraryImporter.ImportResult.Err -> Unit
                    }
                }
            } finally {
                isImporting = false
            }
        }
    }

    if (isImporting) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            collectionToEdit != null -> stringResource(R.string.edit_collection)
                            isSubcollection -> stringResource(R.string.new_subcollection)
                            else -> stringResource(R.string.new_collection)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
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
                    TextButton(
                        onClick = {
                            onSave(name.trim(), description.trim(), selectedIds.toList(), coverUri)
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text(
                            stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = scaffoldPadding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    CoverPickerField(
                        coverUri = coverUri,
                        onPickImage = {
                            coverPickerLauncher.launch(arrayOf("image/*"))
                        },
                        onClearImage = { coverUri = null }
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.collection_name_required)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.description_optional)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    )

                    CollectionShelfPreview(
                        books = selectedBooks,
                        collectionName = name,
                        modifier = Modifier.padding(top = Spacing.Small)
                    )
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.build_your_shelf),
                    modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                ) {
                    OutlinedButton(
                        onClick = {
                            bookImportLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/x-cbr",
                                    "application/vnd.comicbook-rar",
                                    "application/x-rar-compressed",
                                    "application/octet-stream"
                                )
                            )
                        },
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.import_pdf))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.build_your_shelf_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.Large)
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Button(
                    onClick = { onBrowseLibrary(selectedIds) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    shape = RoundedCornerShape(Dimens.CornerCard)
                ) {
                    Icon(Icons.Default.AutoStories, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.book_picker_browse))
                }
                Spacer(modifier = Modifier.height(Spacing.Large))
            }
        }
    }
}

@Composable
private fun CoverPickerField(
    coverUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.CornerCard)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 110.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape
                )
                .clickable(onClick = onPickImage),
            contentAlignment = Alignment.Center
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = stringResource(R.string.collection_cover),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = stringResource(R.string.add_cover),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickImage,
                shape = RoundedCornerShape(Dimens.CornerCard)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (coverUri == null) stringResource(R.string.add_cover)
                    else stringResource(R.string.change_cover)
                )
            }
            if (coverUri != null) {
                TextButton(onClick = onClearImage) {
                    Text(
                        stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        trailing()
    }
}

@Preview(showBackground = true, device = "id:pixel_7_pro")
@Composable
fun CreateCollectionEmptyPreview() {
    LibraryTheme {
        CreateCollectionContent(
            allBooks = listOf(
                Book("1", "Clean Code", "Robert C. Martin", progress = 0.45f),
                Book("2", "The Pragmatic Programmer", "Andy Hunt", progress = 0.20f),
                Book("3", "Kotlin in Action", "Dmitry Jemerov", progress = 0.75f),
                Book("4", "Jetpack Compose Essentials", "Neil Smyth", progress = 0.10f),
                Book("5", "Refactoring", "Martin Fowler", progress = 0.60f)
            ),
            collectionToEdit = null,
            onSave = { _, _, _, _ -> },
            onCancel = {}
        )
    }
}

private fun getCreateDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
    return uri.lastPathSegment
}
