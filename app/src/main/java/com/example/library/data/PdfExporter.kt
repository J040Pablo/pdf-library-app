package com.example.library.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.library.R
import com.example.library.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Exports library PDF files to device storage (Downloads or a user-chosen URI).
 */
object PdfExporter {

    sealed class ExportResult {
        data class Success(val displayName: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    fun pdfFileFor(context: Context, book: Book): File? {
        val file = File(context.filesDir, "books/${book.id}.pdf")
        return file.takeIf { it.exists() }
    }

    fun suggestedFileName(book: Book): String {
        val base = sanitizeFileName(book.title).ifBlank { "book" }
        return "$base.pdf"
    }

    /**
     * Saves the book's PDF into the public Downloads folder with a unique name.
     * Uses MediaStore on API 29+; falls back to the public Downloads directory on older APIs.
     */
    suspend fun exportToDownloads(
        context: Context,
        book: Book
    ): ExportResult = withContext(Dispatchers.IO) {
        val source = pdfFileFor(context, book)
            ?: return@withContext ExportResult.Error(
                context.getString(R.string.export_pdf_missing)
            )

        val displayName = uniqueDownloadsName(context, suggestedFileName(book))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext ExportResult.Error(
                        context.getString(R.string.export_pdf_failed)
                    )
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(source).use { input -> input.copyTo(output) }
                    } ?: run {
                        resolver.delete(uri, null, null)
                        return@withContext ExportResult.Error(
                            context.getString(R.string.export_pdf_failed)
                        )
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    ExportResult.Success(displayName)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    ExportResult.Error(
                        context.getString(
                            R.string.export_pdf_failed_detail,
                            e.message ?: context.getString(R.string.io_error)
                        )
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                    return@withContext ExportResult.Error(
                        context.getString(R.string.export_pdf_failed)
                    )
                }
                val dest = File(downloadsDir, displayName)
                FileInputStream(source).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                ExportResult.Success(displayName)
            }
        } catch (e: SecurityException) {
            ExportResult.Error(context.getString(R.string.export_permission_denied))
        } catch (e: IOException) {
            ExportResult.Error(
                context.getString(
                    R.string.export_pdf_failed_detail,
                    e.message ?: context.getString(R.string.io_error)
                )
            )
        }
    }

    /**
     * Writes the book's PDF to a user-chosen destination [destUri] (SAF CreateDocument).
     */
    suspend fun exportToUri(
        context: Context,
        book: Book,
        destUri: Uri
    ): ExportResult = withContext(Dispatchers.IO) {
        val source = pdfFileFor(context, book)
            ?: return@withContext ExportResult.Error(
                context.getString(R.string.export_pdf_missing)
            )

        try {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: return@withContext ExportResult.Error(
                context.getString(R.string.export_pdf_failed)
            )
            ExportResult.Success(suggestedFileName(book))
        } catch (e: Exception) {
            ExportResult.Error(
                context.getString(
                    R.string.export_pdf_failed_detail,
                    e.message ?: context.getString(R.string.io_error)
                )
            )
        }
    }

    /** Ensures [baseName] does not collide with an existing Downloads entry. */
    private fun uniqueDownloadsName(context: Context, baseName: String): String {
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ".pdf"

        if (!downloadsNameExists(context, baseName)) return baseName

        var index = 1
        while (index < 10_000) {
            val candidate = "$stem ($index)$ext"
            if (!downloadsNameExists(context, candidate)) return candidate
            index++
        }
        return "$stem-${System.currentTimeMillis()}$ext"
    }

    private fun downloadsNameExists(context: Context, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(displayName),
                null
            )?.use { cursor ->
                return cursor.moveToFirst()
            }
            return false
        }

        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        return File(downloadsDir, displayName).exists()
    }

    fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { "book" }
    }
}
