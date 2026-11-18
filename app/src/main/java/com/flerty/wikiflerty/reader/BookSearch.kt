package com.flerty.wikiflerty.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.mediatype.MediaType

internal class BookSearchHit(
    val locator: Locator,
    val before: String,
    val match: String,
    val after: String,
)

internal object HtmlText {
    private val dropBlocks = Regex("(?is)<(script|style|head|title)\\b[^>]*>.*?</\\1\\s*>")
    private val breaks = Regex("(?i)<\\s*(?:br\\b[^>]*|/?\\s*(?:p|div|h[1-6]|li|ul|ol|blockquote|section|article|tr|td|th|dd|dt|pre|figcaption|table|header|footer|aside|hr)\\b[^>]*)>")
    private val tags = Regex("<[^>]*>")
    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val spaces = Regex("\\s+")
    private val bodyTag = Regex("(?i)<body\\b[^>]*>")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "laquo" to "«", "raquo" to "»", "hellip" to "…",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "shy" to "", "thinsp" to " ", "ensp" to " ", "emsp" to " ",
    )

    private fun unescape(s: String): String = entity.replace(s) { m ->
        val v = m.groupValues[1]
        when {
            v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
            v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
            else -> named[v] ?: m.value
        }
    }

    private fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 400), Charsets.ISO_8859_1)
        val name = Regex("(?i)(?:encoding|charset)\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)").find(head)?.groupValues?.get(1)
        val charset = name?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    fun blocks(bytes: ByteArray): List<String> {
        val html = decode(bytes)
        val bodyStart = bodyTag.find(html)?.range?.last?.plus(1) ?: 0
        val bodyEnd = html.lastIndexOf("</body", ignoreCase = true).takeIf { it > bodyStart } ?: html.length
        val body = dropBlocks.replace(html.substring(bodyStart, bodyEnd), " ")
        return breaks.replace(body, "\n")
            .split('\n')
            .map { unescape(tags.replace(it, "")).replace(spaces, " ").trim() }
            .filter { b -> b.any { it.isLetterOrDigit() } }
    }
}

internal object BookSearch {
    private const val CONTEXT = 60

    private fun skip(c: Char): Boolean =
        c == '\u00AD' || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060' || c == '\uFEFF' || c in '\u0300'..'\u036F'

    private fun fold(c: Char): Char = when {
        c == 'ё' || c == 'Ё' -> 'е'
        c.isWhitespace() || c == '\u00A0' -> ' '
        else -> c.lowercaseChar()
    }

    private class Folded(val text: String, val at: IntArray)

    private fun folded(source: String): Folded {
        val out = StringBuilder(source.length)
        val at = IntArray(source.length)
        source.forEachIndexed { i, c ->
            if (skip(c)) return@forEachIndexed
            val f = fold(c)
            if (f == ' ' && (out.isEmpty() || out[out.length - 1] == ' ')) return@forEachIndexed
            at[out.length] = i
            out.append(f)
        }
        return Folded(out.toString(), at.copyOf(out.length))
    }

    fun normalize(query: String): String = folded(query).text.trim()

    private fun textual(type: MediaType?): Boolean =
        type == null || !(type.isBitmap || type.isAudio || type.isVideo || type.matches(MediaType.PDF))

    fun searchable(publication: Publication): Boolean = publication.readingOrder.any { textual(it.mediaType) }

    suspend fun find(
        publication: Publication,
        query: String,
        total: (href: String, progression: Double) -> Pair<Double?, Int?>,
        onHits: suspend (List<BookSearchHit>) -> Boolean,
    ) {
        val needle = normalize(query)
        if (needle.isEmpty()) return
        for (link in publication.readingOrder) {
            currentCoroutineContext().ensureActive()
            if (!textual(link.mediaType)) continue
            val hits = withContext(Dispatchers.Default) {
                val bytes = runCatching {
                    val resource = publication.get(link) ?: return@runCatching null
                    try {
                        resource.read().getOrNull()
                    } finally {
                        runCatching { resource.close() }
                    }
                }.getOrNull() ?: return@withContext emptyList()
                val text = runCatching { HtmlText.blocks(bytes).joinToString(" ") }.getOrNull().orEmpty()
                if (text.isEmpty()) return@withContext emptyList()
                val base = runCatching { publication.locatorFromLink(link) }.getOrNull() ?: return@withContext emptyList()
                val f = folded(text)
                val found = mutableListOf<BookSearchHit>()
                var from = 0
                while (true) {
                    val k = f.text.indexOf(needle, from)
                    if (k < 0) break
                    from = k + needle.length
                    val start = f.at[k]
                    val end = f.at[k + needle.length - 1] + 1
                    val progression = start.toDouble() / text.length
                    val (totalProgression, position) = total(base.href.toString(), progression)
                    val before = text.substring(maxOf(0, start - CONTEXT), start)
                    val match = text.substring(start, end)
                    val after = text.substring(end, minOf(text.length, end + CONTEXT))
                    val locator = base.copyWithLocations(progression = progression, totalProgression = totalProgression, position = position)
                        .copy(text = Locator.Text(before = before.ifEmpty { null }, highlight = match, after = after.ifEmpty { null }))
                    found += BookSearchHit(locator, before, match, after)
                }
                found
            }
            if (hits.isNotEmpty() && !onHits(hits)) return
        }
    }
}
