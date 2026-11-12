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

data class BookSeries(val name: String, val number: Double? = null) {
    val numberLabel: String? get() = number?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }
    val label: String get() = numberLabel?.let { "$name · $it" } ?: name
}

object BookFacts {
    const val Publisher = "publisher"
    const val Year = "year"
    const val Language = "language"
    const val Translator = "translator"
    const val Isbn = "isbn"
    const val Genres = "genres"
    const val Original = "original"
    const val Pages = "pages"
    val Shown = listOf(Publisher, Year, Language, Translator, Original, Isbn)

    fun title(key: String): String = when (key) {
        Publisher -> "Издательство"
        Year -> "Год"
        Language -> "Язык"
        Translator -> "Переводчик"
        Isbn -> "ISBN"
        Original -> "Оригинал"
        else -> key
    }

    fun genres(facts: Map<String, String>): List<String> = facts[Genres].orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }
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
    val paper: Boolean = false,
    val edited: Boolean = false,
    val customDescription: String? = null,
    val pdf: Boolean = false,
    val facts: Map<String, String> = emptyMap(),
    val series: List<BookSeries> = emptyList(),
    val tags: List<String> = emptyList(),
    val scan: Int = 0,
) {
    fun fact(key: String): String = facts[key].orEmpty()

    val statsKey: String get() = fingerprint ?: id

    val isPdf: Boolean get() = pdf || fileName.endsWith(".pdf")

    val pdfText: Boolean get() = pdf && fileName.endsWith(".epub")

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
        put("paper", paper)
        if (edited) {
            put("editTitle", title)
            put("editAuthor", author)
        }
        customDescription?.let { put("description", it) }
        put("facts", JSONObject().apply { facts.forEach { (k, v) -> put(k, v) } })
        if (scan > 0 || series.isNotEmpty()) {
            put("series", JSONArray().apply { series.forEach { s -> put(JSONObject().put("n", s.name).apply { s.number?.let { put("i", it) } }) } })
        }
        put("tags", JSONArray(tags))
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
        paper = j.optBoolean("paper", paper),
        title = if (j.has("editTitle")) j.optString("editTitle").ifBlank { title } else title,
        author = if (j.has("editAuthor")) j.optString("editAuthor") else author,
        edited = edited || j.has("editTitle"),
        customDescription = if (j.has("description")) j.optString("description") else customDescription,
        facts = j.optJSONObject("facts")?.let { o -> facts + o.keys().asSequence().associateWith { k -> o.optString(k) } } ?: facts,
        series = j.optJSONArray("series")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("n").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                BookSeries(name, if (o.has("i")) o.optDouble("i").takeUnless { it.isNaN() } else null)
            }
        } ?: series,
        tags = j.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }.distinct() } ?: tags,
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
        if (pdf) put("pdf", true)
        if (scan > 0) put("scan", scan)
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
                pdf = json.optBoolean("pdf", false),
                scan = json.optInt("scan", 0),
            )
            return if (json.has("status")) base.withMeta(json) else base
        }

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
    }
}
