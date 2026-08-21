package com.example.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.library.R
import com.example.library.model.Book
import com.example.library.model.Chapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Stateless helper that converts a SAF [Uri] pointing to a PDF into a [Book].
 *
 * All heavy I/O runs on [Dispatchers.IO] — call from a coroutine already on
 * the desired dispatcher, or wrap with [withContext] yourself.
 */
object PdfImporter {

    /** One-per-N pages fallback when the PDF has no bookmark outline. */
    private const val FALLBACK_PAGES_PER_CHAPTER = 50

    /** Maximum pixel width of the generated cover thumbnail. */
    private const val THUMBNAIL_WIDTH_PX = 360

    // -------------------------------------------------------------------------

    sealed class ImportResult {
        data class Ok(val book: Book) : ImportResult()
        data class Err(val message: String) : ImportResult()
    }

    /**
     * Imports a PDF from [uri] and returns either the constructed [Book] or a
     * user-readable error message.
     *
     * @param displayName File name as reported by the SAF cursor (may include ".pdf").
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        displayName: String
    ): ImportResult = withContext(Dispatchers.IO) {
        // Step 1 — take a persistable read permission so the grant survives
        // process death and is readable on next launch.
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable grants (e.g. emulator
            // file picker). Non-fatal: the file copy below is our real safety net.
        }

        val uuid = UUID.randomUUID().toString()

        // Step 2 — copy the PDF into internal storage so we're not dependent
        // on the original content:// URI being available later.
        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val copiedFile = File(booksDir, "$uuid.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(copiedFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext ImportResult.Err(
                context.getString(R.string.could_not_read_pdf)
            )
        } catch (e: IOException) {
            return@withContext ImportResult.Err(
                context.getString(
                    R.string.failed_to_copy_file,
                    e.message ?: context.getString(R.string.io_error)
                )
            )
        }

        // Steps 3 & 4 — page count + cover thumbnail via PdfRenderer.
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
                        // Fill white background (PDF pages are transparent)
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
        } catch (e: SecurityException) {
            // Password-protected PDFs throw SecurityException from PdfRenderer.
            copiedFile.delete()
            return@withContext ImportResult.Err(
                context.getString(R.string.pdf_password_protected)
            )
        } catch (e: Exception) {
            // Corrupted or incompatible PDF.
            copiedFile.delete()
            return@withContext ImportResult.Err(
                context.getString(R.string.pdf_import_failed)
            )
        }

        // Steps 5 & 6 — metadata + chapters via pdfbox-android.
        var title = displayName.removeSuffix(".pdf").removeSuffix(".PDF").trim()
        var author = context.getString(R.string.unknown_author)
        var chapters: List<Chapter> = emptyList()

        try {
            PDDocument.load(copiedFile).use { doc ->
                // Metadata
                val info = doc.documentInformation
                val pdfTitle = info?.title?.trim()
                val pdfAuthor = info?.author?.trim()
                if (!pdfTitle.isNullOrEmpty()) title = pdfTitle
                if (!pdfAuthor.isNullOrEmpty()) author = pdfAuthor

                // Chapters from outline
                val outline = doc.documentCatalog?.documentOutline
                if (outline != null) {
                    chapters = collectOutlineItems(context, outline, doc, pageCount)
                }
            }
        } catch (_: Exception) {
            // pdfbox failed (rare for a file PdfRenderer already opened). Keep
            // defaults extracted above and proceed — a book with unknown
            // metadata is still importable.
        }

        // Fallback chapter split if the PDF has no outline.
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
            chapters = chapters
        )
        ImportResult.Ok(book)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Recursively walk the PDF outline and convert items to [Chapter]s. */
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
        // Compute endPage retrospectively
        return result.mapIndexed { i, chapter ->
            val nextStart = result.getOrNull(i + 1)?.startPage
            chapter.copy(endPage = if (nextStart != null) nextStart - 1 else pageCount - 1)
        }
    }

    /** Resolve a PDF destination (page-based or named) to a 0-indexed page number. */
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

    /** Naive chapter split for PDFs with no bookmark outline. */
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
}
