package com.example.library.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.R
import com.example.library.data.BookFiles
import com.example.library.data.BookRepository
import com.example.library.data.LibraryImporter
import com.example.library.data.ZipPdfImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ImportItemStatus {
    Success,
    Duplicate,
    Failed
}

data class ImportItemResult(
    val fileName: String,
    val status: ImportItemStatus,
    val message: String
)

enum class ImportPhase {
    Extracting,
    Importing
}

sealed class UploadState {
    object Empty : UploadState()
    data class Progress(
        val phase: ImportPhase,
        val current: Int,
        val total: Int,
        val currentFileName: String
    ) : UploadState()
    data class BatchComplete(val results: List<ImportItemResult>) : UploadState()
    data class Error(val message: String) : UploadState()
}

/**
 * Drives the Upload tab: multi-PDF/CBR and ZIP import with per-file results.
 */
class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UploadState>(UploadState.Empty)
    val uiState: StateFlow<UploadState> = _uiState.asStateFlow()

    fun onFilesSelected(files: List<Pair<Uri, String?>>) {
        if (files.isEmpty()) return

        val bookFiles = files.mapNotNull { (uri, name) ->
            val fileName = name?.takeIf { it.isNotBlank() } ?: "document.pdf"
            if (BookFiles.isSupportedImportName(fileName)) uri to fileName else null
        }

        if (bookFiles.isEmpty()) {
            _uiState.value = UploadState.Error(
                getApplication<Application>().getString(R.string.invalid_book_file)
            )
            return
        }

        viewModelScope.launch {
            importUriList(bookFiles)
        }
    }

    fun onZipSelected(uri: Uri?, fileName: String?) {
        if (uri == null) {
            _uiState.value = UploadState.Error(
                getApplication<Application>().getString(R.string.invalid_zip_file)
            )
            return
        }

        val name = fileName.orEmpty()
        if (name.isNotBlank() && !name.lowercase().endsWith(".zip")) {
            _uiState.value = UploadState.Error(
                getApplication<Application>().getString(R.string.invalid_zip_file)
            )
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            _uiState.value = UploadState.Progress(
                phase = ImportPhase.Extracting,
                current = 0,
                total = 0,
                currentFileName = name.ifBlank {
                    context.getString(R.string.importing_zip)
                }
            )

            when (val extracted = ZipPdfImporter.extractBooks(context, uri)) {
                is ZipPdfImporter.ExtractResult.Err -> {
                    _uiState.value = UploadState.Error(extracted.message)
                }
                is ZipPdfImporter.ExtractResult.Ok -> {
                    try {
                        val pairs = extracted.books.map { it.file to it.displayName }
                        importLocalFiles(pairs)
                    } finally {
                        extracted.workDir.deleteRecursively()
                    }
                }
            }
        }
    }

    private suspend fun importUriList(files: List<Pair<Uri, String>>) {
        val context = getApplication<Application>().applicationContext
        val results = mutableListOf<ImportItemResult>()
        val knownHashes = BookRepository.books.value
            .mapNotNull { it.contentHash }
            .toMutableSet()

        files.forEachIndexed { index, (uri, fileName) ->
            _uiState.value = UploadState.Progress(
                phase = ImportPhase.Importing,
                current = index + 1,
                total = files.size,
                currentFileName = fileName
            )

            when (val result = LibraryImporter.import(context, uri, fileName, knownHashes)) {
                is LibraryImporter.ImportResult.Ok -> {
                    BookRepository.addBook(result.book)
                    result.book.contentHash?.let { knownHashes.add(it) }
                    results.add(
                        ImportItemResult(
                            fileName = fileName,
                            status = ImportItemStatus.Success,
                            message = result.book.title
                        )
                    )
                }
                is LibraryImporter.ImportResult.Duplicate -> {
                    results.add(
                        ImportItemResult(
                            fileName = fileName,
                            status = ImportItemStatus.Duplicate,
                            message = context.getString(
                                R.string.import_duplicate_detail,
                                result.existingTitle
                            )
                        )
                    )
                }
                is LibraryImporter.ImportResult.Err -> {
                    results.add(
                        ImportItemResult(
                            fileName = fileName,
                            status = ImportItemStatus.Failed,
                            message = result.message
                        )
                    )
                }
            }
        }

        _uiState.value = UploadState.BatchComplete(results)
    }

    private suspend fun importLocalFiles(files: List<Pair<java.io.File, String>>) {
        val context = getApplication<Application>().applicationContext
        val results = mutableListOf<ImportItemResult>()
        val knownHashes = BookRepository.books.value
            .mapNotNull { it.contentHash }
            .toMutableSet()

        files.forEachIndexed { index, (file, fileName) ->
            _uiState.value = UploadState.Progress(
                phase = ImportPhase.Importing,
                current = index + 1,
                total = files.size,
                currentFileName = fileName
            )

            when (
                val result = LibraryImporter.importFromFile(
                    context = context,
                    file = file,
                    displayName = fileName,
                    existingHashes = knownHashes,
                    deleteSourceAfter = true
                )
            ) {
                is LibraryImporter.ImportResult.Ok -> {
                    BookRepository.addBook(result.book)
                    result.book.contentHash?.let { knownHashes.add(it) }
                    results.add(
                        ImportItemResult(
                            fileName = fileName,
                            status = ImportItemStatus.Success,
                            message = result.book.title
                        )
                    )
                }
                is LibraryImporter.ImportResult.Duplicate -> {
                    results.add(
                        ImportItemResult(
                            fileName = fileName,
                            status = ImportItemStatus.Duplicate,
                            message = context.getString(
                                R.string.import_duplicate_detail,
                                result.existingTitle
                            )
                        )
                    )
                }
                is LibraryImporter.ImportResult.Err -> {
                    results.add(
                        ImportItemResult(
                            fileName = fileName,
                            status = ImportItemStatus.Failed,
                            message = result.message
                        )
                    )
                }
            }
        }

        _uiState.value = UploadState.BatchComplete(results)
    }

    fun resetState() {
        _uiState.value = UploadState.Empty
    }
}
