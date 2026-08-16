package com.example.library.data

import com.example.library.model.Book
import com.example.library.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BookRepository {
    private val _books = MutableStateFlow<List<Book>>(listOf(
        Book("1", "Clean Code", "Robert C. Martin", progress = 0.45f, pageCount = 464, currentPage = 208, lastReadDate = "2 dias atrás"),
        Book("2", "The Pragmatic Programmer", "Andy Hunt", progress = 1.0f, pageCount = 352, currentPage = 352, lastReadDate = "Ontem"),
        Book("3", "Kotlin in Action", "Dmitry Jemerov", progress = 0.75f, pageCount = 360, currentPage = 270, lastReadDate = "Hoje"),
        Book("4", "Refactoring", "Martin Fowler", progress = 1.0f, pageCount = 448, currentPage = 448, lastReadDate = "Semana passada"),
        Book("5", "Design Patterns", "Gang of Four", progress = 0.1f, pageCount = 395, currentPage = 40, lastReadDate = "3 dias atrás")
    ))
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _user = MutableStateFlow(User(
        name = "Pablo Silva",
        email = "pablo.silva@example.com"
    ))
    val user: StateFlow<User> = _user.asStateFlow()

    // TODO: For persistence across process death, serialize books to DataStore
    // following the pattern already used in ThemeDataStore.kt.
    fun addBook(book: Book) {
        _books.value = _books.value + book
    }

    fun removeBook(bookId: String) {
        _books.value = _books.value.filter { it.id != bookId }
    }

    fun updateBook(updatedBook: Book) {
        _books.value = _books.value.map {
            if (it.id == updatedBook.id) updatedBook else it
        }
    }
}
