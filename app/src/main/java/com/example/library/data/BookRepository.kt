package com.example.library.data

import android.content.Context
import com.example.library.model.Book
import com.example.library.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BookRepository {

    // Background scope for DataStore I/O — lives as long as the process.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val seedBooks = listOf(
        Book("1", "Clean Code", "Robert C. Martin", progress = 0.45f, pageCount = 464, currentPage = 208, lastReadDate = "2 dias atrás"),
        Book("2", "The Pragmatic Programmer", "Andy Hunt", progress = 1.0f, pageCount = 352, currentPage = 352, lastReadDate = "Ontem"),
        Book("3", "Kotlin in Action", "Dmitry Jemerov", progress = 0.75f, pageCount = 360, currentPage = 270, lastReadDate = "Hoje"),
        Book("4", "Refactoring", "Martin Fowler", progress = 1.0f, pageCount = 448, currentPage = 448, lastReadDate = "Semana passada"),
        Book("5", "Design Patterns", "Gang of Four", progress = 0.1f, pageCount = 395, currentPage = 40, lastReadDate = "3 dias atrás")
    )

    private val _books = MutableStateFlow<List<Book>>(seedBooks)
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _user = MutableStateFlow(User(
        name = "Pablo Silva",
        email = "pablo.silva@example.com"
    ))
    val user: StateFlow<User> = _user.asStateFlow()

    @Volatile private var store: BookStore? = null
    @Volatile private var initialized = false

    /**
     * Must be called once from [MainActivity.onCreate] before [setContent].
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val bookStore = BookStore(context.applicationContext)
            store = bookStore
            initialized = true
            scope.launch {
                val persisted = bookStore.loadBooks()
                if (persisted != null) {
                    _books.value = persisted
                }
            }
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun addBook(book: Book) {
        _books.value = _books.value + book
        persist()
    }

    fun removeBook(bookId: String) {
        _books.value = _books.value.filter { it.id != bookId }
        persist()
    }

    fun updateBook(updatedBook: Book) {
        _books.value = _books.value.map {
            if (it.id == updatedBook.id) updatedBook else it
        }
        persist()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Fire-and-forget persistence on the IO dispatcher. */
    private fun persist() {
        val s = store ?: return
        val snapshot = _books.value
        scope.launch { s.saveBooks(snapshot) }
    }
}
