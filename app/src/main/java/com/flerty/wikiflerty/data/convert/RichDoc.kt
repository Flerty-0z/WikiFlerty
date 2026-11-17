package com.flerty.wikiflerty.data.convert

import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.XHTML_END
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.esc
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.xhtmlHead
import java.io.File

class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
) {
    fun same(o: Run) = bold == o.bold && italic == o.italic && underline == o.underline && sup == o.sup && sub == o.sub
}

enum class Align { Start, Center, End }

sealed class Block {
    class Heading(val level: Int, val runs: List<Run>) : Block()
    class Para(val runs: List<Run>, val align: Align = Align.Start, val quote: Boolean = false) : Block()
    class Image(val href: String) : Block()
    object Break : Block()
    object Rule : Block()
}

val List<Run>.plain: String get() = joinToString("") { it.text }

fun List<Run>.unbold(): List<Run> = map { Run(it.text, false, it.italic, it.underline, it.sup, it.sub) }

class LineBuilder {
    val runs = mutableListOf<Run>()

    val isEmpty: Boolean get() = runs.all { it.text.isBlank() }

    fun add(text: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, sup: Boolean = false, sub: Boolean = false) {
        if (text.isEmpty()) return
        val run = Run(text, bold, italic, underline, sup, sub)
        val last = runs.lastOrNull()
        if (last != null && last.same(run)) runs[runs.lastIndex] = Run(last.text + text, bold, italic, underline, sup, sub) else runs += run
    }

    fun take(): List<Run> {
        val out = mutableListOf<Run>()
        runs.forEach { r ->
            val t = r.text.replace(' ', ' ').replace(Regex("[ \\t\\r\\n]+"), " ")
            if (t.isNotEmpty()) out += Run(t, r.bold, r.italic, r.underline, r.sup, r.sub)
        }
        runs.clear()
        while (out.isNotEmpty() && out.first().text.isBlank()) out.removeAt(0)
        while (out.isNotEmpty() && out.last().text.isBlank()) out.removeAt(out.lastIndex)
        if (out.isNotEmpty()) {
            val f = out.first()
            out[0] = Run(f.text.trimStart(), f.bold, f.italic, f.underline, f.sup, f.sub)
            val l = out.last()
            out[out.lastIndex] = Run(l.text.trimEnd(), l.bold, l.italic, l.underline, l.sup, l.sub)
        }
        return out.filter { it.text.isNotEmpty() }
    }
}

object Entities {
    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "laquo" to "«", "raquo" to "»", "hellip" to "…", "bull" to "•", "middot" to "·",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚",
        "shy" to "", "thinsp" to " ", "ensp" to " ", "emsp" to " ", "zwj" to "", "zwnj" to "",
        "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°", "times" to "×", "minus" to "−", "plusmn" to "±",
        "sect" to "§", "para" to "¶", "prime" to "′", "Prime" to "″", "dagger" to "†", "Dagger" to "‡", "euro" to "€",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "iexcl" to "¡", "iquest" to "¿",
    )

    fun unescape(s: String): String {
        if (s.indexOf('&') < 0) return s
        return entity.replace(s) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { code(it) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { code(it) } ?: m.value
                else -> named[v] ?: named[v.lowercase()] ?: m.value
            }
        }
    }

    private fun code(c: Int): String? = runCatching { if (c in 1..0x10FFFF) String(Character.toChars(c)) else null }.getOrNull()
}

class RichDoc(var title: String, var authors: List<String> = emptyList(), var language: String? = null) {
    val blocks = mutableListOf<Block>()
    private val images = LinkedHashMap<String, Pair<String, ByteArray>>()
    var cover: String? = null
    var description: String? = null

    fun addImage(data: ByteArray): String? {
        val (mime, ext) = imageType(data) ?: return null
        val href = "img/i${images.size + 1}.$ext"
        images[href] = mime to data
        return href
    }

    fun hasText(): Boolean = blocks.any { it is Block.Para || it is Block.Heading }

    fun imageCount(): Int = images.size

