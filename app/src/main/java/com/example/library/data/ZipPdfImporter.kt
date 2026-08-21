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
 * Extracts PDF files from a ZIP archive into a temporary directory.
 * Nested folders are preserved in the walk; only `.pdf` entries are kept.
 */
object ZipPdfImporter {

    data class ExtractedPdf(val file: File, val displayName: String)

    sealed class ExtractResult {
        data class Ok(val pdfs: List<ExtractedPdf>, val workDir: File) : ExtractResult()
        data class Err(val message: String) : ExtractResult()
    }

    /**
     * Extracts all PDF entries from [zipUri] into a unique cache subdirectory.
     * Caller must delete [ExtractResult.Ok.workDir] when finished.
     */
    suspend fun extractPdfs(
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

            val pdfs = mutableListOf<ExtractedPdf>()
            input.use { stream ->
                ZipInputStream(stream.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name ?: ""
                        val isPdf = !entry.isDirectory &&
                            name.substringAfterLast('/').lowercase().endsWith(".pdf")

                        if (isPdf) {
                            val safeName = sanitizeEntryName(name)
                            if (safeName != null) {
                                val outFile = File(workDir, "pdf_${pdfs.size}_${UUID.randomUUID()}.pdf")
                                FileOutputStream(outFile).use { output ->
                                    zis.copyTo(output)
                                }
                                val displayName = name.substringAfterLast('/').ifBlank {
                                    "book_${pdfs.size + 1}.pdf"
                                }
                                pdfs.add(ExtractedPdf(outFile, displayName))
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (pdfs.isEmpty()) {
                workDir.deleteRecursively()
                return@withContext ExtractResult.Err(
                    context.getString(R.string.zip_no_pdfs_found)
                )
            }

            ExtractResult.Ok(pdfs, workDir)
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

    /**
     * Rejects path traversal (`..`) and absolute paths inside ZIP entries.
     * Returns a relative safe path segment or null if unsafe.
     */
    private fun sanitizeEntryName(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains("..")) return null
        val fileName = normalized.substringAfterLast('/')
        if (fileName.isBlank()) return null
        return fileName
    }
}
