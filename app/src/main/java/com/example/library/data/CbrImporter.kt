package com.example.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.library.R
import com.example.library.model.Book
import com.example.library.model.BookFormat
import com.example.library.model.Chapter
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Imports comic book RAR archives (.cbr) into page images under app storage.
 */
object CbrImporter {

    private const val THUMBNAIL_WIDTH_PX = 360

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

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
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable grants.
        }

        val uuid = UUID.randomUUID().toString()
        val workDir = File(context.cacheDir, "cbr_import_$uuid").also { it.mkdirs() }
        val archiveFile = File(workDir, "source.cbr")

        val contentHash: String
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Err(
                    context.getString(R.string.could_not_read_cbr),
                    displayName
                )
            input.use { stream ->
                FileOutputStream(archiveFile).use { output ->
                    contentHash = PdfImporter.copyAndHash(stream, output)
                }
            }
        } catch (e: IOException) {
            workDir.deleteRecursively()
            return@withContext ImportResult.Err(
                context.getString(
                    R.string.failed_to_copy_file,
                    e.message ?: context.getString(R.string.io_error)
                ),
                displayName
            )
        }

        if (contentHash in existingHashes) {
            workDir.deleteRecursively()
            val existing = BookRepository.books.value
                .firstOrNull { it.contentHash == contentHash }
            return@withContext ImportResult.Duplicate(
                existingTitle = existing?.title
                    ?: displayName.removeSuffixIgnoreCase(".cbr"),
                fileName = displayName
            )
        }

        try {
            finalizeImport(context, archiveFile, uuid, displayName, contentHash)
        } finally {
            workDir.deleteRecursively()
        }
    }

    suspend fun importFromFile(
        context: Context,
        file: File,
        displayName: String,
        existingHashes: Set<String> = emptySet(),
        deleteSourceAfter: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            return@withContext ImportResult.Err(
                context.getString(R.string.could_not_read_cbr),
                displayName
            )
        }

        val uuid = UUID.randomUUID().toString()
        val workDir = File(context.cacheDir, "cbr_import_$uuid").also { it.mkdirs() }
        val archiveFile = File(workDir, "source.cbr")

        val contentHash: String
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(archiveFile).use { output ->
                    contentHash = PdfImporter.copyAndHash(input, output)
                }
            }
        } catch (e: IOException) {
            workDir.deleteRecursively()
            return@withContext ImportResult.Err(
                context.getString(
                    R.string.failed_to_copy_file,
                    e.message ?: context.getString(R.string.io_error)
                ),
                displayName
            )
        } finally {
            if (deleteSourceAfter) {
                file.delete()
            }
        }

        if (contentHash in existingHashes) {
            workDir.deleteRecursively()
            val existing = BookRepository.books.value
                .firstOrNull { it.contentHash == contentHash }
            return@withContext ImportResult.Duplicate(
                existingTitle = existing?.title
                    ?: displayName.removeSuffixIgnoreCase(".cbr"),
                fileName = displayName
            )
        }

        try {
            finalizeImport(context, archiveFile, uuid, displayName, contentHash)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun finalizeImport(
        context: Context,
        archiveFile: File,
        uuid: String,
        displayName: String,
        contentHash: String
    ): ImportResult {
        val extractDir = File(archiveFile.parentFile, "extracted").also { it.mkdirs() }

        try {
            extractImages(archiveFile, extractDir)
        } catch (_: Exception) {
            BookFiles.comicDir(context, uuid).deleteRecursively()
            return ImportResult.Err(
                context.getString(R.string.cbr_import_failed),
                displayName
            )
        }

        val imageFiles = extractDir.walkTopDown()
            .filter { it.isFile && isImageFile(it.name) }
            .sortedWith { a, b -> compareNatural(a.name, b.name) }
            .toList()

        if (imageFiles.isEmpty()) {
            BookFiles.comicDir(context, uuid).deleteRecursively()
            return ImportResult.Err(
                context.getString(R.string.cbr_no_pages_found),
                displayName
            )
        }

        var coverUrl: String? = null
        var pageCount = 0
        BookFiles.comicPagesDir(context, uuid).mkdirs()
        for (source in imageFiles) {
            val dest = BookFiles.comicPageFile(context, uuid, pageCount)
            val decoded = decodeBitmap(source) ?: continue
            try {
                FileOutputStream(dest).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                if (coverUrl == null) {
                    coverUrl = writeCoverThumbnail(context, uuid, decoded)
                }
                pageCount++
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }
        }

        if (pageCount == 0) {
            BookFiles.comicDir(context, uuid).deleteRecursively()
            return ImportResult.Err(
                context.getString(R.string.cbr_no_pages_found),
                displayName
            )
        }

        val title = displayName.removeSuffixIgnoreCase(".cbr").trim()
            .ifBlank { context.getString(R.string.unknown_book) }
        val author = context.getString(R.string.unknown_author)
        val chapters = listOf(
            Chapter(
                id = "1",
                title = context.getString(R.string.chapter_one),
                durationOrPages = context.getString(R.string.pages_count, pageCount),
                startPage = 0,
                endPage = pageCount - 1
            )
        )

        return ImportResult.Ok(
            Book(
                id = uuid,
                title = title,
                author = author,
                coverUrl = coverUrl,
                pageCount = pageCount,
                currentPage = 0,
                progress = 0f,
                chapters = chapters,
                contentHash = contentHash,
                format = BookFormat.COMIC
            )
        )
    }

    private fun extractImages(archiveFile: File, extractDir: File) {
        Archive(archiveFile).use { archive ->
            for (header in archive.fileHeaders) {
                if (header.isDirectory) continue
                val name = header.fileName?.replace('\\', '/') ?: continue
                if (name.contains("..") || name.startsWith("/")) continue
                val fileName = name.substringAfterLast('/')
                if (fileName.isBlank() || !isImageFile(fileName)) continue

                val outFile = File(extractDir, sanitizeFileName(fileName))
                FileOutputStream(outFile).use { output ->
                    archive.extractFile(header, output)
                }
            }
        }
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in IMAGE_EXTENSIONS
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun decodeBitmap(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCoverThumbnail(context: Context, uuid: String, source: Bitmap): String {
        val scale = THUMBNAIL_WIDTH_PX.toFloat() / source.width.coerceAtLeast(1)
        val thumbW = THUMBNAIL_WIDTH_PX
        val thumbH = (source.height * scale).toInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(source, thumbW, thumbH, true)
        val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }
        val coverFile = File(coversDir, "$uuid.jpg")
        FileOutputStream(coverFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        if (thumb !== source && !thumb.isRecycled) thumb.recycle()
        return coverFile.absolutePath
    }

    /** Natural order so page2.jpg comes before page10.jpg. */
    fun compareNatural(a: String, b: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val partsA = regex.findAll(a.lowercase(Locale.US)).map { it.value }.toList()
        val partsB = regex.findAll(b.lowercase(Locale.US)).map { it.value }.toList()
        val len = minOf(partsA.size, partsB.size)
        for (i in 0 until len) {
            val pa = partsA[i]
            val pb = partsB[i]
            val cmp = if (pa[0].isDigit() && pb[0].isDigit()) {
                pa.toBigInteger().compareTo(pb.toBigInteger())
            } else {
                pa.compareTo(pb)
            }
            if (cmp != 0) return cmp
        }
        return partsA.size.compareTo(partsB.size)
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String {
        return if (endsWith(suffix, ignoreCase = true)) {
            dropLast(suffix.length)
        } else {
            this
        }
    }
}
