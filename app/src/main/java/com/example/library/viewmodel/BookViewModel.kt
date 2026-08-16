package com.example.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.data.BookRepository
import com.example.library.model.Book
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

import android.content.Context
import com.example.library.data.BookStore
import kotlinx.coroutines.launch

/** Number of books shown in the "Recents" row on HomeScreen. */
private const val RECENT_BOOKS_LIMIT = 5

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModel : ViewModel() {

    /**
     * The 5 most-recently added books, newest first.
     * Derived reactively from [BookRepository.books] — updates instantly whenever
     * a book is imported, removed, or updated.
     */
    val recentBooks: StateFlow<List<Book>> = BookRepository.books
        .map { books -> books.takeLast(RECENT_BOOKS_LIMIT).reversed() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookRepository.books.value.takeLast(RECENT_BOOKS_LIMIT).reversed()
        )

    /**
     * All books sorted by rating (highest first), including newly imported ones
     * whose rating is 0f — they appear at the end rather than being filtered out.
     */
    val topRatedBooks: StateFlow<List<Book>> = BookRepository.books
        .map { books -> books.sortedByDescending { it.rating } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookRepository.books.value.sortedByDescending { it.rating }
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    /**
     * Live search results filtered from the full repository list.
     * Combines the query and the repository's book list so results update
     * correctly even if new books are imported mid-session.
     */
    val searchResults: StateFlow<List<Book>> = combine(
        _searchQuery,
        BookRepository.books
    ) { query, books ->
        if (query.isBlank()) emptyList()
        else books.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadSearchHistory(context: Context) {
        viewModelScope.launch {
            val store = BookStore(context.applicationContext)
            _searchHistory.value = store.loadSearchHistory()
        }
    }

    fun submitSearchQuery(query: String, context: Context) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val currentList = _searchHistory.value.toMutableList()
        currentList.removeAll { it.equals(trimmed, ignoreCase = true) }
        currentList.add(0, trimmed)
        val updated = currentList.take(10)

        _searchHistory.value = updated
        viewModelScope.launch {
            BookStore(context.applicationContext).saveSearchHistory(updated)
        }
    }

    fun removeSearchHistoryItem(item: String, context: Context) {
        val updated = _searchHistory.value.filterNot { it.equals(item, ignoreCase = true) }
        _searchHistory.value = updated
        viewModelScope.launch {
            BookStore(context.applicationContext).saveSearchHistory(updated)
        }
    }

    fun clearSearchHistory(context: Context) {
        _searchHistory.value = emptyList()
        viewModelScope.launch {
            BookStore(context.applicationContext).saveSearchHistory(emptyList())
        }
    }
}