    fun write(target: File, identifier: String) {
        val text = blocks.asSequence().mapNotNull { (it as? Block.Para)?.runs?.plain ?: (it as? Block.Heading)?.runs?.plain }.take(400).joinToString(" ")
        val lang = language?.takeIf { it.isNotBlank() }?.substringBefore('-')?.lowercase()
            ?: if (Regex("[а-яё]", RegexOption.IGNORE_CASE).containsMatchIn(text)) "ru" else "en"
        val book = EpubBuilder(identifier, title.ifBlank { "Без названия" }, authors.filter { it.isNotBlank() }, lang)
        book.description = description
        images.forEach { (href, v) -> book.addImage(href, v.first, v.second, cover = href == cover) }
        val chapters = split(normalize(blocks))
        var page = 0
        if (cover != null) {
            page++
            book.addPage("c0.xhtml", xhtmlHead(title, lang) + """<div class="cover"><img src="${esc(cover!!)}" alt=""/></div>""" + "\n" + XHTML_END)
        }
        var anchor = 0
        chapters.forEachIndexed { ci, chapter ->
            val parts = chapter.blocks.chunked(700)
            parts.forEachIndexed { pi, part ->
                page++
                val href = "c$page.xhtml"
                val html = StringBuilder(xhtmlHead(chapter.title ?: title, lang))
                if (ci == 0 && pi == 0 && chapter.title == null && cover == null && !chapters.any { it.title != null }) {
                    html.append("<h1>${esc(title)}</h1>\n")
                }
                var entry: TocEntry? = null
                if (pi == 0 && chapter.title != null) {
                    entry = TocEntry(chapter.title, href)
                    book.toc += entry
                }
                part.forEach { b ->
                    when (b) {
                        is Block.Heading -> {
                            val level = b.level.coerceIn(1, 6)
                            if (b === chapter.head) {
                                html.append("<h2>").append(render(b.runs.unbold())).append("</h2>\n")
                            } else {
                                anchor++
                                val id = "h$anchor"
                                html.append("<h${(level + 1).coerceAtMost(6)} id=\"$id\">").append(render(b.runs.unbold())).append("</h${(level + 1).coerceAtMost(6)}>\n")
                                val t = b.runs.plain.trim().take(120)
                                if (t.isNotEmpty() && level <= chapter.level + 1) {
                                    val child = TocEntry(t, "$href#$id")
                                    if (entry != null) entry.children += child else book.toc += child
                                }
                            }
                        }
                        is Block.Para -> {
                            val body = render(b.runs)
                            val style = when (b.align) {
                                Align.Center -> " style=\"text-align:center;text-indent:0\""
                                Align.End -> " style=\"text-align:right;text-indent:0\""
                                Align.Start -> ""
                            }
                            if (b.quote) html.append("<blockquote><p$style>$body</p></blockquote>\n") else html.append("<p$style>$body</p>\n")
                        }
                        is Block.Image -> html.append("<div class=\"image\"><img src=\"${esc(b.href)}\" alt=\"\"/></div>\n")
                        Block.Rule -> html.append("<p class=\"subtitle\">* * *</p>\n")
                        Block.Break -> Unit
                    }
                }
                html.append(XHTML_END)
                book.addPage(href, html.toString())
            }
        }
        if (page == 0) book.addPage("c1.xhtml", xhtmlHead(title, lang) + "<h1>${esc(title)}</h1>\n" + XHTML_END)
        if (book.toc.isNotEmpty() && chapters.firstOrNull()?.title == null && chapters.first().blocks.any { it is Block.Para || it is Block.Image }) {
            book.toc.add(0, TocEntry("Начало", if (cover != null) "c2.xhtml" else "c1.xhtml"))
        }
        book.write(target)
    }

    private class Chapter(val title: String?, val head: Block.Heading?, val level: Int, val blocks: MutableList<Block>)

