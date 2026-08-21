package com.example.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.library.model.Book
import com.example.library.model.BookFormat
import com.example.library.model.Chapter
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

import com.example.library.model.Collection as LibraryCollection

// Separate DataStore file from "settings" used by ThemeDataStore.
private val Context.bookDataStore: DataStore<Preferences> by preferencesDataStore(name = "books")

class BookStore(private val context: Context) {

    companion object {
        private val BOOKS_KEY = stringPreferencesKey("book_list_json")
        private val COLLECTIONS_KEY = stringPreferencesKey("collection_list_json")
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history_json")
    }

    /** Persists [books] as a JSON array string. */
    suspend fun saveBooks(books: List<Book>) {
        val json = JSONArray().apply {
            books.forEach { put(it.toJson()) }
        }.toString()
        context.bookDataStore.edit { prefs ->
            prefs[BOOKS_KEY] = json
        }
    }

    /**
     * Reads and deserializes the persisted book list.
     * Returns `null` if no data has been saved yet (first launch).
     */
    suspend fun loadBooks(): List<Book>? {
        val prefs = context.bookDataStore.data.first()
        val json = prefs[BOOKS_KEY] ?: return null
        return try {
            val array = JSONArray(json)
            List(array.length()) { i -> Book.fromJson(array.getJSONObject(i)) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveCollections(collections: List<LibraryCollection>) {
        val json = JSONArray().apply {
            collections.forEach { put(it.toJson()) }
        }.toString()
        context.bookDataStore.edit { prefs ->
            prefs[COLLECTIONS_KEY] = json
        }
    }

    suspend fun loadCollections(): List<LibraryCollection>? {
        val prefs = context.bookDataStore.data.first()
        val json = prefs[COLLECTIONS_KEY] ?: return null
        return try {
            val array = JSONArray(json)
            List(array.length()) { i -> collectionFromJson(array.getJSONObject(i)) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveSearchHistory(history: List<String>) {
        val json = JSONArray(history).toString()
        context.bookDataStore.edit { prefs ->
            prefs[SEARCH_HISTORY_KEY] = json
        }
    }

    suspend fun loadSearchHistory(): List<String> {
        val prefs = context.bookDataStore.data.first()
        val json = prefs[SEARCH_HISTORY_KEY] ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i -> array.getString(i) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ── JSON serialization ────────────────────────────────────────────────────────

fun Book.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("author", author)
    put("coverUrl", coverUrl ?: JSONObject.NULL)
    put("progress", progress.toDouble())
    put("rating", rating.toDouble())
    put("isBookmarked", isBookmarked)
    put("pageCount", pageCount)
    put("currentPage", currentPage)
    put("lastReadDate", lastReadDate ?: JSONObject.NULL)
    put("chapters", JSONArray().apply { chapters.forEach { put(it.toJson()) } })
    put("contentHash", contentHash ?: JSONObject.NULL)
    put("format", format.name)
}

fun Chapter.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("durationOrPages", durationOrPages)
    put("startPage", startPage)
    put("endPage", endPage ?: JSONObject.NULL)
    put("isRead", isRead)
    put("isBookmarked", isBookmarked)
}

fun LibraryCollection.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("description", description)
    put("coverUri", coverUri ?: JSONObject.NULL)
    put("bookIds", JSONArray(bookIds))
    put("parentId", parentId ?: JSONObject.NULL)
}

fun Book.Companion.fromJson(json: JSONObject): Book = Book(
    id = json.getString("id"),
    title = json.getString("title"),
    author = json.getString("author"),
    coverUrl = json.optString("coverUrl").takeIf { it.isNotEmpty() && it != "null" },
    progress = json.optDouble("progress", 0.0).toFloat(),
    rating = json.optDouble("rating", 0.0).toFloat(),
    isBookmarked = json.optBoolean("isBookmarked", false),
    pageCount = json.optInt("pageCount", 0),
    currentPage = json.optInt("currentPage", 0),
    lastReadDate = json.optString("lastReadDate").takeIf { it.isNotEmpty() && it != "null" },
    chapters = json.optJSONArray("chapters")?.let { arr ->
        List(arr.length()) { i -> Chapter.fromJson(arr.getJSONObject(i)) }
    } ?: emptyList(),
    contentHash = json.optString("contentHash").takeIf { it.isNotEmpty() && it != "null" },
    format = runCatching {
        BookFormat.valueOf(json.optString("format", BookFormat.PDF.name))
    }.getOrDefault(BookFormat.PDF)
)

fun Chapter.Companion.fromJson(json: JSONObject): Chapter = Chapter(
    id = json.getString("id"),
    title = json.getString("title"),
    durationOrPages = json.getString("durationOrPages"),
    startPage = json.optInt("startPage", 0),
    endPage = json.optInt("endPage", -1).takeIf { it >= 0 },
    isRead = json.optBoolean("isRead", false),
    isBookmarked = json.optBoolean("isBookmarked", false)
)

fun collectionFromJson(json: JSONObject): LibraryCollection = LibraryCollection(
    id = json.getString("id"),
    name = json.getString("name"),
    description = json.optString("description", ""),
    coverUri = json.optString("coverUri").takeIf { it.isNotEmpty() && it != "null" },
    bookIds = json.optJSONArray("bookIds")?.let { arr ->
        List(arr.length()) { i -> arr.getString(i) }
    } ?: emptyList(),
    parentId = json.optString("parentId").takeIf { it.isNotEmpty() && it != "null" }
)
