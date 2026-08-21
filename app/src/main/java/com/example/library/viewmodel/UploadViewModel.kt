package com.example.library.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.R
import com.example.library.data.BookRepository
import com.example.library.data.PdfImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UploadState {
    object Empty : UploadState()
    object Loading : UploadState()
    data class Success(val fileName: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

/**
 * Drives the "Add Book" screen.
 *
 * Uses [AndroidViewModel] to obtain a [Context] for file I/O without leaking an
 * Activity reference. The actual heavy lifting is delegated to [PdfImporter].
 */
class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UploadState>(UploadState.Empty)
    val uiState: StateFlow<UploadState> = _uiState.asStateFlow()

    /**
     * Called when the user picks a file from the SAF picker.
     *
     * @param uri     Content URI returned by [ActivityResultContracts.OpenDocument].
     * @param fileName Display name of the picked file (may include ".pdf").
     */
    fun onFileSelected(uri: Uri?, fileName: String?) {
        if (uri == null || fileName == null || !fileName.lowercase().endsWith(".pdf")) {
            _uiState.value = UploadState.Error(
                getApplication<Application>().getString(R.string.invalid_pdf_file)
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = UploadState.Loading

            val context = getApplication<Application>().applicationContext
            when (val result = PdfImporter.import(context, uri, fileName)) {
                is PdfImporter.ImportResult.Ok -> {
                    BookRepository.addBook(result.book)
                    _uiState.value = UploadState.Success(result.book.title)
                }
                is PdfImporter.ImportResult.Err -> {
                    _uiState.value = UploadState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = UploadState.Empty
    }
}
