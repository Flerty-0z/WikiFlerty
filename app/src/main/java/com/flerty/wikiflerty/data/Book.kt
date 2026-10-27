package com.flerty.wikiflerty.data

import org.json.JSONArray
import org.json.JSONObject

/** Where a book is on the user's reading list, as in ReadEra. */
enum class ReadStatus(val id: String, val title: String) {
    New("new", "Не начата"),
    Reading("reading", "Читаю"),
    Planned("planned", "В планах"),
    Finished("finished", "Прочитано");

    companion object {
        fun of(id: String?): ReadStatus? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A book in the library.
 *
 * File names are stored relative to the app folders, so the library can be
 * exported and restored on another device.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val fileName: String,
    val coverFileName: String?,
    val addedAt: Long,
    val lastReadAt: Long?,
    /** Reading progress through the whole book, from 0.0 to 1.0. */
    val progress: Double,
    /** The exact reading position (a Readium Locator) as JSON. */
    val locatorJson: String?,
    /** SHA-256 of the book file: the same book on another phone has the same fingerprint. */
    val fingerprint: String? = null,
    val status: ReadStatus = ReadStatus.New,
    val favorite: Boolean = false,
    /** The user's own shelves this book is on. */
    val shelves: List<String> = emptyList(),
    val finishedAt: Long? = null,
    /** When status, favorite or shelves last changed (newer wins when merging backups). */
    val metaChangedAt: Long = 0,
    /** Length of the book in Readium positions (about one printed page each); 0 until first opened. */
    val positions: Int = 0,
) {
    /** The key reading statistics are stored under: the same on every phone. */
    val statsKey: String get() = fingerprint ?: id

    fun metaJson(): JSONObject = JSONObject().apply {
        put("status", status.id)
        put("favorite", favorite)
        put("shelves", JSONArray(shelves))
        put("finishedAt", finishedAt)
        put("metaChangedAt", metaChangedAt)
        put("positions", positions)
    }

    /** This book with status, favorite and shelves taken from [j] (made by [metaJson]). */
    fun withMeta(j: JSONObject): Book = copy(
        status = ReadStatus.of(j.optString("status")) ?: status,
        favorite = j.optBoolean("favorite", favorite),
        shelves = j.optJSONArray("shelves")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: shelves,
        finishedAt = if (j.isNull("finishedAt")) null else j.optLong("finishedAt"),
        metaChangedAt = j.optLong("metaChangedAt", metaChangedAt),
        positions = j.optInt("positions", 0).takeIf { it > 0 } ?: positions,
    )

    fun toJson(): JSONObject = metaJson().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("fileName", fileName)
        put("coverFileName", coverFileName)
        put("addedAt", addedAt)
        put("lastReadAt", lastReadAt)
        put("progress", progress)
        put("locator", locatorJson)
        put("fingerprint", fingerprint)
    }

    companion object {
        fun fromJson(json: JSONObject): Book {
            val progress = json.optDouble("progress", 0.0).takeUnless { it.isNaN() } ?: 0.0
            val base = Book(
                id = json.getString("id"),
                title = json.optString("title"),
                author = json.optString("author"),
                fileName = json.getString("fileName"),
                coverFileName = json.optStringOrNull("coverFileName"),
                addedAt = json.optLong("addedAt"),
                lastReadAt = if (json.isNull("lastReadAt")) null else json.optLong("lastReadAt"),
                progress = progress,
                locatorJson = json.optStringOrNull("locator"),
                fingerprint = json.optStringOrNull("fingerprint"),
                // Books from before statuses existed: opened ones count as "Читаю".
                status = if (progress > 0.0) ReadStatus.Reading else ReadStatus.New,
            )
            return if (json.has("status")) base.withMeta(json) else base
        }

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
    }
}
