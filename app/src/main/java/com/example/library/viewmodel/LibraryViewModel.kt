package com.example.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.data.BookRepository
import com.example.library.model.Book
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    val books: StateFlow<List<Book>> = BookRepository.books

    fun removeBook(book: Book) {
        BookRepository.removeBook(book.id)
    }

    fun toggleFavorite(book: Book) {
        BookRepository.updateBook(book.copy(isBookmarked = !book.isBookmarked))
    }

    fun renameBook(book: Book, newTitle: String) {
        BookRepository.updateBook(book.copy(title = newTitle))
    }
}
