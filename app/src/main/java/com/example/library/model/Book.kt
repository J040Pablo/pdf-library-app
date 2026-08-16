package com.example.library.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    val progress: Float = 0f,
    val rating: Float = 0f,
    val isBookmarked: Boolean = false,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastReadDate: String? = null,
    val chapters: List<Chapter> = emptyList()
)
