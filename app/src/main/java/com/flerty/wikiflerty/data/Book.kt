package com.flerty.wikiflerty.data

import org.json.JSONObject

/**
 * A book in the library.
 *
 * File names are stored relative to the app folders, so the library can later be
 * exported, synced and restored on another device.
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
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("fileName", fileName)
        put("coverFileName", coverFileName)
        put("addedAt", addedAt)
        put("lastReadAt", lastReadAt)
        put("progress", progress)
        put("locator", locatorJson)
    }

    companion object {
        fun fromJson(json: JSONObject): Book = Book(
            id = json.getString("id"),
            title = json.optString("title"),
            author = json.optString("author"),
            fileName = json.getString("fileName"),
            coverFileName = json.optStringOrNull("coverFileName"),
            addedAt = json.optLong("addedAt"),
            lastReadAt = if (json.isNull("lastReadAt")) null else json.optLong("lastReadAt"),
            progress = json.optDouble("progress", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            locatorJson = json.optStringOrNull("locator"),
        )

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
    }
}
