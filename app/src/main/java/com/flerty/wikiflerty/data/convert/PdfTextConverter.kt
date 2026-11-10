package com.flerty.wikiflerty.data.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.XHTML_END
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.esc
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.xhtmlHead
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import org.json.JSONArray
import org.json.JSONObject

class PdfPageMap(val pages: Int, val parts: List<Part>, val version: Int = 1) {
    class Part(val href: String, val from: Int, val to: Int)

    fun hrefOf(page: Int): Part? = parts.firstOrNull { page in it.from..it.to } ?: parts.lastOrNull()

    fun pageOf(href: String, progression: Double): Int? {
        val clean = href.substringBefore('#').trimStart('/')
        val part = parts.firstOrNull { it.href.trimStart('/') == clean || clean.endsWith(it.href.substringAfterLast('/')) } ?: return null
        val span = part.to - part.from + 1
        return (part.from + (progression.coerceIn(0.0, 1.0) * span).toInt()).coerceIn(part.from, part.to)
    }

    fun toJson(): JSONObject = JSONObject().put("pages", pages).put("v", version).put(
        "parts",
        JSONArray().apply { parts.forEach { put(JSONObject().put("href", it.href).put("from", it.from).put("to", it.to)) } },
    )

    companion object {
        fun fromJson(j: JSONObject): PdfPageMap {
            val arr = j.getJSONArray("parts")
            return PdfPageMap(j.getInt("pages"), (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Part(o.getString("href"), o.getInt("from"), o.getInt("to"))
            }, j.optInt("v", 1))
        }

        fun file(context: Context, bookId: String) = File(File(context.filesDir, "pdfmeta"), "$bookId.map.json")

        fun load(context: Context, bookId: String): PdfPageMap? = runCatching { fromJson(JSONObject(file(context, bookId).readText())) }.getOrNull()

        fun anchor(page: Int) = "pdf-p$page"
    }
}

class PdfTextResult(val map: PdfPageMap, val title: String?, val author: String?)

object PdfTextConverter {
    @Volatile private var ready = false

    private fun init(context: Context) {
        if (ready) return
        PDFBoxResourceLoader.init(context.applicationContext)
        ready = true
    }

    const val VERSION = 2

    private class Stripper : PDFTextStripper() {
        init {
            paragraphStart = PARA_START
            paragraphEnd = PARA_END
            lineSeparator = "\n"
            pageStart = ""
            pageEnd = ""
        }

        override fun startPage(page: PDPage?) {
            output.write(PAGE)
            super.startPage(page)
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val list = textPositions
            if (!list.isNullOrEmpty()) {
                var size = 0f
                var n = 0
                var bold = 0
                for (tp in list) {
                    var v = tp.fontSizeInPt
                    if (v < 2f) v = tp.heightDir * 1.35f
                    if (v > 0f) {
                        size += v
                        n++
                    }
                    val name = runCatching { tp.font?.name.orEmpty() }.getOrDefault("")
                    if (name.contains("Bold", true) || name.contains("Black", true) || name.contains("Heavy", true)) bold++
                }
                val avg = if (n > 0) size / n else 0f
                output.write(SIZE_START + (avg * 10).toInt() + (if (bold * 2 > list.size) "b" else "") + SIZE_END)
            }
            super.writeString(text, textPositions)
        }
    }

    private const val PAGE = "\u0001"
    private const val PARA_START = "\u0002"
    private const val PARA_END = "\u0003"
    private const val SIZE_START = "\u0004"
    private const val SIZE_END = "\u0005"

    private class Line(val text: String, val size: Float, val bold: Boolean)

    private sealed class Item {
        class Anchor(val page: Int) : Item()
        class Heading(val lines: List<String>, val level: Int, val sub: String?) : Item() {
            val text: String get() = lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        }
        class Para(val lines: List<String>, val verse: Boolean) : Item()
        class Picture(val page: Int) : Item()
    }

    private val markerRegex = Regex("\u0004(\\d+)(b?)\u0005")

