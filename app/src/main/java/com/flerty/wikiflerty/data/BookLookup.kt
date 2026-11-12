package com.flerty.wikiflerty.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

class LookupResult(
    val source: String,
    val title: String,
    val author: String,
    val cover: String?,
    val description: String,
    val facts: Map<String, String>,
    val series: List<BookSeries>,
    val score: Float,
) {
    val genres: List<String> get() = BookFacts.genres(facts)

    val brings: List<String>
        get() = buildList {
            if (description.isNotBlank()) add("Описание")
            if (series.isNotEmpty()) add("Серия")
            BookFacts.Shown.forEach { if (!facts[it].isNullOrBlank()) add(BookFacts.title(it)) }
            if (genres.isNotEmpty()) add("Жанры")
        }
}

object BookLookup {
    class Outcome(val items: List<LookupResult>, val offline: Boolean)

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 14_000
            setRequestProperty("User-Agent", "WikiFlerty/1.0 (Android reader; contact: wikiflerty@app)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun normal(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun lastName(author: String): String = author.split(',').first().trim().split(' ').lastOrNull().orEmpty()

    private fun score(foundTitle: String, foundAuthor: String, title: String, author: String): Float {
        val a = normal(foundTitle)
        val b = normal(title)
        if (a.isEmpty() || b.isEmpty()) return 0f
        val words = b.split(' ').filter { it.length >= 3 }
        val hit = if (words.isEmpty()) 0f else words.count { w -> a.split(' ').any { it.startsWith(w.take(5)) } }.toFloat() / words.size
        val base = when {
            a == b -> 1f
            a.startsWith(b) || b.startsWith(a) -> 0.8f
            a.contains(b) || b.contains(a) -> 0.65f
            else -> 0.5f * hit
        }
        val last = normal(lastName(author))
        val who = normal(foundAuthor)
        val bonus = when {
            last.length < 3 || who.isEmpty() -> 0f
            who.contains(last.take(5)) -> 0.25f
            else -> -0.3f
        }
        return (base + bonus).coerceIn(0f, 1.25f)
    }

    fun exact(result: LookupResult, title: String, author: String): Boolean {
        if (normal(result.title) != normal(title)) return false
        val last = normal(lastName(author))
        return last.length >= 3 && normal(result.author).contains(last.take(5))
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name).trim()

    private fun fantlab(title: String, author: String): List<LookupResult> {
        val q = (title.trim() + " " + lastName(author)).trim()
        val found = JSONArray(get("https://api.fantlab.ru/search-works?q=" + enc(q) + "&page=1&onlymatches=1")).objects()
            .map { w -> w to score(w.text("rusname").ifBlank { w.text("name") }, w.text("all_autor_rusname").ifBlank { w.text("autor_rusname") }, title, author) }
            .filter { it.second >= 0.6f }
            .sortedByDescending { it.second }
            .take(2)
        return found.mapNotNull { (work, rank) ->
            runCatching {
                val id = work.optLong("work_id")
                val full = JSONObject(get("https://api.fantlab.ru/work/$id/extended"))
                val facts = LinkedHashMap<String, String>()
                BookMetaReader.year(full.text("work_year"))?.let { facts[BookFacts.Year] = it }
                full.text("work_name_orig").takeIf { it.isNotEmpty() && normal(it) != normal(title) }?.let { facts[BookFacts.Original] = it }
                val genres = full.optJSONObject("classificatory")?.optJSONArray("genre_group").objects().firstOrNull()
                    ?.optJSONArray("genre").objects()
                    .flatMap { g -> listOf(g.text("label")) + g.optJSONArray("genre").objects().take(2).map { it.text("label") } }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .take(6)
                    .orEmpty()
                if (genres.isNotEmpty()) facts[BookFacts.Genres] = genres.joinToString("|")
                val saga = full.optJSONArray("work_root_saga").objects().firstOrNull { it.text("work_type").equals("цикл", true) || it.text("work_type").contains("цикл", true) }
                val series = saga?.let { s ->
                    val name = s.text("work_name").takeIf { it.isNotEmpty() } ?: return@let null
                    val number = runCatching {
                        val kind = full.optInt("work_type_id")
                        val children = JSONObject(get("https://api.fantlab.ru/work/${s.optLong("work_id")}/extended")).optJSONArray("children").objects()
                            .filter { it.optInt("deep", 1) == 1 && it.optInt("work_type_id") == kind }
                        children.indexOfFirst { it.optLong("work_id") == id }.takeIf { it >= 0 }?.let { it + 1.0 }
                    }.getOrNull()
                    BookSeries(name, number)
                }
                val authors = full.optJSONArray("authors").objects().filter { it.text("type").ifBlank { "autor" } == "autor" }.joinToString(", ") { it.text("name") }
                    .ifBlank { work.text("all_autor_rusname") }
                LookupResult(
                    source = "Фантлаб",
                    title = full.text("work_name").ifBlank { work.text("rusname") },
                    author = authors,
                    cover = full.text("image").takeIf { it.isNotEmpty() }?.let { if (it.startsWith("http")) it else "https://fantlab.ru$it" },
                    description = BookMetaReader.plain(full.text("work_description").replace(Regex("\\[/?[a-zA-Z]+[^\\]]*]"), "")),
                    facts = facts,
                    series = listOfNotNull(series),
                    score = rank,
                )
            }.getOrNull()
        }
    }

    private fun google(title: String, author: String): List<LookupResult> {
        val last = lastName(author)
        val q = "intitle:" + title.trim() + if (last.isNotBlank()) " inauthor:$last" else ""
        val items = JSONObject(get("https://www.googleapis.com/books/v1/volumes?maxResults=8&printType=books&country=US&q=" + enc(q))).optJSONArray("items").objects()
        return items.mapNotNull { item ->
            val info = item.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val name = info.text("title")
            val authors = info.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } }.orEmpty()
            val rank = score(name, authors, title, author)
            if (rank < 0.6f) return@mapNotNull null
            val facts = LinkedHashMap<String, String>()
            info.text("publisher").takeIf { it.isNotEmpty() }?.let { facts[BookFacts.Publisher] = it }
            BookMetaReader.year(info.text("publishedDate"))?.let { facts[BookFacts.Year] = it }
            info.text("language").takeIf { it.isNotEmpty() }?.let { facts[BookFacts.Language] = BookMetaReader.languageName(it) }
            val ids = info.optJSONArray("industryIdentifiers").objects()
            (ids.firstOrNull { it.text("type") == "ISBN_13" } ?: ids.firstOrNull { it.text("type") == "ISBN_10" })
                ?.let { BookMetaReader.isbn(it.text("identifier")) }?.let { facts[BookFacts.Isbn] = it }
            val genres = info.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            BookMetaReader.genres(genres).takeIf { it.isNotEmpty() }?.let { facts[BookFacts.Genres] = it.joinToString("|") }
            val image = info.optJSONObject("imageLinks")?.let { l -> l.text("thumbnail").ifBlank { l.text("smallThumbnail") } }?.takeIf { it.isNotEmpty() }
                ?.replace("http://", "https://")?.replace("&edge=curl", "")
            val about = BookMetaReader.plain(info.text("description"))
            if (facts.isEmpty() && about.isBlank()) return@mapNotNull null
            LookupResult("Google Книги", name, authors, image, about, facts, emptyList(), rank - 0.05f)
        }.take(3)
    }

    private fun litres(title: String, author: String): List<LookupResult> {
        val q = (title.trim() + " " + lastName(author)).trim()
        val root = JSONObject(get("https://api.litres.ru/foundation/api/search?q=" + enc(q) + "&limit=12&types=text_book"))
        val data = (root.optJSONObject("payload")?.optJSONArray("data") ?: root.optJSONArray("data")).objects()
        fun people(o: JSONObject, role: String): String = o.optJSONArray("persons").objects()
            .filter { it.text("role").ifBlank { "author" } == role }
            .joinToString(", ") { it.text("full_name") }
        val found = data.map { e -> e.optJSONObject("instance") ?: e }
            .map { o -> o to score(o.text("title"), people(o, "author"), title, author) }
            .filter { it.second >= 0.6f }
            .sortedByDescending { it.second }
            .take(2)
        return found.mapNotNull { (short, rank) ->
            runCatching {
                val id = short.optLong("id")
                val full = runCatching {
                    val r = JSONObject(get("https://api.litres.ru/foundation/api/arts/$id"))
                    r.optJSONObject("payload")?.optJSONObject("data") ?: r.optJSONObject("data")
                }.getOrNull() ?: short
                val facts = LinkedHashMap<String, String>()
                val publisher = full.optJSONObject("publisher")?.text("name").orEmpty().ifBlank { full.optJSONArray("publishers").objects().firstOrNull()?.text("name").orEmpty() }
                    .ifBlank { if (full.optJSONObject("publisher") == null) full.text("publisher") else "" }
                if (publisher.isNotBlank()) facts[BookFacts.Publisher] = publisher
                (BookMetaReader.year(full.text("publication_date")) ?: BookMetaReader.year(full.text("date_written_at")))?.let { facts[BookFacts.Year] = it }
                BookMetaReader.isbn(full.text("isbn").split(',', ';').firstOrNull())?.let { facts[BookFacts.Isbn] = it }
                full.text("language_code").takeIf { it.isNotEmpty() }?.let { facts[BookFacts.Language] = BookMetaReader.languageName(it) }
                people(full, "translator").takeIf { it.isNotBlank() }?.let { facts[BookFacts.Translator] = it }
                val genres = (full.optJSONArray("genres").objects() + full.optJSONArray("tags").objects()).map { it.text("name") }
                BookMetaReader.genres(genres).takeIf { it.isNotEmpty() }?.let { facts[BookFacts.Genres] = it.joinToString("|") }
                val series = full.optJSONArray("series").objects().mapNotNull { s ->
                    val name = s.text("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val order = listOf("art_order", "order", "sequence_number").firstNotNullOfOrNull { k -> if (s.has(k) && !s.isNull(k)) s.optDouble(k).takeIf { !it.isNaN() && it > 0 } else null }
                    BookSeries(name, order)
                }.take(2)
                val raw = full.text("cover_url").ifBlank { short.text("cover_url") }
                val image = when {
                    raw.isBlank() -> null
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "https://cdn.litres.ru$raw"
                    else -> raw.replace("http://", "https://")
                }
                val about = BookMetaReader.plain(full.text("html_annotation").ifBlank { full.text("annotation") }.ifBlank { short.text("annotation") })
                LookupResult("ЛитРес", full.text("title").ifBlank { short.text("title") }, people(full, "author").ifBlank { people(short, "author") }, image, about, facts, series, rank + 0.05f)
            }.getOrNull()
        }
    }

    suspend fun find(title: String, author: String): Outcome = coroutineScope {
        val jobs = listOf(
            async(Dispatchers.IO) { runCatching { litres(title, author) } },
            async(Dispatchers.IO) { runCatching { fantlab(title, author) } },
            async(Dispatchers.IO) { runCatching { google(title, author) } },
        ).awaitAll()
        val items = jobs.flatMap { it.getOrDefault(emptyList()) }.filter { it.brings.isNotEmpty() }.sortedByDescending { it.score }
        Outcome(items, items.isEmpty() && jobs.all { it.isFailure })
    }

    suspend fun auto(title: String, author: String): LookupResult? {
        if (title.isBlank() || author.isBlank()) return null
        val items = runCatching { find(title, author) }.getOrNull()?.items.orEmpty().filter { exact(it, title, author) }
        if (items.isEmpty()) return null
        val facts = LinkedHashMap<String, String>()
        items.forEach { r -> r.facts.forEach { (k, v) -> if (v.isNotBlank() && facts[k].isNullOrBlank()) facts[k] = v } }
        return LookupResult(
            "",
            title,
            author,
            null,
            items.firstOrNull { it.description.length > 60 }?.description.orEmpty(),
            facts,
            items.firstOrNull { it.series.isNotEmpty() }?.series.orEmpty(),
            1f,
        )
    }
}
