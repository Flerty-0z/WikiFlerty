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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import org.json.JSONArray
import org.json.JSONObject

class PdfPageMap(val pages: Int, val parts: List<Part>) {
    class Part(val href: String, val from: Int, val to: Int)

    fun hrefOf(page: Int): Part? = parts.firstOrNull { page in it.from..it.to } ?: parts.lastOrNull()

    fun pageOf(href: String, progression: Double): Int? {
        val clean = href.substringBefore('#').trimStart('/')
        val part = parts.firstOrNull { it.href.trimStart('/') == clean || clean.endsWith(it.href.substringAfterLast('/')) } ?: return null
        val span = part.to - part.from + 1
        return (part.from + (progression.coerceIn(0.0, 1.0) * span).toInt()).coerceIn(part.from, part.to)
    }

    fun toJson(): JSONObject = JSONObject().put("pages", pages).put(
        "parts",
        JSONArray().apply { parts.forEach { put(JSONObject().put("href", it.href).put("from", it.from).put("to", it.to)) } },
    )

    companion object {
        fun fromJson(j: JSONObject): PdfPageMap {
            val arr = j.getJSONArray("parts")
            return PdfPageMap(j.getInt("pages"), (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Part(o.getString("href"), o.getInt("from"), o.getInt("to"))
            })
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
    }

    private const val PAGE = "\u0001"
    private const val PARA_START = "\u0002"
    private const val PARA_END = "\u0003"

    private class Para(val lines: List<String>, val verse: Boolean)

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
            val totalChars = pagesText.sumOf { t -> t.count { it.isLetterOrDigit() } }
            if (totalChars < count * 60 && totalChars < 4000) return null

            val info = runCatching { doc.documentInformation }.getOrNull()
            val metaTitle = info?.title?.trim()?.takeIf { it.length in 2..200 && !it.lowercase().let { l -> l == "untitled" || l.endsWith(".doc") || l.endsWith(".docx") || l.endsWith(".pdf") } }
            val metaAuthor = info?.author?.trim()?.takeIf { it.length in 2..200 }
            val outline = runCatching { readOutline(doc) }.getOrDefault(emptyList())

            val pages = pagesText.map { parseParas(it) }
            stripRunningLines(pages)

            val title = metaTitle ?: fallbackTitle
            val sample = pagesText.take(30).joinToString(" ").take(30_000)
            val language = if (Regex("[а-яё]", RegexOption.IGNORE_CASE).containsMatchIn(sample)) "ru" else "en"
            val book = EpubBuilder(identifier, title, listOfNotNull(metaAuthor), language)

            val tops = outline.filter { it.depth == 0 }.map { it.page }.filter { it in 0 until count }.distinct().sorted()
            val starts: List<Int> = if (tops.size >= 2) {
                (listOf(0) + tops).distinct().sorted()
            } else {
                (0 until count step 12).toList()
            }
            val ranges = starts.mapIndexed { i, s -> s to ((starts.getOrNull(i + 1) ?: count) - 1) }.filter { it.second >= it.first }

            val emptyPages = pages.indices.filter { i -> pages[i].sumOf { p -> p.lines.sumOf { it.length } } < 12 }.toSet()
            val images = renderImages(source, emptyPages.take(60))

            val parts = mutableListOf<PdfPageMap.Part>()
            var pending: StringBuilder? = null
            ranges.forEachIndexed { index, (from, to) ->
                val href = "part${index + 1}.xhtml"
                parts += PdfPageMap.Part("OEBPS/$href", from, to)
                val html = StringBuilder(xhtmlHead(title, language))
                if (index == 0) html.append("<h1>${esc(title)}</h1>\n")
                for (p in from..to) {
                    val anchor = "<span id=\"${PdfPageMap.anchor(p)}\"></span>"
                    val paras = pages[p]
                    val image = images[p]
                    if (image != null) {
                        pending?.let { html.append("<p>").append(it).append("</p>\n") }
                        pending = null
                        val name = "img/page$p.jpg"
                        book.addImage(name, "image/jpeg", image)
                        html.append("<div style=\"text-align:center\">").append(anchor).append("<img src=\"$name\" alt=\"\" style=\"max-width:100%\"/></div>\n")
                        continue
                    }
                    if (paras.isEmpty()) {
                        pending?.let { html.append("<p>").append(it).append("</p>\n") }
                        pending = null
                        html.append(anchor).append('\n')
                        continue
                    }
                    paras.forEachIndexed paraLoop@{ k, para ->
                        val body = if (para.verse) para.lines.joinToString("<br/>") { esc(it) } else esc(joinProse(para.lines))
                        val prefix = if (k == 0) anchor else ""
                        val carry = pending
                        if (k == 0 && carry != null && !para.verse && para.lines.first().firstOrNull()?.isLowerCase() == true) {
                            html.append("<p>").append(carry).append(' ').append(prefix).append(body).append("</p>\n")
                            pending = null
                            return@paraLoop
                        }
                        carry?.let { html.append("<p>").append(it).append("</p>\n") }
                        pending = null
                        val last = k == paras.lastIndex
                        val open = !para.verse && last && p < to && !Regex("[.!?…:;»\"”)]$").containsMatchIn(para.lines.last())
                        if (open) {
                            pending = StringBuilder().append(prefix).append(body)
                        } else {
                            html.append(if (para.verse) "<p class=\"verse\">" else "<p>").append(prefix).append(body).append("</p>\n")
                        }
                    }
                }
                pending?.let { html.append("<p>").append(it).append("</p>\n") }
                pending = null
                html.append(XHTML_END)
                book.addPage(href, html.toString())
            }

            val toc = mutableListOf<TocEntry>()
            if (outline.isNotEmpty()) {
                val stack = ArrayDeque<Pair<Int, TocEntry>>()
                outline.forEach { e ->
                    val p = e.page.coerceIn(0, count - 1)
                    val part = parts.firstOrNull { p in it.from..it.to } ?: return@forEach
                    val entry = TocEntry(e.title, part.href.removePrefix("OEBPS/") + "#" + PdfPageMap.anchor(p))
                    while (stack.isNotEmpty() && stack.last().first >= e.depth) stack.removeLast()
                    if (stack.isEmpty()) toc += entry else stack.last().second.children += entry
                    stack.addLast(e.depth to entry)
                }
            } else if (count > 24) {
                parts.forEach { toc += TocEntry("Страницы ${it.from + 1}–${it.to + 1}", it.href.removePrefix("OEBPS/")) }
            }
            book.toc += toc
            book.write(target)
            return PdfTextResult(PdfPageMap(count, parts), metaTitle, metaAuthor)
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

    private fun parseParas(text: String): MutableList<Para> {
        val blocks = mutableListOf<List<String>>()
        text.split(PARA_START).forEach { chunk ->
            chunk.split(PARA_END).forEach { part ->
                val lines = part.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) blocks += lines
            }
        }
        val all = blocks.flatten()
        if (all.isEmpty()) return mutableListOf()
        val lengths = all.map { it.length }.sorted()
        val p90 = lengths[((lengths.size - 1) * 0.9).toInt()].coerceAtLeast(1)
        val pageVerse = p90 < 46 && all.size >= 4
        return blocks.map { lines ->
            val inner = lines.dropLast(1)
            val shortShare = if (inner.isEmpty()) 0.0 else inner.count { it.length < p90 * 0.72 }.toDouble() / inner.size
            val verse = lines.size > 1 && (pageVerse || shortShare > 0.5)
            Para(lines, verse)
        }.toMutableList()
    }

