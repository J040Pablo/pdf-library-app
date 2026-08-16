package com.example.library.viewmodel

import androidx.lifecycle.ViewModel
import com.example.library.data.BookRepository
import com.example.library.data.CollectionRepository
import com.example.library.model.Book
import com.example.library.model.Collection
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class CollectionViewModel : ViewModel() {

    val collections: StateFlow<List<Collection>> = CollectionRepository.collections
    val allBooks: StateFlow<List<Book>> = BookRepository.books

    fun getCollectionById(id: String): Collection? =
        CollectionRepository.getCollectionById(id)

    fun getBooksForCollection(collection: Collection): List<Book> {
        val bookMap = allBooks.value.associateBy { it.id }
        return collection.bookIds.mapNotNull { bookMap[it] }
    }

    fun createCollection(name: String, description: String, selectedBookIds: List<String>, coverUri: String? = null) {
        val collection = Collection(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            bookIds = selectedBookIds,
            coverUri = coverUri
        )
        CollectionRepository.addCollection(collection)
    }

    fun removeCollection(id: String) {
        CollectionRepository.removeCollection(id)
    }

    fun updateCollection(id: String, name: String, description: String, selectedBookIds: List<String>, coverUri: String? = null) {
        val existing = getCollectionById(id) ?: return
        val updated = existing.copy(
            name = name,
            description = description,
            bookIds = selectedBookIds,
            coverUri = coverUri
        )
        CollectionRepository.updateCollection(updated)
    }
}
