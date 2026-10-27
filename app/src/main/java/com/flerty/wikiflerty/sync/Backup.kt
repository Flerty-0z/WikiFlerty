package com.flerty.wikiflerty.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ai.AiSettings
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.DictionaryEntry
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.reader.ReaderSettings
import com.flerty.wikiflerty.ui.UiStyle
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What changed after merging data from another phone or a backup file. */
data class MergeReport(val notes: Int = 0, val words: Int = 0, val progress: Int = 0, val settings: Boolean = false) {
    operator fun plus(o: MergeReport) =
        MergeReport(notes + o.notes, words + o.words, progress + o.progress, settings || o.settings)

    fun describe(): String {
        val parts = buildList {
            if (progress > 0) add("прогресс книг: $progress")
            if (notes > 0) add("заметок: $notes")
            if (words > 0) add("слов: $words")
            if (settings) add("настройки")
        }
        return if (parts.isEmpty()) "Всё уже совпадало" else "Обновлено — " + parts.joinToString(", ")
    }
}

/**
 * One file with everything except the books themselves: reading progress, notes, dictionary,
 * settings and the look. Used both for cloud sync and for export/import.
 * Books are matched between phones by the fingerprint of the book file.
 * API keys are never included.
 */
object Backup {
    private const val FOREIGN = "fp:"

    fun snapshot(app: App): JSONObject {
        val books = app.library.books.value
        val fpById = books.associate { it.id to it.fingerprint }
        val state = app.sync.store.state.value
        fun ref(bookId: String?): String? = when {
            bookId == null -> null
            bookId.startsWith(FOREIGN) -> bookId.removePrefix(FOREIGN)
            else -> fpById[bookId]
        }
        return JSONObject().apply {
            put("format", "wikiflerty")
            put("version", 1)
            put("savedAt", System.currentTimeMillis())
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("books", JSONArray().apply {
                books.filter { it.fingerprint != null }.forEach { b ->
                    put(
                        ForeignBook(b.fingerprint!!, b.title, b.author, b.progress, b.locatorJson, b.lastReadAt ?: 0, b.metaJson().toString()).toJson(),
                    )
                }
                state.foreignBooks.forEach { put(it.toJson()) }
            })
            put("notes", JSONArray().apply {
                app.notes.notes.value.forEach { n ->
                    val book = ref(n.bookId) ?: return@forEach
                    put(n.toJson().put("bookId", JSONObject.NULL).put("book", book))
                }
            })
            put("dictionary", JSONArray().apply {
                app.dictionary.entries.value.forEach { e ->
                    put(e.toJson().put("bookId", JSONObject.NULL).put("book", ref(e.bookId) ?: JSONObject.NULL))
                }
            })
            put("stats", app.stats.toJson())
            put("deletedNotes", JSONObject(state.deletedNotes))
            put("deletedWords", JSONObject(state.deletedWords))
            put("settings", JSONObject().apply {
                put("changedAt", state.settingsChangedAt)
                put("reader", app.readerSettings.settings.value.toJson())
                put("style", app.style.style.value.toJson())
                put("ai", app.ai.settings.value.toJson())
            })
        }
    }

