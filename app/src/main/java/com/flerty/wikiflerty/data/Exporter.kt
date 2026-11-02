package com.flerty.wikiflerty.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {
    private val dateFormat get() = SimpleDateFormat("d MMM yyyy", Locale("ru"))

    fun notesMarkdown(notes: List<Note>, books: List<Book>): String = buildString {
        val byId = books.associateBy { it.id }
        appendLine("# Цитаты и заметки")
        appendLine()
        appendLine("_Экспорт из WikiFlerty · ${dateFormat.format(Date())}_")
        notes.groupBy { it.bookId }
            .toList()
            .sortedBy { (id, _) -> byId[id]?.title ?: "￿" }
            .forEach { (bookId, list) ->
                val book = byId[bookId]
                appendLine()
                appendLine("## ${book?.title ?: "Удалённая книга"}")
                if (!book?.author.isNullOrBlank()) appendLine("_${book!!.author}_")
                list.sortedBy { it.progression }.forEach { note ->
                    appendLine()
                    note.text.trim().lines().filter { it.isNotBlank() }.forEach { appendLine("> ${it.trim()}") }
                    if (note.note.isNotBlank()) {
                        appendLine()
                        appendLine(note.note.trim())
                    }
                    appendLine()
                    val meta = listOfNotNull(note.chapter?.takeIf { it.isNotBlank() }, dateFormat.format(Date(note.createdAt)))
                    appendLine("_${meta.joinToString(" · ")}_")
                }
            }
    }.trim() + "\n"

    fun notesText(notes: List<Note>, books: List<Book>): String = buildString {
        val byId = books.associateBy { it.id }
        notes.groupBy { it.bookId }.forEach { (bookId, list) ->
            val book = byId[bookId]
            appendLine(listOfNotNull(book?.title ?: "Удалённая книга", book?.author?.takeIf { it.isNotBlank() }).joinToString(" — "))
            appendLine()
            list.sortedBy { it.progression }.forEach { note ->
                appendLine("«${note.text.trim()}»")
                if (note.note.isNotBlank()) appendLine("— ${note.note.trim()}")
                note.chapter?.takeIf { it.isNotBlank() }?.let { appendLine("($it)") }
                appendLine()
            }
            appendLine()
        }
    }.trim() + "\n"

    fun wordsMarkdown(entries: List<DictionaryEntry>): String = buildString {
        appendLine("# Словарь")
        appendLine()
        appendLine("_Экспорт из WikiFlerty · ${dateFormat.format(Date())} · слов: ${entries.size}_")
        entries.sortedBy { it.word.lowercase() }.forEach { entry ->
            appendLine()
            appendLine("## ${entry.word.trim()}")
            appendLine()
            appendLine(entry.definition.trim())
            if (entry.context.isNotBlank()) {
                appendLine()
                entry.context.trim().lines().filter { it.isNotBlank() }.forEach { appendLine("> ${it.trim()}") }
            }
            entry.bookTitle?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("_${it}_")
            }
        }
    }.trim() + "\n"

    fun wordsCsv(entries: List<DictionaryEntry>): String = buildString {
        append('﻿')
        appendLine("Слово;Значение;Контекст;Книга")
        entries.forEach { e ->
            appendLine(listOf(e.word, e.definition, e.context, e.bookTitle.orEmpty()).joinToString(";") { csv(it) })
        }
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"").replace(Regex("\r?\n"), " ") + "\""
}
