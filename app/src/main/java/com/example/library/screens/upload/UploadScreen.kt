package com.example.library.screens.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.R
import com.example.library.ui.components.ReadingIllustration
import com.example.library.viewmodel.ImportItemResult
import com.example.library.viewmodel.ImportItemStatus
import com.example.library.viewmodel.ImportPhase
import com.example.library.viewmodel.UploadState
import com.example.library.viewmodel.UploadViewModel

@Composable
fun UploadScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: UploadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val files = uris.map { uri -> uri to getFileName(context, uri) }
        viewModel.onFilesSelected(files)
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onZipSelected(it, getFileName(context, it))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "upload_state"
        ) { state ->
            when (state) {
                is UploadState.Empty -> EmptyStateView(
                    onAddPdfs = {
                        pdfLauncher.launch(arrayOf("application/pdf"))
                    },
                    onAddZip = {
                        zipLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream"
                            )
                        )
                    }
                )
                is UploadState.Progress -> ProgressView(state)
                is UploadState.BatchComplete -> BatchCompleteView(state.results) {
                    viewModel.resetState()
                }
                is UploadState.Error -> ErrorView(state.message) {
                    viewModel.resetState()
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    onAddPdfs: () -> Unit,
    onAddZip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ReadingIllustration(modifier = Modifier.padding(bottom = 32.dp))

        Text(
            text = stringResource(R.string.add_first_book),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.import_pdf_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAddPdfs,
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.85f),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.LibraryAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_pdfs))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onAddZip,
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.85f),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.FolderZip, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_zip))
        }
    }
}

@Composable
fun ProgressView(state: UploadState.Progress) {
    val phaseText = when (state.phase) {
        ImportPhase.Extracting -> stringResource(R.string.extracting_zip)
        ImportPhase.Importing -> if (state.total > 0) {
            stringResource(R.string.importing_progress, state.current, state.total)
        } else {
            stringResource(R.string.importing_pdf)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = phaseText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (state.currentFileName.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.currentFileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.total > 0) {
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { state.current.toFloat() / state.total.toFloat() },
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }
    }
}

@Composable
fun BatchCompleteView(
    results: List<ImportItemResult>,
    onDone: () -> Unit
) {
    val successCount = results.count { it.status == ImportItemStatus.Success }
    val duplicateCount = results.count { it.status == ImportItemStatus.Duplicate }
    val failedCount = results.count { it.status == ImportItemStatus.Failed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.import_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.import_complete_summary,
                successCount,
                duplicateCount,
                failedCount
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { "${it.fileName}-${it.status}-${it.message}" }) { item ->
                ImportResultRow(item)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text(stringResource(R.string.done))
        }
    }
}

@Composable
private fun ImportResultRow(item: ImportItemResult) {
    val (icon, tint) = when (item.status) {
        ImportItemStatus.Success -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        ImportItemStatus.Duplicate -> Icons.Default.Info to MaterialTheme.colorScheme.tertiary
        ImportItemStatus.Failed -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ErrorView(message: String, onTryAgain: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.oops),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onTryAgain) {
            Text(stringResource(R.string.try_again))
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
