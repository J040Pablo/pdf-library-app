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

    /**
     * Removes [id] and reparents its direct children to the deleted collection's parent.
     */
    fun removeCollection(id: String) {
        val target = _collections.value.firstOrNull { it.id == id } ?: return
        val newParent = target.parentId
        _collections.value = _collections.value.mapNotNull { collection ->
            when {
                collection.id == id -> null
                collection.parentId == id -> collection.copy(parentId = newParent)
                else -> collection
            }
        }
        persist()
    }

    fun removeCollections(ids: Set<String>) {
        if (ids.isEmpty()) return
        // Process deepest nodes first conceptually by iterating until stable:
        // for each id, reparent children then remove.
        var current = _collections.value
        ids.forEach { id ->
            val target = current.firstOrNull { it.id == id } ?: return@forEach
            val newParent = target.parentId
            current = current.mapNotNull { collection ->
                when {
                    collection.id == id -> null
                    collection.parentId == id -> collection.copy(parentId = newParent)
                    else -> collection
                }
            }
        }
        _collections.value = current
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

    /** Direct children of [parentId] (null = library root), in list order. */
    fun childrenOf(parentId: String?): List<Collection> =
        _collections.value.filter { it.parentId == parentId }

    /** Ancestor chain from root to the parent of [id] (excludes [id] itself). */
    fun ancestorsOf(id: String): List<Collection> {
        val byId = _collections.value.associateBy { it.id }
        val chain = mutableListOf<Collection>()
        var current = byId[id]?.parentId
        val seen = mutableSetOf<String>()
        while (current != null && current !in seen) {
            seen.add(current)
            val node = byId[current] ?: break
            chain.add(node)
            current = node.parentId
        }
        return chain.asReversed()
    }

    /**
     * Moves [collectionId] under [newParentId] (null = root).
     * Returns false if the move would create a cycle.
     */
    fun moveCollection(collectionId: String, newParentId: String?): Boolean {
        if (collectionId == newParentId) return false
        val all = _collections.value
        val target = all.firstOrNull { it.id == collectionId } ?: return false
        if (target.parentId == newParentId) return true

        if (newParentId != null) {
            // Reject if newParent is the collection itself or a descendant.
            val descendants = mutableSetOf<String>()
            val queue = ArrayDeque<String>().apply { add(collectionId) }
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                all.filter { it.parentId == id }.forEach { child ->
                    if (descendants.add(child.id)) queue.add(child.id)
                }
            }
            if (newParentId in descendants) return false
            if (all.none { it.id == newParentId }) return false
        }

        updateCollection(target.copy(parentId = newParentId))
        return true
    }

    fun removeBooksFromAllCollections(bookIds: Set<String>) {
        val updated = _collections.value.map { collection ->
            collection.copy(bookIds = collection.bookIds.filter { it !in bookIds })
        }
        _collections.value = updated
        persist()
    }

    /**
     * Reorders siblings under [parentId]. Other collections keep relative order.
     */
    fun updateSiblingOrder(parentId: String?, newOrder: List<Collection>) {
        if (newOrder.isEmpty()) return
        val siblingIds = newOrder.map { it.id }.toSet()
        val all = _collections.value
        val result = mutableListOf<Collection>()
        var inserted = false
        for (collection in all) {
            if (collection.parentId == parentId && collection.id in siblingIds) {
                if (!inserted) {
                    result.addAll(newOrder.map { it.copy(parentId = parentId) })
                    inserted = true
                }
            } else {
                result.add(collection)
            }
        }
        if (!inserted) {
            result.addAll(newOrder.map { it.copy(parentId = parentId) })
        }
        _collections.value = result
        persist()
    }

    /** Legacy full-list reorder used by root library when all items are roots. */
    fun updateCollectionOrder(newOrder: List<Collection>) {
        updateSiblingOrder(parentId = null, newOrder = newOrder)
    }

    fun updateBookOrder(collectionId: String, bookIds: List<String>) {
        val existing = getCollectionById(collectionId) ?: return
        updateCollection(existing.copy(bookIds = bookIds))
    }

    /**
     * Moves [bookIds] from [fromCollectionId] into [toCollectionId],
     * appending them (deduped) at the end of the destination.
     */
    fun moveBooks(
        bookIds: Set<String>,
        fromCollectionId: String,
        toCollectionId: String
    ) {
        if (bookIds.isEmpty() || fromCollectionId == toCollectionId) return
        _collections.value = _collections.value.map { collection ->
            when (collection.id) {
                fromCollectionId -> collection.copy(
                    bookIds = collection.bookIds.filter { it !in bookIds }
                )
                toCollectionId -> {
                    val merged = collection.bookIds.toMutableList()
                    bookIds.forEach { id ->
                        if (id !in merged) merged.add(id)
                    }
                    collection.copy(bookIds = merged)
                }
                else -> collection
            }
        }
        persist()
    }

    fun removeBooksFromCollection(collectionId: String, bookIds: Set<String>) {
        val existing = getCollectionById(collectionId) ?: return
        updateCollection(
            existing.copy(bookIds = existing.bookIds.filter { it !in bookIds })
        )
    }

    /**
     * Appends [bookIds] to [collectionId], skipping IDs already present.
     * Returns the number of newly added books.
     */
    fun addBooksToCollection(collectionId: String, bookIds: List<String>): Int {
        if (bookIds.isEmpty()) return 0
        val existing = getCollectionById(collectionId) ?: return 0
        val merged = existing.bookIds.toMutableList()
        var added = 0
        bookIds.forEach { id ->
            if (id !in merged) {
                merged.add(id)
                added++
            }
        }
        if (added > 0) {
            updateCollection(existing.copy(bookIds = merged))
        }
        return added
    }

    private fun persist() {
        val s = store ?: return
        val snapshot = _collections.value
        scope.launch { s.saveCollections(snapshot) }
    }
}
