package com.example.library.data

import com.example.library.model.Collection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CollectionRepository {
    private val _collections = MutableStateFlow<List<Collection>>(
        listOf(
            Collection(
                id = "c1",
                name = "Craft & Engineering",
                description = "Books about writing better code",
                bookIds = listOf("1", "2", "5")
            ),
            Collection(
                id = "c2",
                name = "Kotlin",
                description = "Everything Kotlin",
                bookIds = listOf("3", "4")
            ),
            Collection(
                id = "c3",
                name = "To Read",
                description = "",
                bookIds = emptyList()
            )
        )
    )
    val collections: StateFlow<List<Collection>> = _collections.asStateFlow()

    fun addCollection(collection: Collection) {
        _collections.value = _collections.value + collection
    }

    fun removeCollection(id: String) {
        _collections.value = _collections.value.filter { it.id != id }
    }

    fun updateCollection(updated: Collection) {
        _collections.value = _collections.value.map {
            if (it.id == updated.id) updated else it
        }
    }

    fun getCollectionById(id: String): Collection? =
        _collections.value.firstOrNull { it.id == id }
}
