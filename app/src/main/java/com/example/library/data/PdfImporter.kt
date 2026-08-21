package com.example.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.library.R
import com.example.library.model.Book
import com.example.library.model.BookFormat
import com.example.library.model.Chapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Stateless helper that converts a PDF into a [Book].
 *
 * All heavy I/O runs on [Dispatchers.IO].
 */
object PdfImporter {

    /** One-per-N pages fallback when the PDF has no bookmark outline. */
    private const val FALLBACK_PAGES_PER_CHAPTER = 50

    /** Maximum pixel width of the generated cover thumbnail. */
    private const val THUMBNAIL_WIDTH_PX = 360

    sealed class ImportResult {
        data class Ok(val book: Book) : ImportResult()
        data class Duplicate(val existingTitle: String, val fileName: String) : ImportResult()
        data class Err(val message: String, val fileName: String = "") : ImportResult()
    }

    /**
     * Imports a PDF from a SAF [uri].
     *
     * @param existingHashes SHA-256 hashes of books already in the library.
     */
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
        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val copiedFile = File(booksDir, "$uuid.pdf")

        val contentHash: String
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Err(
                    context.getString(R.string.could_not_read_pdf),
                    displayName
                )
            input.use { stream ->
                FileOutputStream(copiedFile).use { output ->
                    contentHash = copyAndHash(stream, output)
                }
            }
        } catch (e: IOException) {
            copiedFile.delete()
            return@withContext ImportResult.Err(
                context.getString(
                    R.string.failed_to_copy_file,
                    e.message ?: context.getString(R.string.io_error)
                ),
                displayName
            )
        }

        if (contentHash in existingHashes) {
            copiedFile.delete()
            val existing = BookRepository.books.value
                .firstOrNull { it.contentHash == contentHash }
            return@withContext ImportResult.Duplicate(
                existingTitle = existing?.title
                    ?: displayName.removeSuffixIgnoreCase(".pdf"),
                fileName = displayName
            )
        }

        finalizeImport(context, copiedFile, uuid, displayName, contentHash)
    }

    /**
     * Imports a PDF already present as a local [file] (e.g. extracted from a ZIP).
     *
     * When [deleteSourceAfter] is true, [file] is removed after a successful copy
     * into app storage (or immediately on duplicate/error if it lived in a temp dir).
     */
    suspend fun importFromFile(
        context: Context,
        file: File,
        displayName: String,
        existingHashes: Set<String> = emptySet(),
        deleteSourceAfter: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            return@withContext ImportResult.Err(
                context.getString(R.string.could_not_read_pdf),
                displayName
            )
        }

        val uuid = UUID.randomUUID().toString()
        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val destFile = File(booksDir, "$uuid.pdf")

        val contentHash: String
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destFile).use { output ->
                    contentHash = copyAndHash(input, output)
                }
            }
        } catch (e: IOException) {
            destFile.delete()
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
            destFile.delete()
            val existing = BookRepository.books.value
                .firstOrNull { it.contentHash == contentHash }
            return@withContext ImportResult.Duplicate(
                existingTitle = existing?.title
                    ?: displayName.removeSuffixIgnoreCase(".pdf"),
                fileName = displayName
            )
        }

        finalizeImport(context, destFile, uuid, displayName, contentHash)
    }

    private fun finalizeImport(
        context: Context,
        copiedFile: File,
        uuid: String,
        displayName: String,
        contentHash: String
    ): ImportResult {
        var pageCount = 0
        var coverUrl: String? = null
        try {
            val pfd = ParcelFileDescriptor.open(copiedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                pageCount = renderer.pageCount
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val scale = THUMBNAIL_WIDTH_PX.toFloat() / page.width
                        val thumbW = THUMBNAIL_WIDTH_PX
                        val thumbH = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }
                        val coverFile = File(coversDir, "$uuid.jpg")
                        FileOutputStream(coverFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        bmp.recycle()
                        coverUrl = coverFile.absolutePath
                    }
                }
            }
        } catch (_: SecurityException) {
            copiedFile.delete()
            return ImportResult.Err(
                context.getString(R.string.pdf_password_protected),
                displayName
            )
        } catch (_: Exception) {
            copiedFile.delete()
            return ImportResult.Err(
                context.getString(R.string.pdf_import_failed),
                displayName
            )
        }

        var title = displayName.removeSuffixIgnoreCase(".pdf").trim()
        var author = context.getString(R.string.unknown_author)
        var chapters: List<Chapter> = emptyList()

        try {
            PDDocument.load(copiedFile).use { doc ->
                val info = doc.documentInformation
                val pdfTitle = info?.title?.trim()
                val pdfAuthor = info?.author?.trim()
                if (!pdfTitle.isNullOrEmpty()) title = pdfTitle
                if (!pdfAuthor.isNullOrEmpty()) author = pdfAuthor

                val outline = doc.documentCatalog?.documentOutline
                if (outline != null) {
                    chapters = collectOutlineItems(context, outline, doc, pageCount)
                }
            }
        } catch (_: Exception) {
            // Soft-fail: keep filename title / unknown author / fallback chapters.
        }

        if (chapters.isEmpty() && pageCount > 0) {
            chapters = buildFallbackChapters(context, pageCount)
        }

        val book = Book(
            id = uuid,
            title = title,
            author = author,
            coverUrl = coverUrl,
            pageCount = pageCount,
            currentPage = 0,
            progress = 0f,
            chapters = chapters,
            contentHash = contentHash,
            format = BookFormat.PDF
        )
        return ImportResult.Ok(book)
    }

    /** Copies [input] to [output] while computing a SHA-256 hex digest. */
    fun copyAndHash(input: InputStream, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun collectOutlineItems(
        context: Context,
        node: PDOutlineNode,
        doc: PDDocument,
        pageCount: Int
    ): List<Chapter> {
        val result = mutableListOf<Chapter>()
        var child = node.firstChild
        var index = 1
        while (child != null) {
            val chapterTitle = child.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: context.getString(R.string.chapter_fallback, index)
            val startPage = resolveDestinationPage(child.destination, doc) ?: 0
            result.add(
                Chapter(
                    id = index.toString(),
                    title = chapterTitle,
                    durationOrPages = context.getString(R.string.page_start, startPage + 1),
                    startPage = startPage
                )
            )
            index++
            child = child.nextSibling
        }
        return result.mapIndexed { i, chapter ->
            val nextStart = result.getOrNull(i + 1)?.startPage
            chapter.copy(endPage = if (nextStart != null) nextStart - 1 else pageCount - 1)
        }
    }

    private fun resolveDestinationPage(dest: Any?, doc: PDDocument): Int? {
        val resolved = when (dest) {
            is PDPageDestination -> dest
            is PDNamedDestination -> doc.documentCatalog
                ?.dests
                ?.getDestination(dest.namedDestination) as? PDPageDestination
            else -> null
        } ?: return null
        return try {
            val page: PDPage = resolved.page ?: return null
            doc.pages.indexOf(page).takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFallbackChapters(context: Context, pageCount: Int): List<Chapter> {
        if (pageCount <= FALLBACK_PAGES_PER_CHAPTER) {
            return listOf(
                Chapter(
                    id = "1",
                    title = context.getString(R.string.chapter_one),
                    durationOrPages = context.getString(R.string.pages_count, pageCount),
                    startPage = 0,
                    endPage = pageCount - 1
                )
            )
        }
        val chapters = mutableListOf<Chapter>()
        var page = 0
        var id = 1
        while (page < pageCount) {
            val end = (page + FALLBACK_PAGES_PER_CHAPTER - 1).coerceAtMost(pageCount - 1)
            val pageSpan = end - page + 1
            chapters.add(
                Chapter(
                    id = id.toString(),
                    title = context.getString(R.string.chapter_fallback, id),
                    durationOrPages = context.getString(R.string.pages_count, pageSpan),
                    startPage = page,
                    endPage = end
                )
            )
            page += FALLBACK_PAGES_PER_CHAPTER
            id++
        }
        return chapters
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String {
        return if (endsWith(suffix, ignoreCase = true)) {
            dropLast(suffix.length)
        } else {
            this
        }
    }
}
