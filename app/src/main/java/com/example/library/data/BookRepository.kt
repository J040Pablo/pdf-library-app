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

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _user = MutableStateFlow(User.default())
    val user: StateFlow<User> = _user.asStateFlow()

    @Volatile private var store: BookStore? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var initialized = false

    /**
     * Must be called once from [MainActivity.onCreate] before [setContent].
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            val bookStore = BookStore(context.applicationContext)
            store = bookStore
            initialized = true
            CollectionRepository.initialize(context)
            scope.launch {
                val persistedBooks = bookStore.loadBooks()
                if (persistedBooks != null) {
                    _books.value = persistedBooks
                }
                val persistedUser = bookStore.loadUser()
                if (persistedUser != null) {
                    _user.value = persistedUser
                } else {
                    // First launch: persist the default profile so it is available immediately.
                    bookStore.saveUser(_user.value)
                }
            }
        }
    }

    fun updateUser(user: User) {
        _user.value = user
        persistUser()
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun addBook(book: Book) {
        _books.value = _books.value + book
        persist()
    }

    fun removeBook(bookId: String) {
        val removed = _books.value.firstOrNull { it.id == bookId }
        _books.value = _books.value.filter { it.id != bookId }
        CollectionRepository.removeBooksFromAllCollections(setOf(bookId))
        removed?.let { deleteFiles(it) }
        persist()
    }

    /**
     * Removes all books whose IDs are in [bookIds] in a single atomic operation.
     * Also strips the deleted IDs from every Collection to prevent orphaned references.
     */
    fun removeBooks(bookIds: Set<String>) {
        val removed = _books.value.filter { it.id in bookIds }
        _books.value = _books.value.filter { it.id !in bookIds }
        CollectionRepository.removeBooksFromAllCollections(bookIds)
        removed.forEach { deleteFiles(it) }
        persist()
    }

    fun updateBook(updatedBook: Book) {
        _books.value = _books.value.map {
            if (it.id == updatedBook.id) updatedBook else it
        }
        persist()
    }

    fun toggleChapterReadState(bookId: String, chapterId: String) {
        _books.value = _books.value.map { book ->
            if (book.id == bookId) {
                val updatedChapters = book.chapters.map { ch ->
                    if (ch.id == chapterId) ch.copy(isRead = !ch.isRead) else ch
                }
                book.copy(chapters = updatedChapters)
            } else book
        }
        persist()
    }

    fun toggleBookmark(bookId: String) {
        _books.value = _books.value.map { book ->
            if (book.id == bookId) book.copy(isBookmarked = !book.isBookmarked) else book
        }
        persist()
    }

    /**
     * Updates the order of books as defined by [newOrder].
     * Any books not present in [newOrder] will be appended at the end.
     */
    fun updateBookOrder(newOrder: List<Book>) {
        val newOrderIds = newOrder.map { it.id }.toSet()
        val remaining = _books.value.filter { it.id !in newOrderIds }
        _books.value = newOrder + remaining
        persist()
    }

    fun toggleChapterBookmark(bookId: String, chapterId: String) {
        _books.value = _books.value.map { book ->
            if (book.id == bookId) {
                val updatedChapters = book.chapters.map { ch ->
                    if (ch.id == chapterId) ch.copy(isBookmarked = !ch.isBookmarked) else ch
                }
                book.copy(chapters = updatedChapters)
            } else book
        }
        persist()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun deleteFiles(book: Book) {
        val context = appContext ?: return
        scope.launch { BookFiles.deleteForBook(context, book) }
    }

    /** Fire-and-forget persistence on the IO dispatcher. */
    private fun persist() {
        val s = store ?: return
        val snapshot = _books.value
        scope.launch { s.saveBooks(snapshot) }
    }

    private fun persistUser() {
        val s = store ?: return
        val snapshot = _user.value
        scope.launch { s.saveUser(snapshot) }
    }
}
