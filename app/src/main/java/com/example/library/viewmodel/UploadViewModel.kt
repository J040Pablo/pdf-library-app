package com.example.library.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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

class UploadViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UploadState>(UploadState.Empty)
    val uiState: StateFlow<UploadState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri?, fileName: String?) {
        if (uri == null || fileName == null || !fileName.lowercase().endsWith(".pdf")) {
            _uiState.value = UploadState.Error("Arquivo inválido. Selecione um PDF.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UploadState.Loading
            // Simulação de processamento/salvamento
            delay(2000)
            _uiState.value = UploadState.Success(fileName)
        }
    }

    fun resetState() {
        _uiState.value = UploadState.Empty
    }
}
