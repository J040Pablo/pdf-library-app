package com.example.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.model.Book
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BookViewModel : ViewModel() {

    private val _recentBooks = MutableStateFlow<List<Book>>(emptyList())
    val recentBooks: StateFlow<List<Book>> = _recentBooks.asStateFlow()

    private val _topRatedBooks = MutableStateFlow<List<Book>>(emptyList())
    val topRatedBooks: StateFlow<List<Book>> = _topRatedBooks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Book>>(emptyList())
    val searchResults: StateFlow<List<Book>> = _searchResults.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        val mockBooks = listOf(
            Book("1", "Clean Code", "Robert C. Martin", null, 0.45f, 4.8f, true),
            Book("2", "The Pragmatic Programmer", "Andy Hunt", null, 0.20f, 4.9f),
            Book("3", "Kotlin in Action", "Dmitry Jemerov", null, 0.75f, 4.7f),
            Book("4", "Jetpack Compose Essentials", "Neil Smyth", null, 0.10f, 4.6f, true),
            Book("5", "Refactoring", "Martin Fowler", null, 0.60f, 4.9f),
            Book("6", "Design Patterns", "Gang of Four", null, 0.30f, 4.8f, true)
        )
        _recentBooks.value = mockBooks.take(3)
        _topRatedBooks.value = mockBooks
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            _searchResults.value = _topRatedBooks.value.filter {
                it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
            }
        }
    }
}