    private fun parseLine(raw: String): Line? {
        var weighted = 0f
        var chars = 0
        var boldChars = 0
        val matches = markerRegex.findAll(raw).toList()
        matches.forEachIndexed { i, m ->
            val segEnd = matches.getOrNull(i + 1)?.range?.first ?: raw.length
            val seg = raw.substring(m.range.last + 1, segEnd)
            val len = seg.count { !it.isWhitespace() }.coerceAtLeast(1)
            weighted += m.groupValues[1].toInt() / 10f * len
            chars += len
            if (m.groupValues[2] == "b") boldChars += len
        }
        val text = raw.replace(markerRegex, "").replace(' ', ' ').trim()
        if (text.isEmpty()) return null
        return Line(text, if (chars > 0) weighted / chars else 0f, chars > 0 && boldChars * 2 > chars)
    }

    private fun parseBlocks(text: String): MutableList<MutableList<Line>> {
        val blocks = mutableListOf<MutableList<Line>>()
        text.split(PARA_START).forEach { chunk ->
            chunk.split(PARA_END).forEach { part ->
                val lines = part.split('\n').mapNotNull { parseLine(it) }
                if (lines.isNotEmpty()) blocks += lines.toMutableList()
            }
        }
        return blocks
    }

    private fun isCaps(t: String): Boolean {
        val letters = t.filter { it.isLetter() }
        return letters.length >= 3 && letters.all { it.isUpperCase() }
    }

    private fun isCredit(t: String) = t.length <= 120 && t.startsWith("(") && t.endsWith(")")

