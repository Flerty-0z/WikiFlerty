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

    private val first = start
    private var index: Int? = null
    private var pending: Int? = null

    override suspend fun hasNext(): Boolean {
        val i = index?.plus(1) ?: first
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
        val i = (index ?: first) - 1
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
        fun of(bytes: ByteArray, locator: Locator): PlainIterator {
            val blocks = com.flerty.wikiflerty.reader.HtmlText.blocks(bytes)
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
                else -> (progression * elements.size + 1e-6).toInt().coerceIn(0, elements.size)
            }
            return PlainIterator(elements, start)
        }
    }
}
