package com.flerty.wikiflerty.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.flerty.wikiflerty.reader.ReadiumServices
import com.flerty.wikiflerty.data.convert.BookConverter
import com.flerty.wikiflerty.data.convert.PdfSupport
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.cover

class LibraryRepository(
    private val context: Context,
    private val readium: ReadiumServices,
) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val libraryFile = File(context.filesDir, "library.json")
    private val mutex = Mutex()

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun bookFile(book: Book): File = File(booksDir, book.fileName)

    var onBookAdded: (suspend (Book) -> Unit)? = null

    fun coverFile(book: Book): File? = book.coverFileName?.let { File(coversDir, it) }

    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(booksDir, "$id.epub")
        val source = File(booksDir, "$id.src")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Не удалось прочитать файл"))
            input.use { stream -> source.outputStream().use { stream.copyTo(it) } }
            val kind = BookConverter.detect(source)
            var converted: FileMeta? = null
            when (kind) {
                BookConverter.Kind.Epub -> if (!source.renameTo(file)) {
                    source.copyTo(file, overwrite = true)
                    source.delete()
                }
                BookConverter.Kind.Fb2, BookConverter.Kind.Fb2Zip, BookConverter.Kind.Txt -> {
                    val name = displayName(uri)?.substringBeforeLast('.')?.removeSuffix(".fb2")?.takeIf { it.isNotBlank() } ?: "Без названия"
                    try {
                        converted = BookConverter.toEpub(source, kind, file, name, fingerprintOf(source).take(24))
                    } catch (e: Throwable) {
                        file.delete()
                        val what = if (kind == BookConverter.Kind.Txt) "текстовый файл" else "FB2"
                        return@withContext Result.failure(Exception("Не удалось прочитать $what: файл повреждён или в необычном формате"))
                    } finally {
                        source.delete()
                    }
                }
                BookConverter.Kind.Pdf -> {
                    file.delete()
                    return@withContext importPdf(id, source, uri)
                }
                BookConverter.Kind.Unknown -> {
                    source.delete()
                    return@withContext Result.failure(Exception("Этот формат не поддерживается. Подойдут EPUB, FB2, TXT и PDF"))
                }
            }
            val fingerprint = fingerprintOf(file)

            val publication = readium.open(file).getOrElse {
                file.delete()
                return@withContext Result.failure(it)
            }
            try {
                val title = publication.metadata.title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: displayName(uri)?.substringBeforeLast('.')
                    ?: "Без названия"
                val author = publication.metadata.authors.joinToString(", ") { it.name }

                _books.value.firstOrNull {
                    it.fingerprint == fingerprint || (it.title == title && it.author == author)
                }?.let { existing ->
                    file.delete()
                    return@withContext Result.success(existing)
                }

                val coverName = publication.cover()?.let { bitmap ->
                    val name = "$id.jpg"
                    File(coversDir, name).outputStream().use { out ->
                        bitmap.scaledToHeight(900).compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    name
                }

                val meta = (converted ?: FileMeta()).over(runCatching { BookMetaReader.read(publication, file) }.getOrNull() ?: FileMeta())
                writeDescription(id, meta.description)
                val book = Book(
                    id = id,
                    title = title,
                    author = author,
                    fileName = file.name,
                    coverFileName = coverName,
                    addedAt = System.currentTimeMillis(),
                    lastReadAt = null,
                    progress = 0.0,
                    locatorJson = null,
                    fingerprint = fingerprint,
                    facts = meta.facts,
                    series = meta.series,
                    scan = BookMetaReader.Version,
                )
                update { it + book }
                onBookAdded?.invoke(book)
                Result.success(_books.value.firstOrNull { it.id == book.id } ?: book)
            } finally {
                publication.close()
            }
        } catch (e: Throwable) {
            file.delete()
            source.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(if (e is OutOfMemoryError) Exception("Книга слишком большая для памяти телефона") else e)
        }
    }

    fun pdfFile(book: Book): File = File(booksDir, "${book.id}.pdf")

    private fun buildPdfText(id: String, pdf: File, fallbackTitle: String, identifier: String): com.flerty.wikiflerty.data.convert.PdfTextResult? {
        val epub = File(booksDir, "$id.epub")
        return try {
            com.flerty.wikiflerty.data.convert.PdfTextConverter.convert(context, pdf, epub, fallbackTitle, identifier)?.also { result ->
                val mapFile = com.flerty.wikiflerty.data.convert.PdfPageMap.file(context, id)
                mapFile.parentFile?.mkdirs()
                mapFile.writeText(result.map.toJson().toString())
            } ?: run {
                epub.delete()
                null
            }
        } catch (e: Throwable) {
            epub.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun importPdf(id: String, source: File, uri: Uri): Result<Book> {
        val file = File(booksDir, "$id.pdf")
        try {
            if (!source.renameTo(file)) {
                source.copyTo(file, overwrite = true)
                source.delete()
            }
            val meta = runCatching { PdfSupport.inspect(file) }.getOrElse { e ->
                file.delete()
                val locked = e is SecurityException || e.message?.contains("password", true) == true
                return Result.failure(Exception(if (locked) "PDF защищён паролем" else "Не удалось открыть PDF: файл повреждён"))
            }
            if (meta.pages <= 0) {
                file.delete()
                return Result.failure(Exception("В PDF нет страниц"))
            }
            val fingerprint = fingerprintOf(file)
            val fileTitle = displayName(uri)?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.takeIf { it.isNotBlank() }
            _books.value.firstOrNull { it.fingerprint == fingerprint }?.let { existing ->
                file.delete()
                return Result.success(existing)
            }
            val text = buildPdfText(id, file, meta.title ?: fileTitle ?: "Без названия", fingerprint.take(24))
            val title = text?.title ?: meta.title ?: fileTitle ?: "Без названия"
            val author = text?.author ?: meta.author.orEmpty()
            val coverName = PdfSupport.cover(file, 600)?.takeUnless { PdfSupport.isBlank(it) }?.let { bitmap ->
                val name = "$id.jpg"
                File(coversDir, name).outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
                bitmap.recycle()
                name
            }
            val book = Book(
                id = id,
                title = title,
                author = author,
                fileName = if (text != null) "$id.epub" else file.name,
                coverFileName = coverName,
                addedAt = System.currentTimeMillis(),
                lastReadAt = null,
                progress = 0.0,
                locatorJson = null,
                fingerprint = fingerprint,
                positions = if (text != null) 0 else meta.pages,
                pdf = true,
            )
            update { it + book }
            onBookAdded?.invoke(book)
            return Result.success(_books.value.firstOrNull { it.id == book.id } ?: book)
        } catch (e: Throwable) {
            file.delete()
            File(booksDir, "$id.epub").delete()
            source.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Result.failure(if (e is OutOfMemoryError) Exception("Книга слишком большая для памяти телефона") else Exception("Не удалось открыть PDF"))
        }
    }

    fun needsPdfText(book: Book): Boolean = when {
        !book.isPdf -> false
        !book.pdf -> true
        book.pdfText -> (com.flerty.wikiflerty.data.convert.PdfPageMap.load(context, book.id)?.version ?: 0) < com.flerty.wikiflerty.data.convert.PdfTextConverter.VERSION
        else -> false
    }

    suspend fun ensurePdfText(book: Book): Book = withContext(Dispatchers.IO) {
        if (!needsPdfText(book)) return@withContext book
        val pdf = pdfFile(book)
        if (!pdf.exists()) return@withContext book
        val oldMap = com.flerty.wikiflerty.data.convert.PdfPageMap.load(context, book.id)
        val oldPage = book.locatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
            ?.let { com.flerty.wikiflerty.reader.PdfLocators.pageOf(it, oldMap, oldMap?.pages ?: book.positions) }
        val text = buildPdfText(book.id, pdf, book.title, (book.fingerprint ?: book.id).take(24))
        val total = text?.map?.pages ?: book.positions
        val blankCover = book.coverFileName == "${book.id}.jpg" && runCatching {
            android.graphics.BitmapFactory.decodeFile(File(coversDir, "${book.id}.jpg").path)?.let { PdfSupport.isBlank(it) }
        }.getOrNull() == true
        if (blankCover) File(coversDir, "${book.id}.jpg").delete()
        update { list ->
            list.map {
                if (it.id != book.id) {
                    it
                } else {
                    it.copy(
                        pdf = true,
                        fileName = if (text != null) "${book.id}.epub" else it.fileName,
                        coverFileName = if (blankCover) null else it.coverFileName,
                        locatorJson = oldPage?.let { p -> com.flerty.wikiflerty.reader.PdfLocators.pageLocator(p, total).toJSON().toString() } ?: it.locatorJson,
                    )
                }
            }
        }
        _books.value.firstOrNull { it.id == book.id } ?: book
    }

    suspend fun addPaper(title: String, author: String, pages: Int, cover: Uri?): Book = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val coverName = cover?.let { saveCover(id, it) }
        val book = Book(
            id = id,
            title = title.trim().ifBlank { "Без названия" },
            author = author.trim(),
            fileName = "$id.paper",
            coverFileName = coverName,
            addedAt = System.currentTimeMillis(),
            lastReadAt = null,
            progress = 0.0,
            locatorJson = null,
            fingerprint = "paper:$id",
            positions = pages.coerceAtLeast(1),
            paper = true,
            metaChangedAt = System.currentTimeMillis(),
        )
        update { it + book }
        book
    }

    suspend fun restorePaper(fingerprint: String, title: String, author: String, progress: Double, lastReadAt: Long, meta: org.json.JSONObject?) {
        if (_books.value.any { it.fingerprint == fingerprint }) return
        val id = UUID.randomUUID().toString()
        val base = Book(
            id = id,
            title = title,
            author = author,
            fileName = "$id.paper",
            coverFileName = null,
            addedAt = System.currentTimeMillis(),
            lastReadAt = lastReadAt.takeIf { it > 0 },
            progress = progress,
            locatorJson = null,
            fingerprint = fingerprint,
            paper = true,
        )
        val book = meta?.let { base.withMeta(it) } ?: base
        update { it + book.copy(paper = true) }
    }

    suspend fun editPaper(bookId: String, title: String, author: String, pages: Int, cover: Uri?) {
        val coverName = cover?.let { withContext(Dispatchers.IO) { saveCover(bookId, it) } }
        editMeta(bookId) { b ->
            val total = pages.coerceAtLeast(1)
            b.copy(
                title = title.trim().ifBlank { b.title },
                author = author.trim(),
                positions = total,
                coverFileName = coverName ?: b.coverFileName,
                progress = if (b.positions > 0) (b.progress * b.positions / total).coerceIn(0.0, 1.0) else b.progress,
            )
        }
    }

    suspend fun editInfo(bookId: String, title: String, author: String, description: String) {
        editMeta(bookId) { b ->
            b.copy(
                title = title.trim().ifBlank { b.title },
                author = author.trim(),
                customDescription = description.trim(),
                edited = true,
            )
        }
    }

    fun customCover(book: Book): File? {
        val name = book.coverFileName ?: return null
        if (!book.paper && name == "${book.id}.jpg") return null
        return File(coversDir, name).takeIf { it.exists() }
    }

    suspend fun applyCover(bookId: String, bytes: ByteArray, at: Long) {
        val name = "$bookId-$at.jpg"
        val file = File(coversDir, name)
        withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
            file.setLastModified(at)
        }
        val old = _books.value.firstOrNull { it.id == bookId }?.let { coverFile(it) }
        update { list -> list.map { if (it.id == bookId) it.copy(coverFileName = name) else it } }
        withContext(Dispatchers.IO) { old?.takeIf { it.name != name }?.delete() }
    }

    suspend fun setCover(bookId: String, cover: Uri) {
        val name = withContext(Dispatchers.IO) { saveCover(bookId, cover) } ?: return
        val old = _books.value.firstOrNull { it.id == bookId }?.let { coverFile(it) }
        editMeta(bookId) { it.copy(coverFileName = name) }
        withContext(Dispatchers.IO) { old?.takeIf { it.name != name }?.delete() }
    }

    suspend fun setPaperPage(bookId: String, page: Int) {
        val now = System.currentTimeMillis()
        editMeta(bookId) { b ->
            val total = b.positions.coerceAtLeast(1)
            val p = page.coerceIn(0, total)
            val done = p >= total
            b.copy(
                progress = p.toDouble() / total,
                lastReadAt = now,
                status = when {
                    done -> ReadStatus.Finished
                    p > 0 && b.status != ReadStatus.Finished -> ReadStatus.Reading
                    p > 0 -> ReadStatus.Reading
                    else -> b.status
                },
                finishedAt = if (done) (b.finishedAt ?: now) else b.finishedAt,
            )
        }
    }

    private fun saveCover(id: String, uri: Uri): String? = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return@runCatching null
        val name = "$id-${System.currentTimeMillis()}.jpg"
        File(coversDir, name).outputStream().use { out -> bitmap.scaledToHeight(900).compress(Bitmap.CompressFormat.JPEG, 88, out) }
        name
    }.getOrNull()

    private val infoDir = File(context.filesDir, "bookinfo").apply { mkdirs() }

    private fun writeDescription(id: String, description: String) {
        runCatching { File(infoDir, "$id.json").writeText(org.json.JSONObject().put("v", 2).put("description", description).toString()) }
    }

    private fun cachedDescription(id: String): String? = runCatching {
        val cache = File(infoDir, "$id.json")
        if (!cache.exists()) return@runCatching null
        val j = org.json.JSONObject(cache.readText())
        if (j.optInt("v") >= 2 || j.optString("description").isNotBlank()) j.optString("description") else null
    }.getOrNull()

    suspend fun info(book: Book): BookInfo = withContext(Dispatchers.IO) {
        val current = _books.value.firstOrNull { it.id == book.id } ?: book
        fun pack(description: String) = BookInfo(description, current.fact(BookFacts.Publisher), current.fact(BookFacts.Year), current.fact(BookFacts.Language))
        if (book.paper || (book.isPdf && !book.pdfText)) return@withContext pack("")
        cachedDescription(book.id)?.let { return@withContext pack(it) }
        val meta = runCatching {
            val publication = readium.open(bookFile(book)).getOrThrow()
            try {
                BookMetaReader.read(publication, bookFile(book))
            } finally {
                publication.close()
            }
        }.getOrNull() ?: return@withContext pack("")
        writeDescription(book.id, meta.description)
        pack(meta.description)
    }

    suspend fun ensureScan() = withContext(Dispatchers.IO) {
        val pending = _books.value.filter { it.scan < BookMetaReader.Version }
        pending.chunked(12).forEach { chunk ->
            val found = HashMap<String, FileMeta?>()
            for (book in chunk) {
                found[book.id] = if (book.paper || (book.isPdf && !book.pdfText)) {
                    FileMeta()
                } else {
                    runCatching {
                        val file = bookFile(book)
                        val publication = readium.open(file).getOrThrow()
                        try {
                            BookMetaReader.read(publication, file)
                        } finally {
                            publication.close()
                        }
                    }.getOrNull()
                }
                found[book.id]?.description?.takeIf { it.isNotBlank() }?.let { writeDescription(book.id, it) }
                kotlinx.coroutines.yield()
            }
            update { list ->
                list.map { b ->
                    if (b.id !in found) b
                    else {
                        val meta = found[b.id]
                        b.copy(
                            facts = meta?.facts.orEmpty() + b.facts,
                            series = b.series.ifEmpty { meta?.series.orEmpty() },
                            scan = BookMetaReader.Version,
                        )
                    }
                }
            }
        }
    }

    suspend fun editDetails(bookId: String, facts: Map<String, String>, series: List<BookSeries>) = editMeta(bookId) { b ->
        b.copy(facts = b.facts + facts.mapValues { it.value.trim() }, series = series.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase() })
    }

    suspend fun removeFromSeries(bookId: String, name: String) = editMeta(bookId) { b ->
        b.copy(series = b.series.filterNot { it.name.equals(name, true) })
    }

    suspend fun addToSeries(bookId: String, name: String, number: Double?) = editMeta(bookId) { b ->
        b.copy(series = b.series.filterNot { it.name.equals(name, true) } + BookSeries(name, number))
    }

    suspend fun fillDetails(bookId: String, description: String?, facts: Map<String, String>, series: List<BookSeries>) = editMeta(bookId) { b ->
        val missing = facts.filter { (k, v) -> v.isNotBlank() && b.fact(k).isBlank() }
        val hasDescription = !b.customDescription.isNullOrBlank() || !cachedDescription(b.id).isNullOrBlank()
        b.copy(
            facts = b.facts + missing,
            series = b.series.ifEmpty { series },
            customDescription = if (!hasDescription && !description.isNullOrBlank()) description.trim() else b.customDescription,
        )
    }

    suspend fun setTags(bookId: String, tags: List<String>) = editMeta(bookId) { b ->
        b.copy(tags = tags.map { it.replace('|', ' ').trim().trimStart('#').trim() }.filter { it.removePrefix("!").isNotBlank() }.distinctBy { it.removePrefix("!").lowercase() })
    }

    suspend fun renameTag(from: String, to: String?) {
        val now = System.currentTimeMillis()
        val target = to?.trim()?.trimStart('#')?.trim()?.takeIf { it.isNotEmpty() }
        update { list ->
            list.map { b ->
                if (b.tags.none { it.removePrefix("!").equals(from, true) }) b
                else b.copy(
                    tags = b.tags
                        .mapNotNull { t -> if (t.removePrefix("!").equals(from, true)) target?.let { if (t.startsWith("!")) "!$it" else it } else t }
                        .distinctBy { it.removePrefix("!").lowercase() },
                    metaChangedAt = now,
                )
            }
        }
    }

    suspend fun addTag(bookId: String, tag: String) = editMeta(bookId) { b ->
        if (b.tags.any { it.removePrefix("!").equals(tag, true) }) b else b.copy(tags = b.tags + tag)
    }

    suspend fun ensureFingerprints() = withContext(Dispatchers.IO) {
        val missing = _books.value.filter { it.fingerprint == null }
        if (missing.isEmpty()) return@withContext
        val computed = missing.associate { it.id to runCatching { fingerprintOf(bookFile(it)) }.getOrNull() }
        update { list -> list.map { b -> computed[b.id]?.let { b.copy(fingerprint = it) } ?: b } }
    }

    suspend fun applyProgress(bookId: String, locatorJson: String?, progress: Double, lastReadAt: Long) {
        update { list ->
            list.map {
                if (it.id == bookId && lastReadAt > (it.lastReadAt ?: 0L)) {
                    it.copy(
                        locatorJson = locatorJson ?: it.locatorJson,
                        progress = progress,
                        lastReadAt = lastReadAt,
                        status = if (it.status == ReadStatus.New && progress > 0.0) ReadStatus.Reading else it.status,
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun fingerprintOf(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        update { list -> list.filterNot { it.id == book.id } }
        bookFile(book).delete()
        coverFile(book)?.delete()
        if (book.isPdf) {
            pdfFile(book).delete()
            File(context.filesDir, "pdfmeta").listFiles()?.filter { it.name.startsWith(book.id) }?.forEach { it.delete() }
        }
    }

    suspend fun markOpened(bookId: String) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == bookId) it.copy(lastReadAt = now) else it } }
    }

    suspend fun saveProgress(bookId: String, locator: Locator) {
        val json = locator.toJSON().toString()
        val now = System.currentTimeMillis()
        update { list ->
            list.map {
                if (it.id != bookId) {
                    it
                } else {
                    val started = it.status == ReadStatus.New || it.status == ReadStatus.Planned
                    it.copy(
                        locatorJson = json,
                        progress = locator.locations.totalProgression ?: it.progress,
                        lastReadAt = now,
                        status = if (started) ReadStatus.Reading else it.status,
                        metaChangedAt = if (started) now else it.metaChangedAt,
                    )
                }
            }
        }
    }

    suspend fun setStatus(bookId: String, status: ReadStatus) = editMeta(bookId) {
        it.copy(status = status, finishedAt = if (status == ReadStatus.Finished) System.currentTimeMillis() else it.finishedAt)
    }

    suspend fun setRating(bookId: String, rating: Float?, review: String) = editMeta(bookId) {
        it.copy(
            rating = rating,
            review = review.trim(),
            ratedAt = System.currentTimeMillis(),
            status = ReadStatus.Finished,
            finishedAt = it.finishedAt ?: System.currentTimeMillis(),
        )
    }

    suspend fun setFavorite(bookId: String, favorite: Boolean) = editMeta(bookId) { it.copy(favorite = favorite) }

    suspend fun setShelves(bookId: String, shelves: List<String>) = editMeta(bookId) { it.copy(shelves = shelves.distinct()) }

    suspend fun renameShelf(from: String, to: String?) {
        val now = System.currentTimeMillis()
        update { list ->
            list.map { b ->
                if (from !in b.shelves) b else b.copy(
                    shelves = b.shelves.mapNotNull { if (it == from) to else it }.distinct(),
                    metaChangedAt = now,
                )
            }
        }
    }

    suspend fun setPositions(bookId: String, positions: Int) {
        if (positions <= 0 || _books.value.firstOrNull { it.id == bookId }?.positions == positions) return
        update { list -> list.map { if (it.id == bookId) it.copy(positions = positions) else it } }
    }

    suspend fun applyMeta(bookId: String, meta: org.json.JSONObject) {
        update { list ->
            list.map {
                if (it.id == bookId && meta.optLong("metaChangedAt") > it.metaChangedAt) it.withMeta(meta) else it
            }
        }
    }

    private suspend fun editMeta(bookId: String, change: (Book) -> Book) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == bookId) change(it).copy(metaChangedAt = now) else it } }
    }

    private suspend fun update(transform: (List<Book>) -> List<Book>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_books.value)
            _books.value = updated
            scheduleSave()
        }
    }

    private val writer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val saveLock = Any()
    private var pendingSave: kotlinx.coroutines.Job? = null
    private val jsonCache = HashMap<String, Pair<Book, String>>()

    private fun scheduleSave() {
        synchronized(saveLock) {
            pendingSave?.cancel()
            pendingSave = writer.launch {
                kotlinx.coroutines.delay(400)
                persist()
            }
        }
    }

    fun flush() {
        synchronized(saveLock) {
            pendingSave?.cancel()
            pendingSave = null
        }
        persist()
    }

    private fun persist() = synchronized(jsonCache) { runCatching { save(_books.value) } }

    private fun load(): List<Book> = try {
        if (!libraryFile.exists()) {
            emptyList()
        } else {
            val array = JSONObject(libraryFile.readText()).optJSONArray("books") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                runCatching { Book.fromJson(array.getJSONObject(i)) }.getOrNull()
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(books: List<Book>) {
        val alive = HashSet<String>(books.size * 2)
        val text = StringBuilder(books.size * 600 + 64)
        text.append("{\"version\":1,\"books\":[")
        books.forEachIndexed { i, book ->
            alive += book.id
            val cached = jsonCache[book.id]
            val json = if (cached != null && cached.first === book) cached.second else book.toJson().toString().also { jsonCache[book.id] = book to it }
            if (i > 0) text.append(',')
            text.append(json)
        }
        text.append("]}")
        jsonCache.keys.retainAll(alive)
        val out = text.toString()
        val tmp = File(libraryFile.parentFile, "library.json.tmp")
        tmp.writeText(out)
        if (!tmp.renameTo(libraryFile)) {
            libraryFile.writeText(out)
            tmp.delete()
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    private fun Bitmap.scaledToHeight(maxHeight: Int): Bitmap {
        if (height <= maxHeight) return this
        val width = (width * maxHeight.toFloat() / height).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, width, maxHeight, true)
    }
}

data class BookInfo(val description: String = "", val publisher: String = "", val year: String = "", val language: String = "") {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("description", description).put("publisher", publisher).put("year", year).put("language", language)

    companion object {
        fun fromJson(j: org.json.JSONObject) = BookInfo(j.optString("description"), j.optString("publisher"), j.optString("year"), j.optString("language"))
    }
}
