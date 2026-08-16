package com.example.library.screens.createcollection

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import com.example.library.model.Collection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.library.model.Book
import com.example.library.ui.components.BookCoverPlaceholder
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.LibraryTheme
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.CollectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCollectionScreen(
    collectionId: String? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CollectionViewModel = viewModel()
) {
    val allBooks by viewModel.allBooks.collectAsState()
    val collectionToEdit = collectionId?.let { viewModel.getCollectionById(it) }

    CreateCollectionContent(
        allBooks = allBooks,
        collectionToEdit = collectionToEdit,
        onSave = { name, description, selectedIds, coverUri ->
            if (collectionToEdit != null) {
                viewModel.updateCollection(collectionToEdit.id, name, description, selectedIds, coverUri)
            } else {
                viewModel.createCollection(name, description, selectedIds, coverUri)
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
    onSave: (name: String, description: String, selectedBookIds: List<String>, coverUri: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(collectionToEdit) { mutableStateOf(collectionToEdit?.name ?: "") }
    var description by remember(collectionToEdit) { mutableStateOf(collectionToEdit?.description ?: "") }
    var selectedIds by remember(collectionToEdit) { mutableStateOf((collectionToEdit?.bookIds ?: emptyList()).toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var coverUri by remember(collectionToEdit) { mutableStateOf<String?>(collectionToEdit?.coverUri) }

    // Image picker for collection cover
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        coverUri = uri?.toString()
    }

    // PDF picker — reuses the same approach as LibraryScreen's import
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        // In a real implementation this would import the PDF into BookRepository
        // and auto-select it. Hook is wired for future completion.
    }

    val filteredBooks = remember(allBooks, searchQuery) {
        if (searchQuery.isBlank()) allBooks
        else allBooks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.author.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (collectionToEdit != null) "Edit Collection" else "New Collection",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
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
                            "Save",
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
                bottom = scaffoldPadding.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ---- Cover + Name + Description ----
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    // Cover picker
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
                        label = { Text("Collection name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    )
                }
            }



            // ---- Section header ----
            item {
                SectionHeader(
                    title = "Add from library",
                    modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                ) {
                    OutlinedButton(
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(Dimens.CornerCard)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import PDF")
                    }
                }
            }

            // ---- Search bar ----
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search books…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large)
                        .padding(bottom = Spacing.SMedium),
                    shape = RoundedCornerShape(Dimens.CornerCardLarge)
                )
            }

            // ---- Selection summary banner ----
            item {
                AnimatedVisibility(
                    visible = selectedIds.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.SMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedIds.size} book${if (selectedIds.size == 1) "" else "s"} selected",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { selectedIds = emptySet() }) {
                                Text("Clear all")
                            }
                        }
                    }
                }
            }

            // ---- Book pick-list ----
            if (filteredBooks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.XLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No books found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredBooks, key = { it.id }) { book ->
                    val isSelected = book.id in selectedIds
                    BookPickerItem(
                        book = book,
                        isSelected = isSelected,
                        onClick = {
                            selectedIds = if (isSelected) selectedIds - book.id
                            else selectedIds + book.id
                        }
                    )
                }
            }
        }
    }
}

// ---- Cover picker field ----

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
        // Preview box
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
                    contentDescription = "Collection cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Add cover",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        // Action column
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickImage,
                shape = RoundedCornerShape(Dimens.CornerCard)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (coverUri == null) "Add Cover" else "Change Cover")
            }
            if (coverUri != null) {
                TextButton(onClick = onClearImage) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---- Helpers ----

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

@Composable
private fun BookPickerItem(
    book: Book,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Large, vertical = Spacing.SMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (isSelected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(6.dp)
                    ) else Modifier
                )
        ) {
            BookCoverPlaceholder(
                title = book.title,
                modifier = Modifier.fillMaxSize()
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
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
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ---- Previews ----

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
