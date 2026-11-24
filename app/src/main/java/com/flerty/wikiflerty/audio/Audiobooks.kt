package com.flerty.wikiflerty.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AudioTrack(val uri: String, val title: String, val durationMs: Long)

data class AudioChapter(val title: String, val track: Int, val startMs: Long)

data class AudioMark(val track: Int, val ms: Long, val at: Long)

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val tracks: List<AudioTrack>,
    val chapters: List<AudioChapter>,
    val track: Int = 0,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    val bookId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val marks: List<AudioMark> = emptyList(),
) {
    val totalMs: Long get() = tracks.sumOf { it.durationMs }

    fun offsetOf(track: Int): Long = tracks.take(track.coerceIn(0, tracks.size)).sumOf { it.durationMs }

    val elapsedMs: Long get() = offsetOf(track) + positionMs

    val progress: Float get() = if (totalMs <= 0) 0f else (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)

    fun chapterAt(track: Int, ms: Long): Int {
        val at = offsetOf(track) + ms
        var found = 0
        chapters.forEachIndexed { i, c -> if (offsetOf(c.track) + c.startMs <= at + 300) found = i }
        return found
    }

    fun chapterStart(index: Int): Long = chapters.getOrNull(index)?.let { offsetOf(it.track) + it.startMs } ?: 0L

    fun chapterEnd(index: Int): Long = if (index + 1 < chapters.size) chapterStart(index + 1) else totalMs

    fun locate(globalMs: Long): Pair<Int, Long> {
        var left = globalMs.coerceIn(0, (totalMs - 1).coerceAtLeast(0))
        tracks.forEachIndexed { i, t ->
            if (left < t.durationMs || i == tracks.lastIndex) return i to left.coerceAtMost(t.durationMs)
            left -= t.durationMs
        }
        return 0 to 0L
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("tracks", JSONArray().apply { tracks.forEach { put(JSONObject().put("uri", it.uri).put("title", it.title).put("ms", it.durationMs)) } })
        put("chapters", JSONArray().apply { chapters.forEach { put(JSONObject().put("title", it.title).put("track", it.track).put("ms", it.startMs)) } })
        put("track", track)
        put("pos", positionMs)
        put("speed", speed.toDouble())
        bookId?.let { put("book", it) }
        put("added", addedAt)
        lastPlayedAt?.let { put("played", it) }
        put("marks", JSONArray().apply { marks.forEach { put(JSONObject().put("track", it.track).put("ms", it.ms).put("at", it.at)) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Audiobook? {
            val tracks = o.optJSONArray("tracks") ?: return null
            val list = (0 until tracks.length()).mapNotNull { i ->
                tracks.optJSONObject(i)?.let { AudioTrack(it.optString("uri"), it.optString("title"), it.optLong("ms")) }
            }
            if (list.isEmpty()) return null
            val ch = o.optJSONArray("chapters") ?: JSONArray()
            val chapters = (0 until ch.length()).mapNotNull { i ->
                ch.optJSONObject(i)?.let { AudioChapter(it.optString("title"), it.optInt("track"), it.optLong("ms")) }
            }
            val mk = o.optJSONArray("marks") ?: JSONArray()
            val marks = (0 until mk.length()).mapNotNull { i -> mk.optJSONObject(i)?.let { AudioMark(it.optInt("track"), it.optLong("ms"), it.optLong("at")) } }
            return Audiobook(
                id = o.optString("id").ifBlank { return null },
                title = o.optString("title"),
                author = o.optString("author"),
                tracks = list,
                chapters = chapters.ifEmpty { list.mapIndexed { i, t -> AudioChapter(t.title, i, 0) } },
                track = o.optInt("track").coerceIn(0, list.lastIndex),
                positionMs = o.optLong("pos"),
                speed = o.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 3f),
                bookId = o.optString("book").ifBlank { null },
                addedAt = o.optLong("added", System.currentTimeMillis()),
                lastPlayedAt = o.optLong("played").takeIf { it > 0 },
                marks = marks,
            )
        }
    }
}

class AudiobookRepository(private val context: Context) {
    private val file = File(context.filesDir, "audiobooks.json")
    private val coverDir = File(context.filesDir, "audio_covers")
    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Audiobook>> = _books.asStateFlow()

    private fun load(): List<Audiobook> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Audiobook::fromJson) }
    }.getOrDefault(emptyList())

    private fun save(list: List<Audiobook>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        tmp.renameTo(file)
    }

    private val prefs = context.getSharedPreferences("audiobooks", Context.MODE_PRIVATE)
    private val _manual = MutableStateFlow(prefs.getBoolean("manual", false))
    val manual: StateFlow<Boolean> = _manual.asStateFlow()

    @Synchronized
    fun reorder(ids: List<String>) {
        val byId = _books.value.associateBy { it.id }
        val list = ids.mapNotNull { byId[it] } + _books.value.filter { it.id !in ids }
        _books.value = list
        save(list)
        prefs.edit().putBoolean("manual", true).apply()
        _manual.value = true
    }

    fun find(id: String?): Audiobook? = id?.let { k -> _books.value.firstOrNull { it.id == k } }

    fun forBook(bookId: String): Audiobook? = _books.value.firstOrNull { it.bookId == bookId }

    @Synchronized
    fun update(id: String, change: (Audiobook) -> Audiobook) {
        val list = _books.value.map { if (it.id == id) change(it) else it }
        _books.value = list
        save(list)
    }

    @Synchronized
    private fun add(book: Audiobook) {
        val list = listOf(book) + _books.value
        _books.value = list
        save(list)
    }

    @Synchronized
    fun remove(id: String) {
        val gone = find(id) ?: return
        val list = _books.value.filterNot { it.id == id }
        _books.value = list
        save(list)
        cover(id).delete()
        gone.tracks.map { Uri.parse(it.uri) }.forEach { uri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    fun cover(id: String) = File(coverDir, "$id.img")

    suspend fun importFiles(uris: List<Uri>, folderName: String? = null, folderCover: Uri? = null): Audiobook? = withContext(Dispatchers.IO) {
        val audio = uris.filter { isAudio(name(it)) }.distinct().sortedWith(compareBy(NaturalOrder) { name(it) })
        if (audio.isEmpty()) return@withContext null
        audio.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        folderCover?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val id = UUID.randomUUID().toString()
        var album = ""
        var artist = ""
        var art: ByteArray? = null
        var chapters: List<AudioChapter> = emptyList()
        val tracks = audio.mapIndexed { i, uri ->
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (album.isBlank()) album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
                if (artist.isBlank()) {
                    artist = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                        ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)).orEmpty()
                }
                if (art == null) art = runCatching { r.embeddedPicture }.getOrNull()
                val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty().ifBlank { cleanName(name(uri)) }
                if (audio.size == 1) chapters = Mp4Chapters.read(context, uri, ms)
                AudioTrack(uri.toString(), title, ms)
            } catch (e: Exception) {
                AudioTrack(uri.toString(), cleanName(name(uri)), 0L)
            } finally {
                runCatching { r.release() }
            }
        }.filter { it.durationMs > 0 }
        if (tracks.isEmpty()) return@withContext null
        if (art == null && folderCover != null) art = runCatching { context.contentResolver.openInputStream(folderCover)?.use { it.readBytes() } }.getOrNull()
        art?.let {
            coverDir.mkdirs()
            cover(id).writeBytes(it)
        }
        val sameTitles = tracks.size > 1 && tracks.map { it.title }.distinct().size == 1
        val fixed = if (sameTitles) tracks.mapIndexed { i, t -> t.copy(title = "Часть ${i + 1}") } else tracks
        val title = album.ifBlank { folderName.orEmpty() }.ifBlank { if (fixed.size == 1) fixed[0].title else cleanName(name(audio.first())) }
        val book = Audiobook(
            id = id,
            title = title.trim(),
            author = artist.trim(),
            tracks = fixed,
            chapters = chapters.ifEmpty { fixed.mapIndexed { i, t -> AudioChapter(t.title, i, 0) } },
        )
        add(book)
        book
    }

    suspend fun importFolder(tree: Uri): Audiobook? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val found = mutableListOf<Pair<Uri, String>>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 2) walk(id, depth + 1)
                    } else {
                        found += DocumentsContract.buildDocumentUriUsingTree(tree, id) to name
                    }
                }
            }
        }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        runCatching { walk(rootId, 0) }
        val folder = rootId.substringAfterLast(':').substringAfterLast('/')
        val cover = found.firstOrNull { (_, n) -> n.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } && n.lowercase().let { "cover" in it || "folder" in it || "front" in it } }
            ?: found.firstOrNull { (_, n) -> n.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } }
        names.putAll(found.associate { it.first.toString() to it.second })
        importFiles(found.map { it.first }, cleanName(folder), cover?.first)
    }

    private val names = HashMap<String, String>()

    private fun name(uri: Uri): String {
        names[uri.toString()]?.let { return it }
        val n = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
        names[uri.toString()] = n
        return n
    }

    companion object {
        private val AUDIO = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "wma", "amr", "3gp")

        fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO

        fun cleanName(name: String): String =
            name.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifBlank { name }
    }
}

