package com.flerty.wikiflerty.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.data.DictionaryEntry
import org.readium.r2.shared.publication.Locator
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App

    val books: StateFlow<List<Book>> = app.library.books
        .map { list -> list.sortedByDescending { it.lastReadAt ?: it.addedAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _openReader = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openReader = _openReader.asSharedFlow()
    private val _openPdf = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
    val openPdf = _openPdf.asSharedFlow()

    fun coverFile(book: Book): File? = app.library.coverFile(book)

    fun import(uri: Uri, openAfterImport: Boolean = false) {
        viewModelScope.launch {
            _busy.value = "Добавляю книгу…"
            app.library.import(uri)
                .onSuccess { book ->
                    if (openAfterImport) open(book) else _messages.emit("Добавлено: ${book.title}")
                }
                .onFailure { _messages.emit(it.message ?: "Не удалось добавить книгу") }
            _busy.value = null
        }
    }

    fun importAll(uris: List<Uri>) {
        app.imports.add(uris)
    }

    init {
        app.imports.onFinished = { p ->
            _messages.tryEmit(
                buildString {
                    append("Добавлено книг: ${p.added}")
                    if (p.same > 0) append(" · уже были: ${p.same}")
                    if (p.failed > 0) append(" · не открылись: ${p.failed}")
                },
            )
        }
        viewModelScope.launch {
            app.imports.progress.collect { p ->
                if (p != null) {
                    _busy.value = "Добавляю книги ${p.done + 1}/${p.total}…"
                } else if (_busy.value?.startsWith("Добавляю книги") == true) {
                    _busy.value = null
                }
            }
        }
    }

    val notes: StateFlow<List<Note>> = app.notes.notes

    fun open(book: Book, startAt: Locator? = null) {
        if (book.paper) {
            _messages.tryEmit("Это бумажная книга — отмечай чтение в её карточке")
            return
        }
        if (book.isPdf) {
            openPdf(book, startAt)
            return
        }
        viewModelScope.launch {
            _busy.value = "Открываю…"
            app.prepareReader(book, startAt)
                .onSuccess { _openReader.emit(Unit) }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть книгу") }
            _busy.value = null
        }
    }

    private fun openPdf(book: Book, startAt: Locator?) {
        viewModelScope.launch {
            var b = book
            if (app.library.needsPdfText(b)) {
                _busy.value = "Готовлю PDF…"
                b = app.library.ensurePdfText(b)
                _busy.value = null
            }
            val map = if (b.pdfText) com.flerty.wikiflerty.data.convert.PdfPageMap.load(app, b.id) else null
            val original = !b.pdfText
            if (original) {
                if (!app.library.pdfFile(b).exists()) {
                    _messages.emit("Файл книги не найден")
                    return@launch
                }
                val page = startAt?.let { com.flerty.wikiflerty.reader.PdfLocators.pageOf(it, map, map?.pages ?: b.positions) } ?: -1
                _openPdf.emit(b.id to page)
                return@launch
            }
            val saved = b.locatorJson?.let { runCatching { Locator.fromJSON(org.json.JSONObject(it)) }.getOrNull() }
            val start = com.flerty.wikiflerty.reader.PdfLocators.toText(startAt ?: saved, map)
            _busy.value = "Открываю…"
            app.prepareReader(b.copy(locatorJson = null), start)
                .onSuccess { _openReader.emit(Unit) }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть книгу") }
            _busy.value = null
        }
    }

    fun openById(bookId: String) {
        val book = app.library.books.value.firstOrNull { it.id == bookId } ?: return
        open(book)
    }

    fun openNote(note: Note) {
        val book = app.library.books.value.firstOrNull { it.id == note.bookId }
        if (book == null) {
            _messages.tryEmit("Книга этой заметки удалена")
            return
        }
        open(book, note.locator)
    }

    val dictionary: StateFlow<List<DictionaryEntry>> = app.dictionary.entries

    fun openDictionaryEntry(entry: DictionaryEntry) {
        val book = app.library.books.value.firstOrNull { it.id == entry.bookId }
        if (book == null) {
            _messages.tryEmit("Книга этого слова удалена")
            return
        }
        open(book, entry.locator)
    }

    fun deleteDictionaryEntry(entry: DictionaryEntry) {
        viewModelScope.launch { app.dictionary.delete(entry.id) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { app.notes.delete(note.id) }
    }

    fun delete(book: Book) {
        viewModelScope.launch {
            app.library.delete(book)
            app.notes.deleteForBook(book.id)
            app.bookmarks.deleteForBook(book.id)
            app.characters.deleteForBook(book.id)
            if (book.paper) app.paper.removeBook(book)
            _messages.emit("Удалено: ${book.title}")
        }
    }
}
