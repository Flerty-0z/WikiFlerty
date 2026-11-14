package com.flerty.wikiflerty.reader

import com.flerty.wikiflerty.data.convert.ChapterHeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.mediatype.MediaType

object ChapterFinder {
    data class Found(val title: String, val locator: Locator, val at: Double)

    private class Block(val res: Int, val index: Int, val tag: String, val text: String, val offset: Int)

    private val blockRx = Regex("<(h[1-6]|p)(\\s[^>]*)?>(.*?)</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRx = Regex("<[^>]+>")
    private val spaceRx = Regex("\\s+")
    private val numRefRx = Regex("&#(x?)([0-9a-fA-F]+);")

    private fun plain(html: String): String {
        var s = tagRx.replace(html, " ")
        if ('&' in s) {
            s = numRefRx.replace(s) { m ->
                val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
                code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: " "
            }
            s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        }
        return spaceRx.replace(s.replace(' ', ' ').replace("­", ""), " ").trim()
    }

    suspend fun find(pub: Publication, positions: List<Locator>): List<Found> = withContext(Dispatchers.Default) {
        val starts = HashMap<String, Double>()
        positions.forEach { loc ->
            val key = loc.href.toString().substringBefore('#')
            if (key !in starts) starts[key] = loc.locations.totalProgression ?: 0.0
        }
        val order = pub.readingOrder
        val keys = order.map { it.url().toString().substringBefore('#') }
        val blocks = ArrayList<Block>()
        val lengths = IntArray(order.size)
        order.forEachIndexed { ri, link ->
            val res = pub.get(link) ?: return@forEachIndexed
            val bytes = runCatching { res.read().getOrNull() }.getOrNull().also { runCatching { res.close() } } ?: return@forEachIndexed
            val html = String(bytes, Charsets.UTF_8)
            val bodyAt = html.indexOf("<body", ignoreCase = true).coerceAtLeast(0)
            var acc = 0
            var last = bodyAt
            var index = 0
            blockRx.findAll(html, bodyAt).forEach { m ->
                acc += plain(html.substring(last, m.range.first)).length
                val text = plain(m.groupValues[3])
                if (text.isNotEmpty()) {
                    blocks += Block(ri, index++, m.groupValues[1].lowercase(), text, acc)
                    acc += text.length + 1
                }
                last = m.range.last + 1
            }
            acc += plain(html.substring(last.coerceAtMost(html.length))).length
            lengths[ri] = acc.coerceAtLeast(1)
        }

        var lastNumber = 0
        val byRule = blocks.filter { b ->
            if (!ChapterHeading.isHeading(b.text)) return@filter false
            val n = ChapterHeading.number(b.text) ?: return@filter true
            if (n == 1 || n == lastNumber + 1) {
                lastNumber = n
                true
            } else {
                false
            }
        }
        val byTag = blocks.filter { it.tag in setOf("h1", "h2", "h3") && it.text.length <= 80 }
        val chosen = dropContents(if (byRule.size >= 2) byRule else byTag)
        val chosenSet = chosen.toHashSet()
        val position = HashMap<Block, Int>()
        blocks.forEachIndexed { i, b -> position[b] = i }

        chosen.mapNotNull { b ->
            val i = position.getValue(b)
            val next = blocks.getOrNull(i + 1)?.takeIf { it.res == b.res && it !in chosenSet }
            val short = b.text.split(' ').size <= 3
            val title = if (short && next != null && next.text.length <= 60 && (next.tag.startsWith("h") || next.text.last() !in ".!?…:;,")) {
                if (b.text.last() in ".:") "${b.text} ${next.text}" else "${b.text}. ${next.text}"
            } else {
                b.text
            }
            val link = order[b.res]
            val start = starts[keys[b.res]] ?: return@mapNotNull null
            val end = (b.res + 1 until order.size).firstNotNullOfOrNull { starts[keys[it]] } ?: 1.0
            val progression = (b.offset.toDouble() / lengths[b.res]).coerceIn(0.0, 1.0)
            val at = start + (end - start) * progression
            val before = blocks.getOrNull(i - 1)?.takeIf { it.res == b.res }?.text?.takeLast(32)
            val after = blocks.getOrNull(i + 1)?.takeIf { it.res == b.res }?.text?.take(32)
            Found(
                title.take(80),
                Locator(
                    href = link.url(),
                    mediaType = link.mediaType ?: MediaType.XHTML,
                    title = title.take(80),
                    locations = Locator.Locations(progression = progression, totalProgression = at),
                    text = Locator.Text(before = before, highlight = b.text, after = after),
                ),
                at,
            )
        }.distinctBy { (it.at * 100000).toLong() }
    }

    private fun dropContents(list: List<Block>): List<Block> {
        if (list.size < 4) return list
        val out = ArrayList<Block>()
        var run = ArrayList<Block>()
        fun flush() {
            if (run.size < 4) out += run
            run = ArrayList()
        }
        list.forEach { b ->
            val prev = run.lastOrNull()
            if (prev != null && !(prev.res == b.res && b.index - prev.index <= 2)) flush()
            run += b
        }
        flush()
        return out
    }
}