    fun convert(context: Context, source: File, target: File, fallbackTitle: String, identifier: String): PdfTextResult? {
        init(context)
        val doc = PDDocument.load(source, MemoryUsageSetting.setupMixed(24L * 1024 * 1024))
        try {
            if (doc.isEncrypted) runCatching { doc.isAllSecurityToBeRemoved = true }
            val count = doc.numberOfPages
            if (count <= 0) return null
            val writer = StringWriter()
            Stripper().writeText(doc, writer)
            val raw = writer.toString().split(PAGE).drop(1)
            val pagesText = List(count) { i -> raw.getOrNull(i).orEmpty().replace("­", "").replace("\r", "") }
            val totalChars = pagesText.sumOf { t -> t.replace(markerRegex, "").count { it.isLetterOrDigit() } }
            if (totalChars < count * 60 && totalChars < 4000) return null

            val info = runCatching { doc.documentInformation }.getOrNull()
            val metaTitle = info?.title?.trim()?.takeIf { it.length in 2..200 && !it.lowercase().let { l -> l == "untitled" || l.endsWith(".doc") || l.endsWith(".docx") || l.endsWith(".pdf") } }
            val metaAuthor = info?.author?.trim()?.takeIf { it.length in 2..200 }
            val outline = runCatching { readOutline(doc) }.getOrDefault(emptyList())

            val pages = pagesText.map { parseBlocks(it) }
            stripRunningLines(pages)

            val sizes = HashMap<Int, Int>()
            pages.forEach { blocks -> blocks.forEach { b -> b.forEach { l -> if (l.size > 0f) sizes.merge((l.size * 2).toInt(), l.text.length, Int::plus) } } }
            val totalLen = sizes.values.sum().coerceAtLeast(1)
            var acc = 0
            var body = 0f
            for (k in sizes.keys.sorted()) {
                acc += sizes[k] ?: 0
                if (acc * 2 >= totalLen) {
                    body = k / 2f
                    break
                }
            }

            fun headingLevel(l: Line, blockSize: Int): Int {
                val t = l.text
                if (t.length > 140 || t.count { it.isLetterOrDigit() } == 0) return 0
                if (body > 0f && l.size >= body * 1.5f) return 1
                if (body > 0f && l.size >= body * 1.18f) return 2
                if (isCaps(t) && t.length <= 70 && !t.endsWith(",") && blockSize <= 40) return 2
                if (l.bold && t.length <= 80 && blockSize <= 2 && !t.endsWith(".") && !t.endsWith(",")) return 2
                return 0
            }

            val emptyPages = pages.indices.filter { i -> pages[i].sumOf { b -> b.sumOf { it.text.length } } < 12 }.toSet()
            val images = renderImages(source, emptyPages.take(60))

            val items = mutableListOf<Item>()
            for (p in 0 until count) {
                items += Item.Anchor(p)
                if (images[p] != null) {
                    items += Item.Picture(p)
                    continue
                }
                val blocks = pages[p]
                val bodyLines = blocks.flatten().filter { headingLevel(it, 99) == 0 }.map { it.text.length }.sorted()
                val p90 = bodyLines.getOrNull(((bodyLines.size - 1) * 0.9).toInt())?.coerceAtLeast(1) ?: 1
                val pageVerse = p90 < 46 && bodyLines.size >= 4
                for (block in blocks) {
                    var i = 0
                    val plain = mutableListOf<String>()
                    fun flush() {
                        if (plain.isEmpty()) return
                        val inner = plain.dropLast(1)
                        val shortShare = if (inner.isEmpty()) 0.0 else inner.count { it.length < p90 * 0.72 }.toDouble() / inner.size
                        items += Item.Para(plain.toList(), plain.size > 1 && (pageVerse || shortShare > 0.5))
                        plain.clear()
                    }
                    while (i < block.size) {
                        val level = headingLevel(block[i], block.size)
                        if (level == 0) {
                            plain += block[i].text
                            i++
                            continue
                        }
                        flush()
                        val head = mutableListOf(block[i].text)
                        var lvl = level
                        i++
                        while (i < block.size && head.size < 4) {
                            val l2 = headingLevel(block[i], block.size)
                            if (l2 == 0) break
                            head += block[i].text
                            lvl = minOf(lvl, l2)
                            i++
                        }
                        val sub = if (i < block.size && isCredit(block[i].text)) block[i++].text else null
                        val last = items.lastOrNull()
                        if (last is Item.Para && last.lines.size == 1 && Regex("^[\\dIVXLCivxlc]{1,4}\\.?$").matches(last.lines[0])) {
                            items.removeAt(items.lastIndex)
                            head.add(0, last.lines[0])
                        }
                        items += Item.Heading(head, lvl, sub)
                    }
                    flush()
                }
            }

            val headings = items.filterIsInstance<Item.Heading>()
            val tops = outline.filter { it.depth == 0 }.map { it.page }.filter { it in 0 until count }.toSet()
            val chapterLevel = when {
                tops.size >= 2 -> 0
                headings.count { it.level == 1 } >= 2 -> 1
                headings.count { it.level == 2 } >= 2 -> 2
                else -> -1
            }

            val title = metaTitle ?: fallbackTitle
            val sample = pagesText.take(30).joinToString(" ").replace(markerRegex, "").take(30_000)
            val language = if (Regex("[а-яё]", RegexOption.IGNORE_CASE).containsMatchIn(sample)) "ru" else "en"
            val book = EpubBuilder(identifier, title, listOfNotNull(metaAuthor), language)

            val groups = mutableListOf<MutableList<Item>>(mutableListOf())
            var pagesInGroup = 0
            items.forEach { item ->
                val cur = groups.last()
                val breakHere = when {
                    chapterLevel == 0 -> item is Item.Anchor && item.page in tops && item.page != 0
                    chapterLevel > 0 -> item is Item.Heading && item.level <= chapterLevel
                    else -> item is Item.Anchor && pagesInGroup >= 12
                }
                if (breakHere && cur.any { it !is Item.Anchor }) {
                    val moved = mutableListOf<Item>()
                    while (cur.isNotEmpty() && cur.last() is Item.Anchor) moved.add(0, cur.removeAt(cur.lastIndex))
                    groups += (moved + item).toMutableList()
                    pagesInGroup = moved.size + if (item is Item.Anchor) 1 else 0
                } else {
                    cur += item
                    if (item is Item.Anchor) pagesInGroup++
                }
            }

            val parts = mutableListOf<PdfPageMap.Part>()
            val toc = mutableListOf<TocEntry>()
            var headingId = 0
            var lastPage = 0
            groups.filter { it.isNotEmpty() }.forEachIndexed { index, group ->
                val href = "part${index + 1}.xhtml"
                val anchors = group.filterIsInstance<Item.Anchor>().map { it.page }
                val from = anchors.minOrNull() ?: lastPage
                val to = anchors.maxOrNull() ?: from
                lastPage = to
                parts += PdfPageMap.Part("OEBPS/$href", from, to)
                val html = StringBuilder(xhtmlHead(title, language))
                if (index == 0 && group.none { it is Item.Heading }) html.append("<h1>${esc(title)}</h1>\n")
                var entry: TocEntry? = null
                var pendingAnchor = ""
                var carry: StringBuilder? = null
                fun flushCarry() {
                    carry?.let { html.append("<p>").append(it).append("</p>\n") }
                    carry = null
                }
                group.forEach { item ->
                    when (item) {
                        is Item.Anchor -> pendingAnchor += "<span id=\"${PdfPageMap.anchor(item.page)}\"></span>"
                        is Item.Picture -> {
                            flushCarry()
                            val name = "img/page${item.page}.jpg"
                            images[item.page]?.let { book.addImage(name, "image/jpeg", it) }
                            html.append("<div style=\"text-align:center\">").append(pendingAnchor).append("<img src=\"$name\" alt=\"\" style=\"max-width:100%\"/></div>\n")
                            pendingAnchor = ""
                        }
                        is Item.Heading -> {
                            flushCarry()
                            val id = "h${++headingId}"
                            val top = chapterLevel > 0 && item.level <= chapterLevel || entry == null && chapterLevel <= 0
                            val tag = if (top) "h2" else "h3"
                            html.append(pendingAnchor)
                            pendingAnchor = ""
                            html.append("<$tag id=\"$id\">").append(item.lines.joinToString("<br/>") { esc(it) }).append("</$tag>\n")
                            item.sub?.let { html.append("<p class=\"subtitle\" style=\"text-align:center !important;text-indent:0 !important;font-style:italic;opacity:.75\">").append(esc(it)).append("</p>\n") }
                            if (chapterLevel > 0) {
                                val e = TocEntry(item.text, "$href#$id")
                                if (item.level <= chapterLevel) {
                                    if (entry == null) entry = TocEntry(item.text, href)
                                } else if (entry != null) {
                                    entry!!.children += e
                                }
                            } else if (entry == null) {
                                entry = TocEntry(item.text, href)
                            }
                        }
                        is Item.Para -> {
                            if (item.verse) {
                                flushCarry()
                                html.append("<p class=\"poem\" style=\"text-align:left !important;text-indent:0 !important\">").append(pendingAnchor)
                                pendingAnchor = ""
                                item.lines.forEach { html.append("<span style=\"display:block;padding-left:1.4em;text-indent:-1.4em\">").append(esc(it)).append("</span>") }
                                html.append("</p>\n")
                            } else {
                                val text = esc(joinProse(item.lines))
                                val c = carry
                                if (c != null && pendingAnchor.isNotEmpty() && item.lines.first().firstOrNull()?.isLowerCase() == true) {
                                    c.append(' ').append(pendingAnchor).append(text)
                                    pendingAnchor = ""
                                } else {
                                    flushCarry()
                                    carry = StringBuilder().append(pendingAnchor).append(text)
                                    pendingAnchor = ""
                                }
                                if (Regex("[.!?…:;»\"”)]$").containsMatchIn(item.lines.last())) flushCarry()
                            }
                        }
                    }
                }
                flushCarry()
                html.append(pendingAnchor).append('\n')
                html.append(XHTML_END)
                book.addPage(href, html.toString())
                if (chapterLevel != 0) {
                    toc += entry ?: TocEntry(if (from == to) "Страница ${from + 1}" else "Страницы ${from + 1}–${to + 1}", href)
                }
            }

            if (chapterLevel == 0 || (outline.isNotEmpty() && chapterLevel < 0)) {
                toc.clear()
                val stack = ArrayDeque<Pair<Int, TocEntry>>()
                outline.forEach { e ->
                    val p = e.page.coerceIn(0, count - 1)
                    val part = parts.firstOrNull { p in it.from..it.to } ?: return@forEach
                    val entry = TocEntry(e.title, part.href.removePrefix("OEBPS/") + "#" + PdfPageMap.anchor(p))
                    while (stack.isNotEmpty() && stack.last().first >= e.depth) stack.removeLast()
                    if (stack.isEmpty()) toc += entry else stack.last().second.children += entry
                    stack.addLast(e.depth to entry)
                }
            }
            book.toc += toc
            book.write(target)
            return PdfTextResult(PdfPageMap(count, parts, VERSION), metaTitle, metaAuthor)
        } finally {
            runCatching { doc.close() }
        }
    }

