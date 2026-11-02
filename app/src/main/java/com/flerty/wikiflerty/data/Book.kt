package com.flerty.wikiflerty.data

import org.json.JSONArray
import org.json.JSONObject

enum class ReadStatus(val id: String, val title: String) {
    New("new", "Не начата"),
    Reading("reading", "Читаю"),
    Planned("planned", "В планах"),
    Finished("finished", "Прочитано");

    companion object {
        fun of(id: String?): ReadStatus? = entries.firstOrNull { it.id == id }
    }
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val fileName: String,
    val coverFileName: String?,
    val addedAt: Long,
    val lastReadAt: Long?,
    val progress: Double,
    val locatorJson: String?,
    val fingerprint: String? = null,
    val status: ReadStatus = ReadStatus.New,
    val favorite: Boolean = false,
    val shelves: List<String> = emptyList(),
    val finishedAt: Long? = null,
    val metaChangedAt: Long = 0,
    val positions: Int = 0,
    val rating: Float? = null,
    val review: String = "",
    val ratedAt: Long = 0,
) {
    val statsKey: String get() = fingerprint ?: id

    fun metaJson(): JSONObject = JSONObject().apply {
        put("status", status.id)
        put("favorite", favorite)
        put("shelves", JSONArray(shelves))
        put("finishedAt", finishedAt)
        put("metaChangedAt", metaChangedAt)
        put("positions", positions)
        put("rating", rating?.toDouble() ?: -1.0)
        put("review", review)
        put("ratedAt", ratedAt)
    }

    fun withMeta(j: JSONObject): Book = copy(
        status = ReadStatus.of(j.optString("status")) ?: status,
        favorite = j.optBoolean("favorite", favorite),
        shelves = j.optJSONArray("shelves")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: shelves,
        finishedAt = if (j.isNull("finishedAt")) null else j.optLong("finishedAt"),
        metaChangedAt = j.optLong("metaChangedAt", metaChangedAt),
        positions = j.optInt("positions", 0).takeIf { it > 0 } ?: positions,
        rating = when (val r = j.optDouble("rating", -2.0)) {
            -2.0 -> rating
            -1.0 -> null
            else -> r.toFloat().coerceIn(0f, 5f)
        },
        review = if (j.has("review")) j.optString("review") else review,
        ratedAt = j.optLong("ratedAt", ratedAt),
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
                status = if (progress > 0.0) ReadStatus.Reading else ReadStatus.New,
            )
            return if (json.has("status")) base.withMeta(json) else base
        }

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
    }
}
