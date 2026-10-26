package com.flerty.wikiflerty.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
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

    /** Recently read books first, then the newest additions. */
    val books: StateFlow<List<Book>> = app.library.books
        .map { list -> list.sortedByDescending { it.lastReadAt ?: it.addedAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    /** A short status text while something slow is happening, null otherwise. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _openReader = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a book is ready and the reader screen should be shown. */
    val openReader = _openReader.asSharedFlow()

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

    fun open(book: Book) {
        viewModelScope.launch {
            _busy.value = "Открываю…"
            app.prepareReader(book)
                .onSuccess { _openReader.emit(Unit) }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть книгу") }
            _busy.value = null
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch {
            app.library.delete(book)
            _messages.emit("Удалено: ${book.title}")
        }
    }
}
