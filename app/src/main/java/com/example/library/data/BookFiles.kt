package com.example.library.data

import android.content.Context
import com.example.library.model.Book
import com.example.library.model.BookFormat
import java.io.File

/**
 * On-disk layout helpers for imported books.
 *
 * PDF:   `books/{id}.pdf`
 * Comic: `books/{id}/pages/0000.jpg` …
 * Cover: `covers/{id}.jpg`
 */
object BookFiles {

    fun pdfFile(context: Context, bookId: String): File =
        File(context.filesDir, "books/$bookId.pdf")

    fun comicDir(context: Context, bookId: String): File =
        File(context.filesDir, "books/$bookId")

    fun comicPagesDir(context: Context, bookId: String): File =
        File(comicDir(context, bookId), "pages")

    fun comicPageFile(context: Context, bookId: String, pageIndex: Int): File =
        File(comicPagesDir(context, bookId), "%04d.jpg".format(pageIndex))

    fun coverFile(context: Context, bookId: String): File =
        File(context.filesDir, "covers/$bookId.jpg")

    fun deleteForBook(context: Context, book: Book) {
        when (book.format) {
            BookFormat.PDF -> pdfFile(context, book.id).delete()
            BookFormat.COMIC -> comicDir(context, book.id).deleteRecursively()
        }
        val cover = book.coverUrl?.let { File(it) }
        if (cover != null && cover.exists() && cover.absolutePath.startsWith(context.filesDir.absolutePath)) {
            cover.delete()
        } else {
            coverFile(context, book.id).delete()
        }
    }

    fun isSupportedImportName(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".pdf") || lower.endsWith(".cbr")
    }

    fun isPdfName(fileName: String): Boolean =
        fileName.lowercase().endsWith(".pdf")

    fun isCbrName(fileName: String): Boolean =
        fileName.lowercase().endsWith(".cbr")
}
