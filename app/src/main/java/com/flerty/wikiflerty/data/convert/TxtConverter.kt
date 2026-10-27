package com.flerty.wikiflerty.data.convert

import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.XHTML_END
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.esc
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.xhtmlHead
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class TxtConverter(private val identifier: String) {
    fun convert(bytes: ByteArray, target: File, title: String) {
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "")
        val language = if (Regex("[а-яё]", RegexOption.IGNORE_CASE).containsMatchIn(text.take(20_000))) "ru" else "en"
        val paragraphs = paragraphs(text)
        val book = EpubBuilder(identifier, title, emptyList(), language)

        val parts = mutableListOf<Pair<String?, MutableList<String>>>()
        var current: Pair<String?, MutableList<String>> = null to mutableListOf()
        parts += current
        paragraphs.forEach { p ->
            if (isHeading(p)) {
                current = p to mutableListOf()
                parts += current
            } else {
                if (current.second.size >= 400 && current.first == null) {
                    current = null to mutableListOf()
                    parts += current
                }
                current.second += p
            }
        }

        var index = 0
        parts.filter { it.first != null || it.second.isNotEmpty() }.forEach { (heading, body) ->
            index++
            val href = "part$index.xhtml"
            val html = StringBuilder(xhtmlHead(title, language))
            if (index == 1 && heading == null) html.append("<h1>${esc(title)}</h1>\n")
            if (heading != null) {
                html.append("<h2>${esc(heading)}</h2>\n")
                book.toc += TocEntry(heading, href)
            }
            body.forEach { html.append("<p>${esc(it)}</p>\n") }
            html.append(XHTML_END)
            book.addPage(href, html.toString())
        }
        if (index == 0) book.addPage("part1.xhtml", xhtmlHead(title, language) + "<h1>${esc(title)}</h1>\n" + XHTML_END)
        if (book.toc.isNotEmpty() && parts.first().second.isNotEmpty()) book.toc.add(0, TocEntry(title, "part1.xhtml"))
        book.write(target)
    }

    private fun paragraphs(text: String): List<String> {
        val lines = text.lines()
        val blank = lines.count { it.isBlank() }
        val byBlank = blank > lines.size * 0.15
        val result = mutableListOf<String>()
        if (byBlank) {
            val buffer = StringBuilder()
            lines.forEach { line ->
                if (line.isBlank()) {
                    if (buffer.isNotBlank()) result += buffer.toString().trim()
                    buffer.setLength(0)
                } else {
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(line.trim())
                }
            }
            if (buffer.isNotBlank()) result += buffer.toString().trim()
        } else {
            lines.forEach { if (it.isNotBlank()) result += it.trim() }
        }
        return result.map { it.replace(Regex("[ \\t]{2,}"), " ") }
    }

    private fun isHeading(p: String): Boolean {
        if (p.length > 80) return false
        val t = p.trim()
        if (Regex("^(глава|часть|книга|пролог|эпилог|предисловие|послесловие|chapter|part|book|prologue|epilogue|preface|afterword)([\\s.:,\\-—–]+.*)?", RegexOption.IGNORE_CASE).matches(t)) return true
        if (Regex("^(\\d{1,3}|[IVXLC]{1,7})[.)]?$").matches(t)) return true
        if (Regex("^\\*\\s*\\*\\s*\\*$").matches(t)) return false
        return false
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            val cp1251 = runCatching { Charset.forName("windows-1251") }.getOrNull()
            val koi8 = runCatching { Charset.forName("KOI8-R") }.getOrNull()
            val a = cp1251?.let { String(bytes, it) }
            val b = koi8?.let { String(bytes, it) }
            when {
                a != null && b != null -> if (score(a) >= score(b)) a else b
                a != null -> a
                else -> String(bytes, Charsets.ISO_8859_1)
            }
        }
    }

    private fun score(text: String): Int {
        val sample = text.take(20_000)
        return Regex("[оеаинтсрвл]").findAll(sample).count() - Regex("[ЁЮЯ][а-я]|[ъь][аеиоуыэюя]").findAll(sample).count() * 5
    }
}