    private fun stripRunningLines(pages: List<MutableList<Para>>) {
        fun norm(s: String) = s.replace(Regex("\\d+"), "#").lowercase().trim()
        val firsts = HashMap<String, Int>()
        val lasts = HashMap<String, Int>()
        pages.forEach { paras ->
            paras.firstOrNull()?.lines?.firstOrNull()?.let { firsts.merge(norm(it), 1, Int::plus) }
            paras.lastOrNull()?.lines?.lastOrNull()?.let { lasts.merge(norm(it), 1, Int::plus) }
        }
        val limit = (pages.size * 0.3).toInt().coerceAtLeast(3)
        fun running(line: String, counts: Map<String, Int>): Boolean {
            if (Regex("^[-–—\\s]*\\d{1,4}[-–—\\s]*$").matches(line)) return true
            if (Regex("^(стр\\.?|page|с\\.)\\s*\\d+$", RegexOption.IGNORE_CASE).matches(line)) return true
            return line.length < 90 && (counts[norm(line)] ?: 0) >= limit
        }
        pages.forEach { paras ->
            paras.firstOrNull()?.let { first ->
                if (running(first.lines.first(), firsts)) {
                    val rest = first.lines.drop(1)
                    if (rest.isEmpty()) paras.removeAt(0) else paras[0] = Para(rest, first.verse)
                }
            }
            paras.lastOrNull()?.let { last ->
                if (running(last.lines.last(), lasts)) {
                    val rest = last.lines.dropLast(1)
                    if (rest.isEmpty()) paras.removeAt(paras.lastIndex) else paras[paras.lastIndex] = Para(rest, last.verse)
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
