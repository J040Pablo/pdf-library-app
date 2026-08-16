package com.example.library.model

data class Chapter(
    val id: String,
    val title: String,
    val durationOrPages: String,
    /** 0-indexed page number where this chapter begins in the PDF. */
    val startPage: Int = 0,
    /** 0-indexed page number of the last page in this chapter (null = end of document). */
    val endPage: Int? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false
) {
    companion object
}
