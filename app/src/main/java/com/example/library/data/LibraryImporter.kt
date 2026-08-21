package com.example.library.data

import android.content.Context
import android.net.Uri
import com.example.library.R
import com.example.library.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dispatches imports to [PdfImporter] or [CbrImporter] based on file extension.
 */
object LibraryImporter {

    sealed class ImportResult {
        data class Ok(val book: Book) : ImportResult()
        data class Duplicate(val existingTitle: String, val fileName: String) : ImportResult()
        data class Err(val message: String, val fileName: String = "") : ImportResult()
    }

    suspend fun import(
        context: Context,
        uri: Uri,
        displayName: String,
        existingHashes: Set<String> = emptySet()
    ): ImportResult = withContext(Dispatchers.IO) {
        when {
            BookFiles.isPdfName(displayName) -> PdfImporter.import(
                context, uri, displayName, existingHashes
            ).toLibraryResult()
            BookFiles.isCbrName(displayName) -> CbrImporter.import(
                context, uri, displayName, existingHashes
            ).toLibraryResult()
            else -> ImportResult.Err(
                context.getString(R.string.invalid_book_file),
                displayName
            )
        }
    }

    suspend fun importFromFile(
        context: Context,
        file: File,
        displayName: String,
        existingHashes: Set<String> = emptySet(),
        deleteSourceAfter: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        when {
            BookFiles.isPdfName(displayName) -> PdfImporter.importFromFile(
                context, file, displayName, existingHashes, deleteSourceAfter
            ).toLibraryResult()
            BookFiles.isCbrName(displayName) -> CbrImporter.importFromFile(
                context, file, displayName, existingHashes, deleteSourceAfter
            ).toLibraryResult()
            else -> {
                if (deleteSourceAfter) file.delete()
                ImportResult.Err(
                    context.getString(R.string.invalid_book_file),
                    displayName
                )
            }
        }
    }

    private fun PdfImporter.ImportResult.toLibraryResult(): ImportResult = when (this) {
        is PdfImporter.ImportResult.Ok -> ImportResult.Ok(book)
        is PdfImporter.ImportResult.Duplicate -> ImportResult.Duplicate(existingTitle, fileName)
        is PdfImporter.ImportResult.Err -> ImportResult.Err(message, fileName)
    }

    private fun CbrImporter.ImportResult.toLibraryResult(): ImportResult = when (this) {
        is CbrImporter.ImportResult.Ok -> ImportResult.Ok(book)
        is CbrImporter.ImportResult.Duplicate -> ImportResult.Duplicate(existingTitle, fileName)
        is CbrImporter.ImportResult.Err -> ImportResult.Err(message, fileName)
    }
}
