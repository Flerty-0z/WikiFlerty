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
    val href: String? = null,
) {
    fun same(o: Run) = bold == o.bold && italic == o.italic && underline == o.underline && sup == o.sup && sub == o.sub && href == o.href

    fun with(text: String = this.text, bold: Boolean = this.bold) = Run(text, bold, italic, underline, sup, sub, href)
}

class TocItem(val title: String, val target: String, val depth: Int)

enum class Align { Start, Center, End }

sealed class Block {
    class Heading(val level: Int, val runs: List<Run>) : Block()
    class Para(val runs: List<Run>, val align: Align = Align.Start, val quote: Boolean = false) : Block()
    class Image(val href: String) : Block()
    object Break : Block()
    object Rule : Block()
    class Anchor(val id: String) : Block()
}

val List<Run>.plain: String get() = joinToString("") { it.text }

fun List<Run>.unbold(): List<Run> = map { it.with(bold = false) }

class LineBuilder {
    val runs = mutableListOf<Run>()

    val isEmpty: Boolean get() = runs.all { it.text.isBlank() }

    fun add(text: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, sup: Boolean = false, sub: Boolean = false, href: String? = null) {
        if (text.isEmpty()) return
        val run = Run(text, bold, italic, underline, sup, sub, href)
        val last = runs.lastOrNull()
        if (last != null && last.same(run)) runs[runs.lastIndex] = last.with(text = last.text + text) else runs += run
    }

