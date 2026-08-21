package com.example.library.data

import android.content.Context
import android.net.Uri
import com.example.library.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Extracts PDF and CBR files from a ZIP archive into a temporary directory.
 */
object ZipPdfImporter {

    data class ExtractedBook(val file: File, val displayName: String)

    sealed class ExtractResult {
        data class Ok(val books: List<ExtractedBook>, val workDir: File) : ExtractResult()
        data class Err(val message: String) : ExtractResult()
    }

    /**
     * Extracts all PDF/CBR entries from [zipUri] into a unique cache subdirectory.
     * Caller must delete [ExtractResult.Ok.workDir] when finished.
     */
    suspend fun extractPdfs(
        context: Context,
        zipUri: Uri
    ): ExtractResult = extractBooks(context, zipUri)

    suspend fun extractBooks(
        context: Context,
        zipUri: Uri
    ): ExtractResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "zip_import_${UUID.randomUUID()}").also {
            if (!it.mkdirs()) {
                return@withContext ExtractResult.Err(
                    context.getString(R.string.zip_extract_failed)
                )
            }
        }

        try {
            val input = context.contentResolver.openInputStream(zipUri)
                ?: run {
                    workDir.deleteRecursively()
                    return@withContext ExtractResult.Err(
                        context.getString(R.string.zip_could_not_read)
                    )
                }

            val books = mutableListOf<ExtractedBook>()
            input.use { stream ->
                ZipInputStream(stream.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name ?: ""
                        val baseName = name.substringAfterLast('/')
                        val supported = !entry.isDirectory && BookFiles.isSupportedImportName(baseName)

                        if (supported) {
                            val safeName = sanitizeEntryName(name)
                            if (safeName != null) {
                                val ext = if (BookFiles.isCbrName(safeName)) "cbr" else "pdf"
                                val outFile = File(
                                    workDir,
                                    "book_${books.size}_${UUID.randomUUID()}.$ext"
                                )
                                FileOutputStream(outFile).use { output ->
                                    zis.copyTo(output)
                                }
                                val displayName = baseName.ifBlank {
                                    "book_${books.size + 1}.$ext"
                                }
                                books.add(ExtractedBook(outFile, displayName))
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (books.isEmpty()) {
                workDir.deleteRecursively()
                return@withContext ExtractResult.Err(
                    context.getString(R.string.zip_no_books_found)
                )
            }

            ExtractResult.Ok(books, workDir)
        } catch (_: ZipException) {
            workDir.deleteRecursively()
            ExtractResult.Err(context.getString(R.string.zip_corrupted))
        } catch (_: IOException) {
            workDir.deleteRecursively()
            ExtractResult.Err(context.getString(R.string.zip_extract_failed))
        } catch (_: Exception) {
            workDir.deleteRecursively()
            ExtractResult.Err(context.getString(R.string.zip_extract_failed))
        }
    }

    private fun sanitizeEntryName(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains("..")) return null
        val fileName = normalized.substringAfterLast('/')
        if (fileName.isBlank()) return null
        return fileName
    }
}