    private fun normalize(input: List<Block>): List<Block> {
        val src = input.toMutableList()
        val key = { s: String -> s.lowercase().filter { it.isLetterOrDigit() } }
        val first = src.indexOfFirst { it is Block.Heading || it is Block.Para }
        if (first >= 0 && first < 3) {
            val b = src[first]
            val t = (b as? Block.Heading)?.runs?.plain ?: (b as? Block.Para)?.runs?.plain
            if (t != null && key(t) == key(title) && key(t).isNotEmpty()) {
                src.removeAt(first)
                val next = src.getOrNull(first)
                val nt = (next as? Block.Para)?.runs?.plain ?: (next as? Block.Heading)?.runs?.plain
                if (nt != null && authors.any { key(it) == key(nt) }) src.removeAt(first)
            }
        }
        val out = mutableListOf<Block>()
        src.forEach { b ->
            when (b) {
                is Block.Para -> {
                    val t = b.runs.plain.trim()
                    if (t.isEmpty()) return@forEach
                    if (Regex("^[*·•\\-–—~#\\s]{3,}$").matches(t) && t.count { !it.isWhitespace() } in 1..12) out += Block.Rule else out += b
                }
                is Block.Heading -> if (b.runs.plain.isNotBlank()) out += b
                Block.Break -> if (out.lastOrNull() != Block.Break) out += b
                else -> out += b
            }
        }
        while (out.firstOrNull() == Block.Break) out.removeAt(0)
        while (out.lastOrNull() == Block.Break) out.removeAt(out.lastIndex)
        if (out.none { it is Block.Heading }) {
            var last = 0
            return out.map { b ->
                if (b is Block.Para && !b.quote) {
                    val t = b.runs.plain.trim()
                    if (t.length <= 80 && ChapterHeading.isHeading(t)) {
                        val n = ChapterHeading.number(t)
                        if (n == null || n == 1 || n == last + 1) {
                            if (n != null) last = n
                            return@map Block.Heading(1, b.runs)
                        }
                    }
                }
                b
            }
        }
        return out
    }

    private fun split(src: List<Block>): List<Chapter> {
        val headings = src.filterIsInstance<Block.Heading>()
        var level = headings.minOfOrNull { it.level } ?: 1
        if (headings.count { it.level == level } == 1 && headings.any { it.level > level }) {
            val next = headings.filter { it.level > level }.minOf { it.level }
            if (headings.count { it.level == next } >= 2) level = next
        }
        val out = mutableListOf<Chapter>()
        var current = Chapter(null, null, level, mutableListOf())
        fun push() {
            if (current.blocks.any { it !is Block.Break } || current.title != null) out += current
        }
        src.forEach { b ->
            when {
                b is Block.Heading && b.level <= level -> {
                    val t = b.runs.plain.trim()
                    val onlyHeadings = current.blocks.isNotEmpty() && current.blocks.all { it is Block.Heading || it is Block.Image }
                    if (current.title != null && onlyHeadings && current.blocks.size <= 2 && current.blocks.none { it is Block.Image }) {
                        current.blocks += b
                    } else {
                        push()
                        current = Chapter(t.take(120), b, level, mutableListOf(b))
                    }
                }
                b == Block.Break -> {
                    if (current.blocks.any { it is Block.Para || it is Block.Image }) {
                        push()
                        current = Chapter(null, null, level, mutableListOf())
                    }
                }
                else -> current.blocks += b
            }
        }
        push()
        var n = 0
        return out.map { c ->
            if (c.title != null || out.none { it.title != null }) c
            else {
                val first = c.blocks.firstOrNull { it is Block.Para } as? Block.Para
                val t = first?.runs?.plain?.trim()
                val looks = t != null && t.length in 2..80 && (first.align == Align.Center || first.runs.all { it.bold })
                if (looks) Chapter(t, null, c.level, c.blocks) else { n++; c }
            }
        }
    }

    companion object {
        fun render(runs: List<Run>): String = buildString {
            runs.forEach { r ->
                var s = esc(r.text)
                if (r.sup) s = "<sup>$s</sup>"
                if (r.sub) s = "<sub>$s</sub>"
                if (r.underline) s = "<u>$s</u>"
                if (r.italic) s = "<i>$s</i>"
                if (r.bold) s = "<b>$s</b>"
                append(s)
            }
        }

        fun imageType(data: ByteArray): Pair<String, String>? {
            if (data.size < 12) return null
            fun b(i: Int) = data[i].toInt() and 0xFF
            return when {
                b(0) == 0xFF && b(1) == 0xD8 -> "image/jpeg" to "jpg"
                b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 -> "image/png" to "png"
                b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46 -> "image/gif" to "gif"
                b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 && b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50 -> "image/webp" to "webp"
                b(0) == 0x42 && b(1) == 0x4D -> "image/bmp" to "bmp"
                else -> {
                    val head = String(data, 0, minOf(data.size, 256), Charsets.ISO_8859_1)
                    if (head.contains("<svg")) "image/svg+xml" to "svg" else null
                }
            }
        }
    }
}
