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

    fun childrenOf(parentId: String?): List<Collection> =
        CollectionRepository.childrenOf(parentId)

    fun ancestorsOf(id: String): List<Collection> =
        CollectionRepository.ancestorsOf(id)

    fun getBooksForCollection(collection: Collection): List<Book> {
        val bookMap = allBooks.value.associateBy { it.id }
        return collection.bookIds.mapNotNull { bookMap[it] }
    }

    fun createCollection(
        name: String,
        description: String,
        selectedBookIds: List<String>,
        coverUri: String? = null,
        parentId: String? = null
    ) {
        val collection = Collection(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            bookIds = selectedBookIds,
            coverUri = coverUri,
            parentId = parentId
        )
        CollectionRepository.addCollection(collection)
    }

    fun removeCollection(id: String) {
        CollectionRepository.removeCollection(id)
    }

    fun removeCollections(ids: Set<String>) {
        CollectionRepository.removeCollections(ids)
    }

    fun updateCollection(
        id: String,
        name: String,
        description: String,
        selectedBookIds: List<String>,
        coverUri: String? = null
    ) {
        val existing = getCollectionById(id) ?: return
        val updated = existing.copy(
            name = name,
            description = description,
            bookIds = selectedBookIds,
            coverUri = coverUri
        )
        CollectionRepository.updateCollection(updated)
    }

    fun updateCollectionOrder(newOrder: List<Collection>) {
        CollectionRepository.updateCollectionOrder(newOrder)
    }

    fun updateSiblingOrder(parentId: String?, newOrder: List<Collection>) {
        CollectionRepository.updateSiblingOrder(parentId, newOrder)
    }

    fun updateBookOrder(collectionId: String, bookIds: List<String>) {
        CollectionRepository.updateBookOrder(collectionId, bookIds)
    }

    fun moveBooks(bookIds: Set<String>, fromCollectionId: String, toCollectionId: String) {
        CollectionRepository.moveBooks(bookIds, fromCollectionId, toCollectionId)
    }

    fun removeBooksFromCollection(collectionId: String, bookIds: Set<String>) {
        CollectionRepository.removeBooksFromCollection(collectionId, bookIds)
    }

    fun moveCollection(collectionId: String, newParentId: String?): Boolean =
        CollectionRepository.moveCollection(collectionId, newParentId)

    /** Flat list of collections excluding [excludeId] and its descendants, for move pickers. */
    fun moveTargetsExcluding(excludeId: String?): List<Collection> {
        if (excludeId == null) return collections.value
        val all = collections.value
        val blocked = mutableSetOf(excludeId)
        val queue = ArrayDeque<String>().apply { add(excludeId) }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            all.filter { it.parentId == id }.forEach { child ->
                if (blocked.add(child.id)) queue.add(child.id)
            }
        }
        return all.filter { it.id !in blocked }
    }
}