object NaturalOrder : Comparator<String> {
    private val parts = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val x = parts.findAll(a.lowercase()).map { it.value }.toList()
        val y = parts.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val n = p.trimStart('0').length.compareTo(q.trimStart('0').length)
                if (n != 0) n else p.trimStart('0').compareTo(q.trimStart('0'))
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

object Mp4Chapters {
    fun read(context: Context, uri: Uri, durationMs: Long): List<AudioChapter> = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch -> parse(ch, durationMs) }
        }
    }.getOrNull().orEmpty()

    private fun parse(ch: java.nio.channels.FileChannel, durationMs: Long): List<AudioChapter> {
        val size = ch.size()
        val moov = findBox(ch, 0, size, "moov") ?: return emptyList()
        val udta = findBox(ch, moov.first, moov.second, "udta") ?: return emptyList()
        val chpl = findBox(ch, udta.first, udta.second, "chpl") ?: return emptyList()
        val len = (chpl.second - chpl.first).toInt().coerceAtMost(1 shl 20)
        val buf = java.nio.ByteBuffer.allocate(len)
        ch.read(buf, chpl.first)
        buf.flip()
        val version = buf.get().toInt()
        buf.position(buf.position() + 3)
        if (version != 0) buf.position(buf.position() + 4)
        val count = buf.get().toInt() and 0xFF
        val out = mutableListOf<AudioChapter>()
        repeat(count) {
            if (buf.remaining() < 9) return@repeat
            val start = buf.long / 10_000
            val n = buf.get().toInt() and 0xFF
            if (buf.remaining() < n) return@repeat
            val bytes = ByteArray(n)
            buf.get(bytes)
            val title = String(bytes, Charsets.UTF_8).trim()
            if (start in 0..durationMs) out += AudioChapter(title.ifBlank { "Глава ${out.size + 1}" }, 0, start)
        }
        return if (out.size >= 2) out.sortedBy { it.startMs } else emptyList()
    }

    private fun findBox(ch: java.nio.channels.FileChannel, from: Long, to: Long, type: String): Pair<Long, Long>? {
        var at = from
        val head = java.nio.ByteBuffer.allocate(16)
        while (at + 8 <= to) {
            head.clear()
            head.limit(8)
            if (ch.read(head, at) < 8) return null
            head.flip()
            var size = head.int.toLong() and 0xFFFFFFFFL
            val name = String(ByteArray(4).also { head.get(it) }, Charsets.ISO_8859_1)
            var body = at + 8
            if (size == 1L) {
                head.clear()
                head.limit(8)
                if (ch.read(head, at + 8) < 8) return null
                head.flip()
                size = head.long
                body = at + 16
            } else if (size == 0L) {
                size = to - at
            }
            if (size < 8) return null
            if (name == type) return body to (at + size).coerceAtMost(to)
            at += size
        }
        return null
    }
}
