package com.example.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
     * user-readable Portuguese error message.
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
                "Não foi possível ler o arquivo PDF selecionado."
            )
        } catch (e: IOException) {
            return@withContext ImportResult.Err(
                "Falha ao copiar o arquivo: ${e.message ?: "erro de E/S"}."
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
                "Este PDF está protegido por senha e não pode ser importado."
            )
        } catch (e: Exception) {
            // Corrupted or incompatible PDF.
            copiedFile.delete()
            return@withContext ImportResult.Err(
                "Não foi possível importar o PDF. Arquivo corrompido ou inválido."
            )
        }

        // Steps 5 & 6 — metadata + chapters via pdfbox-android.
        var title = displayName.removeSuffix(".pdf").removeSuffix(".PDF").trim()
        var author = "Autor desconhecido"
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
                    chapters = collectOutlineItems(outline, doc, pageCount)
                }
            }
        } catch (_: Exception) {
            // pdfbox failed (rare for a file PdfRenderer already opened). Keep
            // defaults extracted above and proceed — a book with unknown
            // metadata is still importable.
        }

        // Fallback chapter split if the PDF has no outline.
        if (chapters.isEmpty() && pageCount > 0) {
            chapters = buildFallbackChapters(pageCount)
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
        node: PDOutlineNode,
        doc: PDDocument,
        pageCount: Int
    ): List<Chapter> {
        val result = mutableListOf<Chapter>()
        var child = node.firstChild
        var index = 1
        while (child != null) {
            val chapterTitle = child.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Capítulo $index"
            val startPage = resolveDestinationPage(child.destination, doc) ?: 0
            result.add(
                Chapter(
                    id = index.toString(),
                    title = chapterTitle,
                    durationOrPages = "Pág. ${startPage + 1}",
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
    private fun buildFallbackChapters(pageCount: Int): List<Chapter> {
        if (pageCount <= FALLBACK_PAGES_PER_CHAPTER) {
            return listOf(
                Chapter(
                    id = "1",
                    title = "Capítulo 1",
                    durationOrPages = "$pageCount páginas",
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
                    title = "Capítulo $id",
                    durationOrPages = "$pageSpan páginas",
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