    /** Merges data from another phone into this one. Newer changes win; deletions are respected. */
    suspend fun merge(app: App, remote: JSONObject, applySettings: Boolean): MergeReport {
        if (remote.optString("format") != "wikiflerty") throw IllegalArgumentException("Это не файл WikiFlerty")
        val books = app.library.books.value
        val idByFp = books.filter { it.fingerprint != null }.associate { it.fingerprint!! to it.id }
        fun localBookId(fp: String?): String? = fp?.let { idByFp[it] ?: (FOREIGN + it) }

        // Deletions from both sides.
        val state = app.sync.store.state.value
        val deletedNotes = maxMerge(state.deletedNotes, remote.optJSONObject("deletedNotes").toLongMap())
        val deletedWords = maxMerge(state.deletedWords, remote.optJSONObject("deletedWords").toLongMap())

        // Notes
        val localNotes = app.notes.notes.value.associateBy { it.id }.toMutableMap()
        var notesChanged = 0
        remote.optJSONArray("notes").objects().forEach { j ->
            val bookId = localBookId(j.optString("book").takeIf { it.isNotEmpty() }) ?: return@forEach
            val note = runCatching { Note.fromJson(j.put("bookId", bookId)) }.getOrNull() ?: return@forEach
            val deletedAt = deletedNotes[note.id]
            if (deletedAt != null && deletedAt >= note.updatedAt) return@forEach
            val mine = localNotes[note.id]
            if (mine == null || note.updatedAt > mine.updatedAt) {
                localNotes[note.id] = note
                notesChanged++
            }
        }
        val notesAfter = localNotes.values.filter { n -> deletedNotes[n.id]?.let { it < n.updatedAt } ?: true }
        if (notesChanged > 0 || notesAfter.size != app.notes.notes.value.size) app.notes.replaceAll(notesAfter)

        // Dictionary
        val localWords = app.dictionary.entries.value.associateBy { it.id }.toMutableMap()
        var wordsChanged = 0
        remote.optJSONArray("dictionary").objects().forEach { j ->
            val fp = if (j.isNull("book")) null else j.optString("book").takeIf { it.isNotEmpty() }
            val entry = runCatching {
                DictionaryEntry.fromJson(j.put("bookId", localBookId(fp) ?: JSONObject.NULL))
            }.getOrNull() ?: return@forEach
            if (deletedWords.containsKey(entry.id) || localWords.containsKey(entry.id)) return@forEach
            localWords[entry.id] = entry
            wordsChanged++
        }
        val wordsAfter = localWords.values.filterNot { deletedWords.containsKey(it.id) }.sortedByDescending { it.createdAt }
        if (wordsChanged > 0 || wordsAfter.size != app.dictionary.entries.value.size) app.dictionary.replaceAll(wordsAfter)

        // Reading progress
        var progressChanged = 0
        val foreign = state.foreignBooks.associateBy { it.fingerprint }.toMutableMap()
        remote.optJSONArray("books").objects().forEach { j ->
            val fb = runCatching { ForeignBook.fromJson(j) }.getOrNull() ?: return@forEach
            val local = books.firstOrNull { it.fingerprint == fb.fingerprint }
            if (local != null) {
                if (fb.lastReadAt > (local.lastReadAt ?: 0L)) {
                    app.library.applyProgress(local.id, fb.locatorJson, fb.progress, fb.lastReadAt)
                    progressChanged++
                }
                fb.meta?.let { app.library.applyMeta(local.id, JSONObject(it)) }
            } else {
                val known = foreign[fb.fingerprint]
                val metaTime = { f: ForeignBook? -> f?.meta?.let { JSONObject(it).optLong("metaChangedAt") } ?: 0L }
                if (known == null || fb.lastReadAt > known.lastReadAt || metaTime(fb) > metaTime(known)) {
                    foreign[fb.fingerprint] = if (known != null && fb.lastReadAt < known.lastReadAt) known.copy(meta = fb.meta) else fb
                }
            }
        }

        // Reading statistics
        app.stats.merge(remote.optJSONObject("stats"))

        // Settings and look
        var settingsApplied = false
        val remoteSettings = remote.optJSONObject("settings")
        val remoteChangedAt = remoteSettings?.optLong("changedAt") ?: 0L
        if (applySettings && remoteSettings != null && remoteChangedAt > state.settingsChangedAt) {
            remoteSettings.optJSONObject("reader")?.let { app.readerSettings.replace(ReaderSettings.fromJson(it)) }
            remoteSettings.optJSONObject("style")?.let { app.style.replace(UiStyle.fromJson(it)) }
            remoteSettings.optJSONObject("ai")?.let { app.ai.store.replace(AiSettings.fromJson(it)) }
            settingsApplied = true
        }

        app.sync.store.update {
            it.copy(
                deletedNotes = deletedNotes,
                deletedWords = deletedWords,
                foreignBooks = foreign.values.toList(),
                settingsChangedAt = if (settingsApplied) remoteChangedAt else it.settingsChangedAt,
            )
        }
        return MergeReport(notesChanged, wordsChanged, progressChanged, settingsApplied)
    }

    /** A book was added on this phone: attach notes, words and progress that came from other phones. */
    suspend fun onBookAdded(app: App, book: Book) {
        val fp = book.fingerprint ?: return
        app.notes.remapBook(FOREIGN + fp, book.id)
        app.dictionary.remapBook(FOREIGN + fp, book.id)
        val foreign = app.sync.store.state.value.foreignBooks.firstOrNull { it.fingerprint == fp } ?: return
        app.library.applyProgress(book.id, foreign.locatorJson, foreign.progress, foreign.lastReadAt)
        foreign.meta?.let { app.library.applyMeta(book.id, JSONObject(it)) }
        app.sync.store.update { s -> s.copy(foreignBooks = s.foreignBooks.filterNot { it.fingerprint == fp }) }
    }

    // ------------------------------------------------------------ export / import files

    /** Writes a .wikiflerty file (a ZIP): backup.json and, if asked, the book files. */
    suspend fun export(app: App, context: Context, target: Uri, includeBooks: Boolean) = withContext(Dispatchers.IO) {
        val json = snapshot(app).toString()
        val out = context.contentResolver.openOutputStream(target) ?: throw IllegalStateException("Не удалось создать файл")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (includeBooks) {
                app.library.books.value.forEach { book ->
                    val file = app.library.bookFile(book)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("books/${book.fingerprint ?: book.id}.epub"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Reads a .wikiflerty file: adds the books inside, then merges everything else. */
    suspend fun import(app: App, context: Context, source: Uri): MergeReport = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        var backup: JSONObject? = null
        var booksAdded = 0
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw IllegalStateException("Не удалось открыть файл")
            val bytes = input.use { it.readBytes() }
            if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
                backup = JSONObject(String(bytes, Charsets.UTF_8))
            } else {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when {
                            entry.name == "backup.json" -> backup = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                            entry.name.startsWith("books/") && entry.name.endsWith(".epub") -> {
                                val file = File(tempDir, File(entry.name).name)
                                file.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
            tempDir.listFiles()?.forEach { file ->
                if (app.library.import(Uri.fromFile(file)).isSuccess) booksAdded++
            }
            val data = backup ?: throw IllegalArgumentException("В файле нет данных WikiFlerty")
            merge(app, data, applySettings = app.sync.store.state.value.syncSettings).also { lastImportedBooks = booksAdded }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** How many books the last import added (shown to the user). */
    var lastImportedBooks = 0
        private set

    private fun maxMerge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
        (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0L, b[it] ?: 0L) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
