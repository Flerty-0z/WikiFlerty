package com.flerty.wikiflerty.sync

import com.flerty.wikiflerty.App

class SyncManager(@Suppress("unused") private val app: App) {
    val store = SyncStateStore(app)

    fun noteDeleted(ids: List<String>) {
        val now = System.currentTimeMillis()
        store.update { it.copy(deletedNotes = it.deletedNotes + ids.associateWith { now }) }
    }

    fun wordDeleted(ids: List<String>) {
        val now = System.currentTimeMillis()
        store.update { it.copy(deletedWords = it.deletedWords + ids.associateWith { now }) }
    }

    fun bookmarkDeleted(ids: List<String>) {
        val now = System.currentTimeMillis()
        store.update { it.copy(deletedBookmarks = it.deletedBookmarks + ids.associateWith { now }) }
    }

    fun characterDeleted(ids: List<String>) {
        val now = System.currentTimeMillis()
        store.update { it.copy(deletedCharacters = it.deletedCharacters + ids.associateWith { now }) }
    }

    fun settingsChanged() {
        if (Extras.applying) return
        store.update { it.copy(settingsChangedAt = System.currentTimeMillis()) }
    }

    fun libraryChanged() {
        store.update { it.copy(libraryChangedAt = System.currentTimeMillis()) }
    }

    fun flashcardsChanged() {
        store.update { it.copy(flashcardsChangedAt = System.currentTimeMillis()) }
    }
}
