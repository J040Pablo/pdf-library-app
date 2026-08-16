package com.example.library.model

data class Collection(
    val id: String,
    val name: String,
    val description: String = "",
    val bookIds: List<String> = emptyList(),
    val coverUri: String? = null          // URI of user-selected cover image; null = auto-generated
) {
    companion object
}
