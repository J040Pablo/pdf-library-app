package com.example.library.model

/**
 * Visual effect applied when the user turns a page in the reader.
 * Persisted in DataStore as `page_animation_type`.
 */
enum class PageAnimationType {
    /** No animation — the page is replaced instantly. */
    NONE,

    /** The current page slides out horizontally as the next page slides in. */
    SLIDE,

    /** Realistic book-style page fold/curl, following the user's drag gesture. */
    CURL_FOLD
}
