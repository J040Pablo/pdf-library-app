package com.example.library.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Upload : Screen("upload", "Upload", Icons.Default.FileUpload)
    object Library : Screen("library", "Library", Icons.Default.AutoStories)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object BookDetail : Screen("book_detail/{bookId}?origin={origin}", "Book Detail", Icons.Default.AutoStories) {
        fun createRoute(bookId: String, origin: String) = "book_detail/$bookId?origin=$origin"
    }
    object CollectionDetail : Screen("collection_detail/{collectionId}", "Collection", Icons.Default.AutoStories) {
        fun createRoute(collectionId: String) = "collection_detail/$collectionId"
    }
    object CreateCollection : Screen("create_collection?collectionId={collectionId}", "New Collection", Icons.Default.AutoStories) {
        fun createRoute(collectionId: String? = null): String {
            return if (collectionId != null) "create_collection?collectionId=$collectionId" else "create_collection"
        }
    }
    object Reading : Screen("reading/{bookId}/{chapterId}", "Reading", Icons.Default.AutoStories) {
        fun createRoute(bookId: String, chapterId: String) = "reading/$bookId/$chapterId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Upload,
    Screen.Library,
    Screen.Profile
)
