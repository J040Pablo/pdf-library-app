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

    private fun persist() {
        val s = store ?: return
        val snapshot = _collections.value
        scope.launch { s.saveCollections(snapshot) }
    }
}
