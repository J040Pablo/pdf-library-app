package com.example.library.data

import android.content.Context
import com.example.library.model.Collection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CollectionRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections.asStateFlow()

    @Volatile private var store: BookStore? = null
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val bookStore = BookStore(context.applicationContext)
            store = bookStore
            initialized = true
            scope.launch {
                val persisted = bookStore.loadCollections()
                if (persisted != null) {
                    _collections.value = persisted
                }
            }
        }
    }

    fun addCollection(collection: Collection) {
        _collections.value = _collections.value + collection
        persist()
    }

    fun removeCollection(id: String) {
        _collections.value = _collections.value.filter { it.id != id }
        persist()
    }

    fun updateCollection(updated: Collection) {
        _collections.value = _collections.value.map {
            if (it.id == updated.id) updated else it
        }
        persist()
    }

    fun getCollectionById(id: String): Collection? =
        _collections.value.firstOrNull { it.id == id }

    /**
     * Removes all references to [bookIds] from every collection.
     * Called automatically by [BookRepository.removeBooks] so collections never hold orphaned IDs.
     */
    fun removeBooksFromAllCollections(bookIds: Set<String>) {
        val updated = _collections.value.map { collection ->
            collection.copy(bookIds = collection.bookIds.filter { it !in bookIds })
        }
        _collections.value = updated
        persist()
    }

    /**
     * Updates the order of collections as defined by [newOrder].
     * Any collections not present in [newOrder] will be appended at the end.
     */
    fun updateCollectionOrder(newOrder: List<Collection>) {
        val newOrderIds = newOrder.map { it.id }.toSet()
        val remaining = _collections.value.filter { it.id !in newOrderIds }
        _collections.value = newOrder + remaining
        persist()
    }

    private fun persist() {
        val s = store ?: return
        val snapshot = _collections.value
        scope.launch { s.saveCollections(snapshot) }
    }
}
