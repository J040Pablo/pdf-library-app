package com.example.library.model

data class Collection(
    val id: String,
    val name: String,
    val description: String = "",
    val bookIds: List<String> = emptyList(),
    val coverUri: String? = null,
    /** Null = top-level (Library root). Otherwise the parent collection id. */
    val parentId: String? = null
) {
    companion object
}
