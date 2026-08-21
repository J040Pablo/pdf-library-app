package com.example.library.screens.editbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.R
import com.example.library.model.Book
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Spacing
import com.example.library.viewmodel.BookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    bookId: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    viewModel: BookViewModel = viewModel()
) {
    val allBooks by viewModel.allBooks.collectAsState()
    val book = allBooks.firstOrNull { it.id == bookId } ?: return

    EditBookContent(
        book = book,
        onSave = { updatedBook ->
            viewModel.updateBook(updatedBook)
            onSave()
        },
        onCancel = onCancel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBookContent(
    book: Book,
    onSave: (Book) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var rating by remember(book.id) { mutableFloatStateOf(book.rating) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.edit_book),
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
                            onSave(
                                book.copy(
                                    title = title.trim(),
                                    author = author.trim(),
                                    rating = rating
                                )
                            )
                        },
                        enabled = title.isNotBlank()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(horizontal = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            Spacer(modifier = Modifier.height(Spacing.Medium))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.book_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.CornerCard),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.CornerCard),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Star rating selector
            Text(
                text = stringResource(R.string.rating),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(5) { index ->
                    val starValue = index + 1
                    IconButton(
                        onClick = {
                            // Tap same star again → clear rating
                            rating = if (rating == starValue.toFloat()) 0f else starValue.toFloat()
                        }
                    ) {
                        Icon(
                            imageVector = if (index < rating.toInt()) Icons.Default.Star
                                          else Icons.Default.StarBorder,
                            contentDescription = stringResource(
                                R.string.stars_content_description,
                                starValue
                            ),
                            tint = if (index < rating.toInt()) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.Small))

                if (rating > 0f) {
                    Text(
                        text = "${rating.toInt()}/5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
