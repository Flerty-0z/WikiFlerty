package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class LibrarySort(val id: String, val title: String) {
    Recent("recent", "Недавно читал"),
    Added("added", "Дата добавления"),
    Title("title", "Название"),
    Author("author", "Автор"),
    Progress("progress", "Прогресс");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Recent
    }
}

enum class LibraryView(val id: String) {
    Grid("grid"), List("list");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Grid
    }
}

/** How the library looks: filter, sort order, grid or list, cover size, and the user's shelves. */
data class LibraryPrefs(
    /** "all", "favorite", a status id, or "shelf:<name>". */
    val filter: String = "all",
    val sort: LibrarySort = LibrarySort.Recent,
    /** Newest/biggest first for dates and progress, А→Я for names when false. */
    val descending: Boolean = true,
    val view: LibraryView = LibraryView.Grid,
    /** 0 small, 1 medium, 2 large covers. */
    val coverSize: Int = 1,
    val shelves: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("filter", filter)
        put("sort", sort.id)
        put("descending", descending)
        put("view", view.id)
        put("coverSize", coverSize)
        put("shelves", JSONArray(shelves))
    }

    companion object {
        fun fromJson(j: JSONObject) = LibraryPrefs(
            filter = j.optString("filter", "all"),
            sort = LibrarySort.of(j.optString("sort")),
            descending = j.optBoolean("descending", true),
            view = LibraryView.of(j.optString("view")),
            coverSize = j.optInt("coverSize", 1).coerceIn(0, 2),
            shelves = j.optJSONArray("shelves")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
        )
    }
}

class LibraryPrefsStore(context: Context) {
    private val file = File(context.filesDir, "library_prefs.json")
    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<LibraryPrefs> = _prefs.asStateFlow()

    @Synchronized
    fun update(transform: (LibraryPrefs) -> LibraryPrefs) {
        val updated = transform(_prefs.value)
        if (updated == _prefs.value) return
        _prefs.value = updated
        runCatching { file.writeText(updated.toJson().toString()) }
    }

    private fun load(): LibraryPrefs = runCatching {
        if (file.exists()) LibraryPrefs.fromJson(JSONObject(file.readText())) else LibraryPrefs()
    }.getOrElse { LibraryPrefs() }
}