    private fun readOutline(doc: PDDocument): List<PdfOutline.Entry> {
        val root = doc.documentCatalog?.documentOutline ?: return emptyList()
        val out = mutableListOf<PdfOutline.Entry>()
        fun walk(node: PDOutlineNode, depth: Int) {
            if (depth > 8 || out.size > 2000) return
            for (item in node.children()) {
                val title = item.title?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val page = runCatching { item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } }.getOrNull() ?: -1
                if (title.isNotEmpty() && page >= 0) out += PdfOutline.Entry(title, depth, page)
                walk(item, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    private fun stripRunningLines(pages: List<MutableList<MutableList<Line>>>) {
        fun norm(s: String) = s.replace(Regex("\\d+"), "#").lowercase().trim()
        val firsts = HashMap<String, Int>()
        val lasts = HashMap<String, Int>()
        pages.forEach { blocks ->
            blocks.firstOrNull()?.firstOrNull()?.let { firsts.merge(norm(it.text), 1, Int::plus) }
            blocks.lastOrNull()?.lastOrNull()?.let { lasts.merge(norm(it.text), 1, Int::plus) }
        }
        val limit = (pages.size * 0.3).toInt().coerceAtLeast(3)
        fun running(line: String, counts: Map<String, Int>): Boolean {
            if (Regex("^[-–—\\s]*\\d{1,4}[-–—\\s]*$").matches(line)) return true
            if (Regex("^(стр\\.?|page|с\\.)\\s*\\d+$", RegexOption.IGNORE_CASE).matches(line)) return true
            return line.length < 90 && (counts[norm(line)] ?: 0) >= limit
        }
        pages.forEach { blocks ->
            blocks.firstOrNull()?.let { first ->
                if (running(first.first().text, firsts)) {
                    first.removeAt(0)
                    if (first.isEmpty()) blocks.removeAt(0)
                }
            }
            blocks.lastOrNull()?.let { last ->
                if (running(last.last().text, lasts)) {
                    last.removeAt(last.lastIndex)
                    if (last.isEmpty()) blocks.removeAt(blocks.lastIndex)
                }
            }
        }
    }

    private fun joinProse(lines: List<String>): String {
        val sb = StringBuilder()
        lines.forEachIndexed { i, line ->
            if (i == 0) {
                sb.append(line)
                return@forEachIndexed
            }
            val prev = sb.lastOrNull()
            if (prev == '-' && sb.length > 1 && sb[sb.length - 2].isLetter() && line.firstOrNull()?.isLowerCase() == true) {
                sb.setLength(sb.length - 1)
                sb.append(line)
            } else {
                sb.append(' ').append(line)
            }
        }
        return sb.toString()
    }

    private fun renderImages(file: File, pages: List<Int>): Map<Int, ByteArray> {
        if (pages.isEmpty()) return emptyMap()
        val out = HashMap<Int, ByteArray>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    for (i in pages) {
                        if (i >= renderer.pageCount) continue
                        runCatching {
                            val page = renderer.openPage(i)
                            try {
                                val w = 900
                                val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 2700)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val bytes = ByteArrayOutputStream().use { s ->
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 78, s)
                                    s.toByteArray()
                                }
                                bmp.recycle()
                                out[i] = bytes
                            } finally {
                                page.close()
                            }
                        }
                    }
                } finally {
                    renderer.close()
                }
            }
        }
        return out
    }
}
