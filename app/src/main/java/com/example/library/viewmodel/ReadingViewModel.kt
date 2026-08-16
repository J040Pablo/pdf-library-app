package com.example.library.viewmodel

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.data.BookRepository
import com.example.library.model.Book
import com.example.library.model.Chapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class ReadingUiState(
    val book: Book? = null,
    val chapter: Chapter? = null,
    val chapterIndex: Int = 0,
    val currentPage: Int = 0, // 0-indexed within chapter
    val totalPages: Int = 1,
    val isControlsVisible: Boolean = true,
    val zoomScale: Float = 1f,
    val zoomOffset: Offset = Offset.Zero,
    val isBookmarked: Boolean = false,
    val orientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
)

class ReadingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private var loadedBookId: String? = null
    private var loadedChapterId: String? = null
    private var autoHideJob: Job? = null

    fun load(bookId: String, chapterId: String) {
        // Retain active reading state on configuration change / screen rotation
        if (loadedBookId == bookId && loadedChapterId == _uiState.value.chapter?.id && _uiState.value.book != null) {
            return
        }

        val books = BookRepository.books.value
        val book = books.firstOrNull { it.id == bookId } ?: return
        val chapters = book.chapters.ifEmpty {
            listOf(Chapter("1", "Capítulo 1", "${book.pageCount} páginas", 0, (book.pageCount - 1).coerceAtLeast(0)))
        }

        val chapterIdx = chapters.indexOfFirst { it.id == chapterId }.let { if (it >= 0) it else 0 }
        val chapter = chapters.getOrNull(chapterIdx) ?: chapters.first()

        loadedBookId = bookId
        loadedChapterId = chapter.id

        val startPage = chapter.startPage
        val endPage = chapter.endPage ?: (book.pageCount - 1).coerceAtLeast(startPage)
        val totalPagesInChapter = (endPage - startPage + 1).coerceAtLeast(1)

        // Determine starting page within chapter based on book's current overall page
        val initialPageInChapter = if (book.currentPage >= startPage && book.currentPage <= endPage) {
            book.currentPage - startPage
        } else {
            0
        }

        _uiState.update {
            it.copy(
                book = book,
                chapter = chapter,
                chapterIndex = chapterIdx,
                currentPage = initialPageInChapter.coerceIn(0, totalPagesInChapter - 1),
                totalPages = totalPagesInChapter,
                isBookmarked = book.isBookmarked,
                zoomScale = 1f,
                zoomOffset = Offset.Zero,
                isControlsVisible = true
            )
        }

        restartAutoHideTimer()
    }

    fun toggleControls() {
        val next = !_uiState.value.isControlsVisible
        _uiState.update { it.copy(isControlsVisible = next) }
        if (next) {
            restartAutoHideTimer()
        } else {
            autoHideJob?.cancel()
        }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(isControlsVisible = visible) }
        if (visible) {
            restartAutoHideTimer()
        } else {
            autoHideJob?.cancel()
        }
    }

    private fun restartAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(4000)
            _uiState.update { it.copy(isControlsVisible = false) }
        }
    }

    private var lastPageChangeTime = 0L

    fun goToPage(page: Int) {
        val now = System.currentTimeMillis()
        val maxPage = _uiState.value.totalPages - 1
        val newPage = page.coerceIn(0, maxPage.coerceAtLeast(0))
        if (newPage != _uiState.value.currentPage) {
            if (now - lastPageChangeTime < 80) return // Rapid tap protection guard
            lastPageChangeTime = now
            _uiState.update { it.copy(currentPage = newPage) }
            saveReadingProgress()
        }
    }

    fun nextPage() {
        goToPage(_uiState.value.currentPage + 1)
    }

    fun previousPage() {
        goToPage(_uiState.value.currentPage - 1)
    }

    fun goToNextChapter() {
        val book = _uiState.value.book ?: return
        val nextIdx = _uiState.value.chapterIndex + 1
        if (nextIdx < book.chapters.size) {
            val nextChapter = book.chapters[nextIdx]
            val startPage = nextChapter.startPage
            val endPage = nextChapter.endPage ?: (book.pageCount - 1).coerceAtLeast(startPage)
            val totalPages = (endPage - startPage + 1).coerceAtLeast(1)

            loadedChapterId = nextChapter.id

            _uiState.update {
                it.copy(
                    chapter = nextChapter,
                    chapterIndex = nextIdx,
                    currentPage = 0,
                    totalPages = totalPages,
                    zoomScale = 1f,
                    zoomOffset = Offset.Zero
                )
            }
            saveReadingProgress()
        }
    }

    fun goToPreviousChapter() {
        val book = _uiState.value.book ?: return
        val prevIdx = _uiState.value.chapterIndex - 1
        if (prevIdx >= 0) {
            val prevChapter = book.chapters[prevIdx]
            val startPage = prevChapter.startPage
            val endPage = prevChapter.endPage ?: (book.pageCount - 1).coerceAtLeast(startPage)
            val totalPages = (endPage - startPage + 1).coerceAtLeast(1)

            loadedChapterId = prevChapter.id

            _uiState.update {
                it.copy(
                    chapter = prevChapter,
                    chapterIndex = prevIdx,
                    currentPage = 0,
                    totalPages = totalPages,
                    zoomScale = 1f,
                    zoomOffset = Offset.Zero
                )
            }
            saveReadingProgress()
        }
    }

    fun toggleBookmark() {
        val book = _uiState.value.book ?: return
        val updatedBook = book.copy(isBookmarked = !book.isBookmarked)
        _uiState.update {
            it.copy(
                book = updatedBook,
                isBookmarked = updatedBook.isBookmarked
            )
        }
        BookRepository.updateBook(updatedBook)
    }

    fun toggleOrientation(activity: Activity) {
        val currentOrientation = activity.requestedOrientation
        val newOrientation = if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        activity.requestedOrientation = newOrientation
        _uiState.update { it.copy(orientation = newOrientation) }
    }

    fun setZoom(scale: Float, offset: Offset) {
        _uiState.update {
            it.copy(
                zoomScale = scale,
                zoomOffset = if (scale <= 1f) Offset.Zero else offset
            )
        }
    }

    fun resetZoom() {
        _uiState.update {
            it.copy(
                zoomScale = 1f,
                zoomOffset = Offset.Zero
            )
        }
    }

    private fun saveReadingProgress() {
        val book = _uiState.value.book ?: return
        val chapter = _uiState.value.chapter ?: return
        val globalPage = chapter.startPage + _uiState.value.currentPage
        val totalPages = max(1, book.pageCount)
        val progress = ((globalPage + 1).toFloat() / totalPages).coerceIn(0f, 1f)

        // Automatically mark chapter as read when reaching its last page
        val isLastPageInChapter = _uiState.value.currentPage == _uiState.value.totalPages - 1
        val updatedChapters = if (isLastPageInChapter && !chapter.isRead) {
            book.chapters.map { ch ->
                if (ch.id == chapter.id) ch.copy(isRead = true) else ch
            }
        } else {
            book.chapters
        }

        val updatedBook = book.copy(
            currentPage = globalPage,
            progress = progress,
            lastReadDate = "Hoje",
            chapters = updatedChapters
        )
        _uiState.update {
            it.copy(
                book = updatedBook,
                chapter = updatedChapters.firstOrNull { ch -> ch.id == chapter.id } ?: chapter
            )
        }
        BookRepository.updateBook(updatedBook)
    }
}
