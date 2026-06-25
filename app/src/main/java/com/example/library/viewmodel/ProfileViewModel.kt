package com.example.library.viewmodel

import androidx.lifecycle.ViewModel
import com.example.library.data.BookRepository
import com.example.library.model.Book
import com.example.library.model.User
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ProfileViewModel : ViewModel() {

    val user: StateFlow<User> = BookRepository.user
    val books: StateFlow<List<Book>> = BookRepository.books

    val stats = combine(books, user) { books, user ->
        val finishedBooks = books.count { it.progress >= 1.0f }
        val totalPagesRead = books.sumOf { it.currentPage }
        ProfileStats(
            totalBooks = books.size,
            finishedBooks = finishedBooks,
            totalPagesRead = totalPagesRead,
            totalHours = user.totalTimeReadingHours
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileStats())

    val lastReadBook = books.map { books ->
        books.filter { it.lastReadDate != null }
            .sortedByDescending { it.lastReadDate } // Simplification, in real app use timestamp
            .firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

data class ProfileStats(
    val totalBooks: Int = 0,
    val finishedBooks: Int = 0,
    val totalPagesRead: Int = 0,
    val totalHours: Int = 0
)