    fun take(): List<Run> {
        val out = mutableListOf<Run>()
        runs.forEach { r ->
            var t = r.text.replace(' ', ' ').replace(Regex("[ \\t\\r\\n]+"), " ")
            if (t.startsWith(" ") && (out.isEmpty() || out.last().text.endsWith(" "))) t = t.substring(1)
            if (t.isNotEmpty()) out += r.with(text = t)
        }
        runs.clear()
        while (out.isNotEmpty() && out.first().text.isBlank()) out.removeAt(0)
        while (out.isNotEmpty() && out.last().text.isBlank()) out.removeAt(out.lastIndex)
        if (out.isNotEmpty()) {
            val f = out.first()
            out[0] = f.with(text = f.text.trimStart())
            val l = out.last()
            out[out.lastIndex] = l.with(text = l.text.trimEnd())
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
    val toc = mutableListOf<TocItem>()

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
        val norm = normalize(blocks)
        val known = norm.mapNotNullTo(HashSet()) { (it as? Block.Anchor)?.id }
        val explicit = toc.filter { it.target in known && it.title.isNotBlank() && !noteText.matches(it.title.trim()) }.distinctBy { it.target to it.title }
        val manual = explicit.size >= 2
        val chapters = if (manual) splitAt(norm, explicit.mapTo(HashSet()) { it.target }) else split(norm)
        class Plan(val href: String, val chapter: Chapter, val blocks: List<Block>, val first: Boolean, val ci: Int)
        val plans = ArrayList<Plan>()
        var page = 0
        if (cover != null) {
            page++
            book.addPage("c0.xhtml", xhtmlHead(title, lang) + """<div class="cover"><img src="${esc(cover!!)}" alt=""/></div>""" + "\n" + XHTML_END)
        }
        chapters.forEachIndexed { ci, chapter ->
            chapter.blocks.chunked(700).forEachIndexed { pi, part ->
                page++
                plans += Plan("c$page.xhtml", chapter, part, pi == 0, ci)
            }
        }
        val where = HashMap<String, String>()
        val leading = HashSet<String>()
        plans.forEach { plan ->
            var content = false
            plan.blocks.forEach { b ->
                if (b is Block.Anchor) {
                    where.putIfAbsent(safeId(b.id), plan.href)
                    if (!content) leading += safeId(b.id)
                } else if (b !is Block.Break) content = true
            }
        }
        val resolve: (String) -> String? = { href ->
            when {
                href.startsWith("#") -> safeId(href.substring(1)).let { id -> where[id]?.let { f -> "$f#$id" } }
                href.startsWith("http://", true) || href.startsWith("https://", true) || href.startsWith("mailto:", true) -> href
                else -> null
            }
        }
        var anchor = 0
        plans.forEach { plan ->
            val chapter = plan.chapter
            val href = plan.href
            val html = StringBuilder(xhtmlHead(chapter.title ?: title, lang))
            if (!manual && plan.ci == 0 && plan.first && chapter.title == null && cover == null && !chapters.any { it.title != null }) {
                html.append("<h1>${esc(title)}</h1>\n")
            }
            var entry: TocEntry? = null
            if (!manual && plan.first && chapter.title != null) {
                entry = TocEntry(clean(chapter.title), href)
                book.toc += entry
            }
            val pending = ArrayList<String>()
            fun ids(): Pair<String, String> {
                if (pending.isEmpty()) return "" to ""
                val first = " id=\"${esc(pending[0])}\""
                val rest = pending.drop(1).joinToString("") { "<span id=\"${esc(it)}\"></span>" }
                pending.clear()
                return first to rest
            }
            plan.blocks.forEach { b ->
                when (b) {
                    is Block.Anchor -> safeId(b.id).let { if (it !in pending) pending += it }
                    is Block.Heading -> {
                        val level = b.level.coerceIn(1, 6)
                        if (!manual && b === chapter.head) {
                            val (id, extra) = ids()
                            html.append("<h2$id>").append(extra).append(render(b.runs.unbold(), resolve)).append("</h2>\n")
                        } else {
                            if (pending.isEmpty()) pending += "h${++anchor}"
                            val own = pending[0]
                            val (id, extra) = ids()
                            val tag = "h${(level + 1).coerceAtMost(6)}"
                            html.append("<$tag$id>").append(extra).append(render(b.runs.unbold(), resolve)).append("</$tag>\n")
                            val t = clean(b.runs.filter { it.href == null }.plain).take(120)
                            if (!manual && t.isNotEmpty() && level <= chapter.level + 1) {
                                val child = TocEntry(t, "$href#$own")
                                if (entry != null) entry.children += child else book.toc += child
                            }
                        }
                    }
                    is Block.Para -> {
                        val body = render(b.runs, resolve)
                        val style = when (b.align) {
                            Align.Center -> " style=\"text-align:center;text-indent:0\""
                            Align.End -> " style=\"text-align:right;text-indent:0\""
                            Align.Start -> ""
                        }
                        val (id, extra) = ids()
                        if (b.quote) html.append("<blockquote$id><p$style>$extra$body</p></blockquote>\n") else html.append("<p$id$style>$extra$body</p>\n")
                    }
                    is Block.Image -> {
                        val (id, extra) = ids()
                        html.append("<div class=\"image\"$id>$extra<img src=\"${esc(b.href)}\" alt=\"\"/></div>\n")
                    }
                    Block.Rule -> {
                        val (id, extra) = ids()
                        html.append("<p class=\"subtitle\"$id>$extra* * *</p>\n")
                    }
                    Block.Break -> Unit
                }
            }
            if (pending.isNotEmpty()) html.append(pending.joinToString("") { "<span id=\"${esc(it)}\"></span>" }).append('\n')
            html.append(XHTML_END)
            book.addPage(href, html.toString())
        }
        if (page == 0) book.addPage("c1.xhtml", xhtmlHead(title, lang) + "<h1>${esc(title)}</h1>\n" + XHTML_END)
        if (manual) {
            val stack = ArrayList<Pair<Int, TocEntry>>()
            explicit.forEach { item ->
                val id = safeId(item.target)
                val file = where[id] ?: return@forEach
                val link = if (id in leading) file else "$file#$id"
                val e = TocEntry(clean(item.title).take(160), link)
                while (stack.isNotEmpty() && stack.last().first >= item.depth) stack.removeAt(stack.lastIndex)
                if (stack.isEmpty()) book.toc += e else stack.last().second.children += e
                stack += item.depth to e
            }
        } else if (book.toc.isNotEmpty() && chapters.firstOrNull()?.title == null && chapters.first().blocks.any { it is Block.Para || it is Block.Image }) {
            book.toc.add(0, TocEntry("Начало", if (cover != null) "c2.xhtml" else "c1.xhtml"))
        }
        book.write(target)
    }

    private fun tidyPara(p: Block.Para): Block.Para? {
        val t = p.runs.plain
        val bare = t.trim()
        if (bare.length >= 40 && bare.none { it.isWhitespace() } && Regex("\\d{12,}").containsMatchIn(bare) && !bare.contains("://")) return null
        val glued = Regex("(?<=[\\p{L}\\d])(Глава|Часть|Книга|Chapter|Part)[\\s\u00a0]?[0-9IVXLC]+").findAll(t).toList()
        if (glued.size < 5) return p
        val first = glued.first().range.first
        val stop = t.substring(0, first).indexOfLast { it == '.' || it == '!' || it == '?' || it == '…' }
        if (stop < 0) return null
        val keep = stop + 1
        val out = ArrayList<Run>()
        var at = 0
        for (r in p.runs) {
            if (at >= keep) break
            val take = minOf(r.text.length, keep - at)
            out += r.with(text = r.text.substring(0, take))
            at += r.text.length
        }
        return if (out.plain.isBlank()) null else Block.Para(out, p.align, p.quote)
    }

    private fun linkNotes(list: MutableList<Block>) {
        if (list.any { b -> (b as? Block.Para)?.runs?.any { it.href != null } == true }) return
        val number = Regex("^[\\[(]?(\\d{1,4})[\\])]?$")
        val lead = Regex("^[\\[(]?(\\d{1,4})[\\].)]\\s+\\S")
        val textOf = { b: Block -> (b as? Block.Heading)?.runs?.plain?.trim() ?: (b as? Block.Para)?.runs?.plain?.trim() }
        val head = list.indexOfLast { b -> b is Block.Heading && Regex("(?i)^(сноски|примечания|комментарии|notes|footnotes|endnotes)$").matches(b.runs.plain.trim()) }
        val from = if (head >= 0) head + 1 else list.size / 2
        val targets = LinkedHashMap<String, Int>()
        for (i in from until list.size) {
            val t = textOf(list[i]) ?: continue
            val n = number.find(t)?.groupValues?.get(1) ?: (if (head >= 0) lead.find(t)?.groupValues?.get(1) else null) ?: continue
            targets.putIfAbsent(n, i)
        }
        if (targets.size < (if (head >= 0) 1 else 3)) return
        var linked = 0
        for (i in 0 until (if (head >= 0) head else from)) {
            val p = list[i] as? Block.Para ?: continue
            if (p.runs.none { it.sup }) continue
            var changed = false
            val runs = p.runs.map { r ->
                val n = if (r.sup && r.href == null) number.find(r.text.trim())?.groupValues?.get(1) else null
                if (n != null && n in targets) {
                    changed = true
                    Run(r.text, r.bold, r.italic, r.underline, r.sup, r.sub, "#note_$n")
                } else r
            }
            if (changed) {
                list[i] = Block.Para(runs, p.align, p.quote)
                linked++
            }
        }
        if (linked == 0) return
        targets.entries.sortedByDescending { it.value }.forEach { (n, at) ->
            val b = list[at]
            if (b is Block.Heading) list[at] = Block.Para(b.runs.unbold().map { it.with(bold = true) }, Align.Start)
            list.add(at, Block.Anchor("note_$n"))
        }
    }

    private fun clean(t: String) = t.replace(Regex("\\s*[\\[(]\\d{1,4}[\\])]"), "").replace(Regex("\\s+"), " ").trim().ifEmpty { t.trim() }

    private fun splitAt(src: List<Block>, targets: Set<String>): List<Chapter> {
        val titles = toc.associate { it.target to it.title }
        val out = mutableListOf<Chapter>()
        var current = Chapter(null, null, 1, mutableListOf())
        fun content() = current.blocks.any { it is Block.Para || it is Block.Image || it is Block.Heading || it == Block.Rule }
        src.forEach { b ->
            when {
                b is Block.Anchor && b.id in targets -> {
                    if (content()) {
                        out += current
                        current = Chapter(titles[b.id], null, 1, mutableListOf())
                    } else if (current.title == null) {
                        current = Chapter(titles[b.id], null, 1, current.blocks)
                    }
                    current.blocks += b
                }
                b == Block.Break -> if (content()) {
                    out += current
                    current = Chapter(null, null, 1, mutableListOf())
                }
                else -> current.blocks += b
            }
        }
        if (content() || current.blocks.any { it is Block.Anchor }) out += current
        return out
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
                    val b = tidyPara(b) ?: return@forEach
                    val t = b.runs.plain.trim()
                    if (t.isEmpty()) return@forEach
                    if (Regex("^[*·•\\-–—~#\\s]{3,}$").matches(t) && t.count { !it.isWhitespace() } in 1..12) out += Block.Rule else out += b
                }
                is Block.Heading -> {
                    val t = b.runs.plain.trim()
                    if (t.isEmpty()) return@forEach
                    if (Regex("^[*·•\\-–—~#\\s]{3,}$").matches(t) && t.count { !it.isWhitespace() } in 1..12) out += Block.Rule else out += b
                }
                Block.Break -> if (out.lastOrNull() != Block.Break) out += b
                else -> out += b
            }
        }
        linkNotes(out)
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
            if (current.blocks.any { it !is Block.Break && it !is Block.Anchor } || current.title != null) out += current
        }
        src.forEach { b ->
            when {
                b is Block.Heading && b.level <= level -> {
                    val t = b.runs.plain.trim()
                    val real = current.blocks.filter { it !is Block.Anchor }
                    val onlyHeadings = real.isNotEmpty() && real.all { it is Block.Heading || it is Block.Image }
                    if (current.title != null && onlyHeadings && real.size <= 2 && real.none { it is Block.Image }) {
                        current.blocks += b
                    } else {
                        val carry = ArrayList<Block>()
                        while (current.blocks.lastOrNull() is Block.Anchor) carry.add(0, current.blocks.removeAt(current.blocks.lastIndex))
                        push()
                        current = Chapter(t.take(120), b, level, (carry + b).toMutableList())
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
        private val noteText = Regex("^[\\[({]?(\\d{1,4}|[*†‡]{1,3})[\\])}]?$")

        fun safeId(id: String): String {
            val t = buildString {
                id.forEach { c -> if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-' || c == '.') append(c) else append("_").append(Integer.toHexString(c.code)) }
            }
            return if (t.isEmpty() || t[0] !in 'A'..'Z' && t[0] !in 'a'..'z') "a$t" else t
        }

        private fun piece(r: Run): String {
            var s = esc(r.text)
            if (r.sup) s = "<sup>$s</sup>"
            if (r.sub) s = "<sub>$s</sub>"
            if (r.underline) s = "<u>$s</u>"
            if (r.italic) s = "<i>$s</i>"
            if (r.bold) s = "<b>$s</b>"
            return s
        }

        fun render(runs: List<Run>, resolve: ((String) -> String?)? = null): String = buildString {
            var i = 0
            while (i < runs.size) {
                val href = runs[i].href
                if (href == null) {
                    append(piece(runs[i]))
                    i++
                    continue
                }
                var j = i
                while (j < runs.size && runs[j].href == href) j++
                val group = runs.subList(i, j)
                val target = resolve?.invoke(href)
                if (target == null) group.forEach { append(piece(it)) }
                else {
                    val label = group.plain.trim()
                    val note = !target.contains("://") && !target.startsWith("mailto:") && (noteText.matches(label) || (label.length <= 6 && group.all { it.sup }))
                    append("<a href=\"").append(esc(target)).append('"')
                    if (note) append(" class=\"noteref\" epub:type=\"noteref\"")
                    append('>')
                    group.forEach { append(piece(it)) }
                    append("</a>")
                }
                i = j
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
