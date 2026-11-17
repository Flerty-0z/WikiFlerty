package com.flerty.wikiflerty.tts

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.PublicationServicesHolder
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.publication.services.content.iterators.ResourceContentIteratorFactory
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.InMemoryResource
import org.readium.r2.shared.util.resource.Resource

@OptIn(ExperimentalReadiumApi::class)
object SafeContent : ResourceContentIteratorFactory {
    @Volatile var lastIssue: String? = null

    override suspend fun create(
        manifest: Manifest,
        servicesHolder: PublicationServicesHolder,
        readingOrderIndex: Int,
        resource: Resource,
        mediaType: MediaType,
        locator: Locator,
    ): Content.Iterator? {
        if (mediaType.isBitmap || mediaType.isAudio || mediaType.isVideo || mediaType.matches(MediaType.PDF)) return null
        val bytes = runCatching { resource.read().getOrNull() }.getOrNull()
        runCatching { resource.close() }
        if (bytes == null) {
            lastIssue = "read"
            return null
        }
        val factory = HtmlResourceContentIterator.Factory()
        val plain = PlainIterator.of(bytes, locator)
        val found = runCatching {
            val probe = factory.create(manifest, servicesHolder, readingOrderIndex, InMemoryResource(bytes), MediaType.XHTML, locator.copy(locations = Locator.Locations(progression = 0.0)))
            var n = 0
            while (probe != null && n < 100_000 && probe.hasNext()) {
                val e = probe.next()
                if (e is Content.TextualElement && !e.text.isNullOrBlank()) n++
            }
            n
        }.onFailure { lastIssue = it.javaClass.simpleName + ": " + it.message?.take(60) }.getOrDefault(0)
        val works = found > 0 && found * 2 >= plain.size
        if (works) {
            return runCatching { factory.create(manifest, servicesHolder, readingOrderIndex, InMemoryResource(bytes), MediaType.XHTML, locator) }.getOrNull()
                ?: PlainIterator.of(bytes, locator)
        }
        if (plain.size > 0) lastIssue = (lastIssue ?: "r$found/${plain.size}") + " → plain"
        return plain
    }
}

@OptIn(ExperimentalReadiumApi::class)
private class PlainIterator(private val elements: List<Content.Element>, start: Int) : Content.Iterator {
    val size get() = elements.size

    private var index = start - 1
    private var pending: Int? = null

    override suspend fun hasNext(): Boolean {
        val i = index + 1
        if (i !in elements.indices) return false
        pending = i
        return true
    }

    override fun next(): Content.Element {
        val i = pending ?: error("hasNext first")
        pending = null
        index = i
        return elements[i]
    }

    override suspend fun hasPrevious(): Boolean {
        val i = index - 1
        if (i !in elements.indices) return false
        pending = i
        return true
    }

    override fun previous(): Content.Element {
        val i = pending ?: error("hasPrevious first")
        pending = null
        index = i
        return elements[i]
    }

    companion object {
        private val dropBlocks = Regex("(?is)<(script|style|head|title)\\b[^>]*>.*?</\\1\\s*>")
        private val breaks = Regex("(?i)<\\s*(?:br\\b[^>]*|/?\\s*(?:p|div|h[1-6]|li|ul|ol|blockquote|section|article|tr|td|th|dd|dt|pre|figcaption|table|header|footer|aside|hr)\\b[^>]*)>")
        private val tags = Regex("<[^>]*>")
        private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
        private val named = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "mdash" to "—", "ndash" to "–", "laquo" to "«", "raquo" to "»", "hellip" to "…",
            "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "shy" to "", "thinsp" to " ", "ensp" to " ", "emsp" to " ",
        )

        private fun unescape(s: String): String = entity.replace(s) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> named[v] ?: m.value
            }
        }

        private fun decode(bytes: ByteArray): String {
            val head = String(bytes, 0, minOf(bytes.size, 400), Charsets.ISO_8859_1)
            val name = Regex("(?i)(?:encoding|charset)\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)").find(head)?.groupValues?.get(1)
            val charset = name?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
            return String(bytes, charset)
        }

        fun of(bytes: ByteArray, locator: Locator): PlainIterator {
            val html = decode(bytes)
            val bodyStart = Regex("(?i)<body\\b[^>]*>").find(html)?.range?.last?.plus(1) ?: 0
            val bodyEnd = html.lastIndexOf("</body", ignoreCase = true).takeIf { it > bodyStart } ?: html.length
            val body = dropBlocks.replace(html.substring(bodyStart, bodyEnd), " ")
            val blocks = breaks.replace(body, "\n")
                .split('\n')
                .map { unescape(tags.replace(it, "")).replace(Regex("\\s+"), " ").trim() }
                .filter { b -> b.any { it.isLetterOrDigit() } }
            val count = blocks.size.coerceAtLeast(1)
            var before = ""
            val elements = blocks.mapIndexed { i, text ->
                val at = locator.copy(
                    locations = Locator.Locations(progression = i.toDouble() / count),
                    text = Locator.Text(before = before.takeLast(50).ifBlank { null }, highlight = text),
                )
                before += " $text"
                if (before.length > 400) before = before.takeLast(200)
                Content.TextElement(
                    locator = at,
                    role = Content.TextElement.Role.Body,
                    segments = listOf(Content.TextElement.Segment(at, text, emptyList())),
                )
            }
            val progression = locator.locations.progression ?: 0.0
            val start = when {
                progression >= 1.0 -> elements.size
                else -> (progression * elements.size).toInt().coerceIn(0, elements.size)
            }
            return PlainIterator(elements, start)
        }
    }
}
