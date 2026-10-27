package com.flerty.wikiflerty.sync

import com.flerty.wikiflerty.App

/**
 * Keeps what moving data between phones needs: deleted notes and words (so a backup
 * from another phone does not bring them back), and the time settings last changed.
 * The data itself travels in a backup file (export / import).
 */
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

    fun settingsChanged() {
        store.update { it.copy(settingsChangedAt = System.currentTimeMillis()) }
    }
}
