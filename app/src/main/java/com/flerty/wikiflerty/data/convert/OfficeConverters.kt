package com.flerty.wikiflerty.data.convert

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

internal object Xml {
    fun parse(bytes: ByteArray): Element? = runCatching {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.isExpandEntityReferences = false }
        f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }.getOrNull()

    fun Node.local(): String = localName ?: nodeName.substringAfter(':')

    fun Element.kids(): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) {
            if (n is Element) out += n
            n = n.nextSibling
        }
        return out
    }

    fun Element.kid(name: String): Element? = kids().firstOrNull { it.local() == name }

    fun Element.attr(name: String): String? {
        val a = attributes ?: return null
        for (i in 0 until a.length) {
            val n = a.item(i)
            if (n.local() == name) return n.nodeValue
        }
        return null
    }

    fun Element.all(name: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(e: Element) {
            e.kids().forEach { k ->
                if (k.local() == name) out += k
                walk(k)
            }
        }
        walk(this)
        return out
    }

    fun Element.text(): String = textContent ?: ""
}

private fun ZipFile.bytes(name: String): ByteArray? = getEntry(name)?.let { e -> getInputStream(e).use { it.readBytes() } }

private fun ZipFile.stream(name: String): java.io.InputStream? = getEntry(name)?.let { getInputStream(it) }

private fun resolvePath(base: String, target: String): String {
    if (target.startsWith("/")) return target.removePrefix("/")
    val parts = base.split('/').filter { it.isNotEmpty() }.toMutableList()
    target.split('/').forEach { p ->
        when (p) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += p
        }
    }
    return parts.joinToString("/")
}

object DocxConverter {
    private class Style(val name: String, val outline: Int?, val basedOn: String?, val bold: Boolean?, val italic: Boolean?)

    fun read(file: File, fallbackTitle: String): RichDoc = ZipFile(file).use { zip ->
        with(Lite) {
            val document = zip.stream("word/document.xml")?.use { parse(it) } ?: throw IllegalArgumentException("no document")
            val styles = HashMap<String, Style>()
            zip.stream("word/styles.xml")?.use { parse(it) }?.kids()?.filter { it.local() == "style" }?.forEach { s ->
                val id = s.attr("styleId") ?: return@forEach
                val name = s.kid("name")?.attr("val")?.lowercase().orEmpty()
                val outline = s.kid("pPr")?.kid("outlineLvl")?.attr("val")?.toIntOrNull()
                val rpr = s.kid("rPr")
                styles[id] = Style(name, outline, s.kid("basedOn")?.attr("val"), rpr?.kid("b")?.let { on(it) }, rpr?.kid("i")?.let { on(it) })
            }
            val rels = HashMap<String, String>()
            val external = HashSet<String>()
            zip.stream("word/_rels/document.xml.rels")?.use { parse(it) }?.kids()?.forEach { r ->
                val id = r.attr("Id") ?: return@forEach
                rels[id] = r.attr("Target") ?: return@forEach
                if (r.attr("TargetMode") == "External") external += id
            }
            fun notesOf(path: String, tag: String): Map<String, XEl> =
                zip.stream(path)?.use { parse(it) }?.kids()?.filter { it.local() == tag && it.attr("type") == null }?.mapNotNull { n -> n.attr("id")?.let { it to n } }?.toMap().orEmpty()
            val footnotes = notesOf("word/footnotes.xml", "footnote")
            val endnotes = notesOf("word/endnotes.xml", "endnote")
            val noteOrder = LinkedHashMap<String, Int>()
            var currentNote: Int? = null
            var href: String? = null
            val core = zip.stream("docProps/core.xml")?.use { parse(it) }
            val title = core?.all("title")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val author = core?.all("creator")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
            val doc = RichDoc(title, listOfNotNull(author))
            doc.description = core?.all("description")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
            val imageCache = HashMap<String, String?>()

            fun headingOf(styleId: String?): Int {
                var id = styleId
                var depth = 0
                while (id != null && depth < 8) {
                    val s = styles[id] ?: return headingFromName(id.lowercase())
                    s.outline?.let { if (it in 0..5) return it + 1 }
                    val h = headingFromName(s.name)
                    if (h > 0) return h
                    id = s.basedOn
                    depth++
                }
                return 0
            }

            fun styleBold(styleId: String?): Boolean {
                var id = styleId
                var depth = 0
                while (id != null && depth < 8) {
                    val s = styles[id] ?: return false
                    s.bold?.let { return it }
                    id = s.basedOn
                    depth++
                }
                return false
            }

            fun image(rid: String?): String? {
                if (rid == null) return null
                return imageCache.getOrPut(rid) {
                    val target = rels[rid] ?: return@getOrPut null
                    zip.bytes(resolvePath("word", target))?.let { doc.addImage(it) }
                }
            }

            fun paragraph(p: XEl) {
                val ppr = p.kid("pPr")
                val styleId = ppr?.kid("pStyle")?.attr("val")
                var level = headingOf(styleId)
                ppr?.kid("outlineLvl")?.attr("val")?.toIntOrNull()?.let { if (it in 0..5) level = it + 1 }
                val align = when (ppr?.kid("jc")?.attr("val")) {
                    "center" -> Align.Center
                    "right", "end" -> Align.End
                    else -> Align.Start
                }
                if (ppr?.kid("pageBreakBefore")?.let { on(it) } == true) doc.blocks += Block.Break
                val list = ppr?.kid("numPr") != null
                val baseBold = styleBold(styleId)
                val line = LineBuilder()
                if (list) line.add("• ")
                val pending = ArrayList<Block>()
                val marks = ArrayList<String>()
                fun flush() {
                    val runs = line.take()
                    marks.forEach { doc.blocks += Block.Anchor(it) }
                    marks.clear()
                    if (runs.isNotEmpty()) doc.blocks += if (level > 0) Block.Heading(level, runs) else Block.Para(runs, align)
                    doc.blocks += pending
                    pending.clear()
                }
                fun run(r: XEl) {
                    val rpr = r.kid("rPr")
                    val bold = rpr?.kid("b")?.let { on(it) } ?: baseBold
                    val italic = rpr?.kid("i")?.let { on(it) } ?: false
                    val underline = rpr?.kid("u")?.attr("val")?.let { it != "none" } ?: false
                    val va = rpr?.kid("vertAlign")?.attr("val")
                    r.kids().forEach { k ->
                        when (k.local()) {
                            "t" -> line.add(k.text(), bold, italic, underline, va == "superscript", va == "subscript", href)
                            "footnoteReference", "endnoteReference" -> k.attr("id")?.let { id ->
                                val key = (if (k.local() == "footnoteReference") "f" else "e") + id
                                val n = noteOrder.getOrPut(key) { noteOrder.size + 1 }
                                line.add("[$n]", false, false, false, true, false, "#note_$key")
                            }
                            "footnoteRef", "endnoteRef" -> currentNote?.let { line.add("$it. ", true) }
                            "tab" -> line.add(" ")
                            "noBreakHyphen" -> line.add("-")
                            "softHyphen" -> Unit
                            "br", "cr" -> if (k.attr("type") == "page") {
                                flush()
                                doc.blocks += Block.Break
                            } else if (level > 0) line.add(" ") else flush()
                            "drawing", "pict", "object" -> {
                                val rid = k.all("blip").firstOrNull()?.attr("embed") ?: k.all("imagedata").firstOrNull()?.attr("id")
                                image(rid)?.let { pending += Block.Image(it) }
                            }
                            "lastRenderedPageBreak" -> Unit
                        }
                    }
                }
                fun walk(e: XEl) {
                    e.kids().forEach { k ->
                        when (k.local()) {
                            "pPr", "del", "moveFrom", "commentRangeStart", "commentRangeEnd", "bookmarkEnd", "proofErr" -> Unit
                            "bookmarkStart" -> k.attr("name")?.takeIf { it.isNotBlank() && it != "_GoBack" }?.let { marks += "bm_$it" }
                            "r" -> run(k)
                            "hyperlink" -> {
                                val before = href
                                val anchor = k.attr("anchor")
                                val rid = k.attr("id")
                                href = when {
                                    !anchor.isNullOrBlank() -> "#bm_$anchor"
                                    rid != null && rid in external -> rels[rid]
                                    else -> null
                                }
                                walk(k)
                                href = before
                            }
                            "ins", "smartTag", "fldSimple", "sdt", "sdtContent", "customXml", "moveTo", "dir", "bdo" -> walk(k)
                        }
                    }
                }
                walk(p)
                flush()
            }

            fun table(t: XEl) {
                t.kids().filter { it.local() == "tr" }.forEach { tr ->
                    val cells = tr.kids().filter { it.local() == "tc" }.map { tc ->
                        tc.all("t").joinToString("") { it.text() }.replace(Regex("\\s+"), " ").trim()
                    }.filter { it.isNotEmpty() }
                    if (cells.isNotEmpty()) doc.blocks += Block.Para(listOf(Run(cells.joinToString("  ·  "))))
                }
            }

            fun body(e: XEl) {
                e.kids().forEach { k ->
                    when (k.local()) {
                        "p" -> paragraph(k)
                        "tbl" -> table(k)
                        "bookmarkStart" -> k.attr("name")?.takeIf { it.isNotBlank() && it != "_GoBack" }?.let { doc.blocks += Block.Anchor("bm_$it") }
                        "sdt", "sdtContent", "customXml" -> body(k)
                        "sectPr" -> Unit
                    }
                }
            }
            document.kid("body")?.let { body(it) }
            if (noteOrder.isNotEmpty()) {
                doc.blocks += Block.Break
                doc.blocks += Block.Heading(1, listOf(Run("Примечания")))
                noteOrder.forEach { (key, n) ->
                    val note = (if (key[0] == 'f') footnotes else endnotes)[key.substring(1)] ?: return@forEach
                    doc.blocks += Block.Anchor("note_$key")
                    currentNote = n
                    body(note)
                }
                currentNote = null
            }
            if (doc.cover == null) {
                val first = doc.blocks.take(3).firstOrNull { it is Block.Image } as? Block.Image
                if (first != null && doc.blocks.indexOf(first) == 0) {
                    doc.cover = first.href
                    doc.blocks.remove(first)
                }
            }
            doc
        }
    }

    private fun on(e: XEl): Boolean = with(Lite) { e.attr("val")?.lowercase()?.let { it != "false" && it != "0" && it != "off" && it != "none" } ?: true }

    internal fun headingFromName(name: String): Int {
        val n = name.lowercase().replace("_20_", " ")
        if (n == "title" || n == "название" || n == "заголовок") return 1
        val m = Regex("^(heading|заголовок|überschrift|titre|título|titolo|nagłówek)\\s*(\\d)").find(n) ?: return 0
        return m.groupValues[2].toInt().coerceIn(1, 6)
    }
}

object OdtConverter {
    private class Style(val parent: String?, val bold: Boolean?, val italic: Boolean?, val underline: Boolean?, val pos: Int?, val align: Align?, val breakBefore: Boolean, val heading: Int)

    fun read(file: File, fallbackTitle: String): RichDoc = ZipFile(file).use { zip ->
        with(Xml) {
            val content = zip.bytes("content.xml")?.let { parse(it) } ?: throw IllegalArgumentException("no content")
            val styles = HashMap<String, Style>()
            fun collect(root: Element?) {
                root?.all("style")?.forEach { s ->
                    val name = s.attr("name") ?: return@forEach
                    val tp = s.kid("text-properties")
                    val pp = s.kid("paragraph-properties")
                    val weight = tp?.attr("font-weight")
                    val pos = tp?.attr("text-position")?.let { if (it.startsWith("super")) 1 else if (it.startsWith("sub")) -1 else if (it.startsWith("-")) -1 else if (it.firstOrNull()?.isDigit() == true && it != "0%" && !it.startsWith("0 ")) 1 else 0 }
                    val align = when (pp?.attr("text-align")) {
                        "center" -> Align.Center
                        "end", "right" -> Align.End
                        "start", "left", "justify" -> Align.Start
                        else -> null
                    }
                    val display = (s.attr("display-name") ?: name).lowercase()
                    val outline = s.attr("default-outline-level")?.toIntOrNull()
                    styles[name] = Style(
                        s.attr("parent-style-name"),
                        weight?.let { it == "bold" || it.startsWith("6") || it.startsWith("7") || it.startsWith("8") || it.startsWith("9") },
                        tp?.attr("font-style")?.let { it == "italic" || it == "oblique" },
                        tp?.attr("text-underline-style")?.let { it != "none" },
                        pos,
                        align,
                        pp?.attr("break-before") == "page",
                        outline ?: DocxConverter.headingFromName(display),
                    )
                }
            }
            collect(zip.bytes("styles.xml")?.let { parse(it) })
            collect(content)
            fun <T> resolve(name: String?, pick: (Style) -> T?): T? {
                var n = name
                var d = 0
                while (n != null && d < 10) {
                    val s = styles[n] ?: return null
                    pick(s)?.let { return it }
                    n = s.parent
                    d++
                }
                return null
            }
            val meta = zip.bytes("meta.xml")?.let { parse(it) }
            val title = meta?.all("title")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val author = (meta?.all("creator")?.firstOrNull() ?: meta?.all("initial-creator")?.firstOrNull())?.text()?.trim()?.takeIf { it.isNotBlank() }
            val doc = RichDoc(title, listOfNotNull(author))
            doc.description = meta?.all("description")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
            val notes = ArrayList<Element>()
            var notePrefix: String? = null
            var href: String? = null

            fun paragraph(p: Element, level0: Int, bullet: Boolean) {
                val sn = p.attr("style-name")
                val level = if (level0 > 0) level0 else resolve(sn) { s -> s.heading.takeIf { it > 0 } } ?: 0
                val align = resolve(sn) { it.align } ?: Align.Start
                if (resolve(sn) { s -> if (s.breakBefore) true else null } == true) doc.blocks += Block.Break
                val line = LineBuilder()
                if (bullet) line.add("• ")
                notePrefix?.let {
                    line.add(it, true)
                    notePrefix = null
                }
                val pending = ArrayList<Block>()
                val marks = ArrayList<String>()
                fun flush() {
                    val runs = line.take()
                    marks.forEach { doc.blocks += Block.Anchor(it) }
                    marks.clear()
                    if (runs.isNotEmpty()) doc.blocks += if (level > 0) Block.Heading(level, runs) else Block.Para(runs, align)
                    doc.blocks += pending
                    pending.clear()
                }
                fun walk(e: Element, b: Boolean, i: Boolean, u: Boolean, pos: Int) {
                    var n = e.firstChild
                    while (n != null) {
                        if (n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) {
                            line.add(n.nodeValue ?: "", b, i, u, pos > 0, pos < 0, href)
                        } else if (n is Element) {
                            when (n.local()) {
                                "span" -> {
                                    val s = n.attr("style-name")
                                    walk(n, resolve(s) { it.bold } ?: b, resolve(s) { it.italic } ?: i, resolve(s) { it.underline } ?: u, resolve(s) { it.pos } ?: pos)
                                }
                                "a" -> {
                                    val before = href
                                    val target = n.attr("href")?.trim().orEmpty()
                                    href = when {
                                        target.startsWith("#") -> "#bm_" + target.substring(1).substringBefore('|')
                                        target.startsWith("http://", true) || target.startsWith("https://", true) || target.startsWith("mailto:", true) -> target
                                        else -> null
                                    }
                                    walk(n, b, i, u, pos)
                                    href = before
                                }
                                "meta", "ruby", "ruby-base" -> walk(n, b, i, u, pos)
                                "bookmark", "bookmark-start" -> n.attr("name")?.takeIf { it.isNotBlank() }?.let { marks += "bm_$it" }
                                "note" -> {
                                    notes += n
                                    val k = notes.size
                                    line.add("[$k]", false, false, false, true, false, "#note_o$k")
                                }
                                "s" -> line.add(" ".repeat((n.attr("c")?.toIntOrNull() ?: 1).coerceIn(1, 8)))
                                "tab" -> line.add(" ")
                                "line-break" -> if (level > 0) line.add(" ") else flush()
                                "frame" -> n.all("image").forEach { img ->
                                    val href = img.attr("href") ?: return@forEach
                                    zip.bytes(href.removePrefix("./"))?.let { doc.addImage(it) }?.let { pending += Block.Image(it) }
                                }
                                "annotation", "bookmark-end", "soft-page-break", "change", "change-start", "change-end", "sequence-ref" -> Unit
                                else -> walk(n, b, i, u, pos)
                            }
                        }
                        n = n.nextSibling
                    }
                }
                val s = sn
                walk(p, resolve(s) { it.bold } ?: false, resolve(s) { it.italic } ?: false, resolve(s) { it.underline } ?: false, resolve(s) { it.pos } ?: 0)
                flush()
            }

            fun body(e: Element, bullet: Boolean = false) {
                e.kids().forEach { k ->
                    when (k.local()) {
                        "h" -> paragraph(k, (k.attr("outline-level")?.toIntOrNull() ?: 1).coerceIn(1, 6), false)
                        "p" -> paragraph(k, 0, bullet)
                        "list" -> body(k, true)
                        "list-item", "list-header" -> body(k, bullet)
                        "section", "index-body", "table-of-content", "illustration-index", "alphabetical-index" -> if (k.local() == "section") body(k, bullet)
                        "table" -> k.all("table-row").forEach { tr ->
                            val cells = tr.kids().filter { it.local() == "table-cell" }.map { it.text().replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
                            if (cells.isNotEmpty()) doc.blocks += Block.Para(listOf(Run(cells.joinToString("  ·  "))))
                        }
                        "frame" -> k.all("image").forEach { img ->
                            val href = img.attr("href") ?: return@forEach
                            zip.bytes(href.removePrefix("./"))?.let { doc.addImage(it) }?.let { doc.blocks += Block.Image(it) }
                        }
                    }
                }
            }
            content.all("text").firstOrNull { it.parentNode?.local() == "body" }?.let { body(it) }
            if (notes.isNotEmpty()) {
                doc.blocks += Block.Break
                doc.blocks += Block.Heading(1, listOf(Run("Примечания")))
                var k = 0
                while (k < notes.size) {
                    val note = notes[k]
                    k++
                    doc.blocks += Block.Anchor("note_o$k")
                    notePrefix = "$k. "
                    note.kids().firstOrNull { it.local() == "note-body" }?.let { body(it) }
                    notePrefix = null
                }
            }
            val first = doc.blocks.firstOrNull()
            if (first is Block.Image) {
                doc.cover = first.href
                doc.blocks.removeAt(0)
            }
            doc
        }
    }
}

object RtfConverter {
    private class State(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var sup: Boolean = false,
        var sub: Boolean = false,
        var skip: Boolean = false,
        var uc: Int = 1,
        var font: Int = -1,
        var dest: String? = null,
        var align: Align = Align.Start,
        var level: Int = 0,
        var style: Int = 0,
    ) {
        fun copy() = State(bold, italic, underline, sup, sub, skip, uc, font, dest, align, level, style)
    }

    private val skipDest = setOf(
        "colortbl", "header", "headerl", "headerr", "headerf", "footer", "footerl", "footerr", "footerf", "footnote",
        "fldinst", "object", "themedata", "colorschememapping", "latentstyles", "datastore", "xmlnstbl", "listtable",
        "listoverridetable", "rsidtbl", "generator", "mmathpr", "pgdsctbl", "revtbl", "filetbl", "bkmkstart", "bkmkend",
        "nonshppict", "annotation", "atnid", "atnauthor", "atrfstart", "atrfend", "comment", "doccomm", "operator",
        "keywords", "subject", "company", "category", "manager", "hlinkbase", "printim", "revtim", "creatim", "buptim",
        "xe", "tc", "txe", "fchars", "lchars", "pnseclvl", "pntext", "pntxta", "pntxtb", "listtext", "wgrffmtfilter",
        "shpinst", "sp", "sn", "sv", "objdata", "objclass", "blipuid", "panose", "falt", "fname", "passwordhash",
    )

    private fun cp(charset: Int, ansi: Int): Int = when (charset) {
        204 -> 1251
        238 -> 1250
        161 -> 1253
        162 -> 1254
        177 -> 1255
        178 -> 1256
        186 -> 1257
        163 -> 1258
        222 -> 874
        128 -> 932
        134 -> 936
        129 -> 949
        136 -> 950
        else -> ansi
    }

    fun read(file: File, fallbackTitle: String): RichDoc {
        val src = String(file.readBytes(), Charsets.ISO_8859_1)
        val doc = RichDoc(fallbackTitle)
        var ansi = 1252
        val fontCharset = HashMap<Int, Int>()
        val styleHeading = HashMap<Int, Int>()
        val stack = ArrayList<State>()
        var st = State()
        val line = LineBuilder()
        val bytes = java.io.ByteArrayOutputStream()
        var skipChars = 0
        val captured = StringBuilder()
        var infoTitle: String? = null
        var infoAuthor: String? = null
        var fontNo = -1
        var fontSet = 0
        var styleNo = 0
        var styleOutline = -1
        val pict = StringBuilder()
        var pictKind: String? = null
        var groupDepthOfPict = -1

        fun charset(): java.nio.charset.Charset {
            val c = if (st.font >= 0) fontCharset[st.font]?.let { cp(it, ansi) } ?: ansi else ansi
            return runCatching { java.nio.charset.Charset.forName("windows-$c") }.recoverCatching { java.nio.charset.Charset.forName("cp$c") }.getOrDefault(Charsets.ISO_8859_1)
        }

        val notes = ArrayList<String>()
        val noteBuf = StringBuilder()

        fun out(s: String) {
            when (st.dest) {
                "fonttbl", "stylesheet", "title", "author" -> captured.append(s)
                "footnote" -> noteBuf.append(s)
                null -> if (!st.skip) line.add(s, st.bold, st.italic, st.underline, st.sup, st.sub)
                else -> Unit
            }
        }

        fun drain() {
            if (bytes.size() == 0) return
            out(String(bytes.toByteArray(), charset()))
            bytes.reset()
        }

        fun flush() {
            drain()
            if (st.dest == "footnote") {
                noteBuf.append('\n')
                return
            }
            val runs = line.take()
            if (runs.isEmpty()) return
            val level = if (st.level > 0) st.level else styleHeading[st.style] ?: 0
            doc.blocks += if (level > 0) Block.Heading(level, runs) else Block.Para(runs, st.align)
        }

        fun endGroup() {
            drain()
            val closing = st
            if (closing.dest == "footnote" && stack.lastOrNull()?.dest != "footnote") {
                notes += noteBuf.toString()
                noteBuf.setLength(0)
            }
            when (closing.dest) {
                "title" -> captured.toString().trim().takeIf { t -> t.any { it.isLetterOrDigit() } }?.let { infoTitle = it }
                "author" -> captured.toString().trim().takeIf { t -> t.any { it.isLetterOrDigit() } }?.let { infoAuthor = it }
            }
            if (closing.dest == "fonttbl" && fontNo >= 0) {
                fontCharset[fontNo] = fontSet
            }
            if (closing.dest == "stylesheet" && styleOutline >= 0) {
                styleHeading[styleNo] = styleOutline + 1
            } else if (closing.dest == "stylesheet") {
                val h = DocxConverter.headingFromName(captured.toString().substringBefore(';').trim())
                if (h > 0) styleHeading[styleNo] = h
            }
            if (stack.size == groupDepthOfPict) {
                val hex = pict.toString().filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.let { if (it.length % 2 == 1) it.dropLast(1) else it }
                if (pictKind != null && hex.length >= 16) {
                    val data = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
                    doc.addImage(data)?.let {
                        flush()
                        doc.blocks += Block.Image(it)
                    }
                }
                pict.setLength(0)
                pictKind = null
                groupDepthOfPict = -1
            }
            st = if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else State()
            if (closing.dest == "fonttbl" && st.dest == "fonttbl") {
                fontNo = -1
                fontSet = 0
                captured.setLength(0)
            }
            if (closing.dest == "stylesheet" && st.dest == "stylesheet") {
                styleNo = 0
                styleOutline = -1
                captured.setLength(0)
            }
        }

        var i = 0
        var ignorable = false
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                c == '{' -> {
                    drain()
                    stack += st
                    st = st.copy()
                    i++
                }
                c == '}' -> {
                    endGroup()
                    i++
                }
                c == '\\' -> {
                    i++
                    if (i >= n) break
                    val d = src[i]
                    if (d.isLetter()) {
                        val s = i
                        while (i < n && src[i].isLetter()) i++
                        val word = src.substring(s, i)
                        var param: Int? = null
                        if (i < n && (src[i] == '-' || src[i].isDigit())) {
                            val ps = i
                            i++
                            while (i < n && src[i].isDigit()) i++
                            param = src.substring(ps, i).toIntOrNull()
                        }
                        if (i < n && src[i] == ' ') i++
                        if (st.dest == "pict") {
                            when (word) {
                                "pngblip" -> pictKind = "png"
                                "jpegblip" -> pictKind = "jpg"
                            }
                            continue
                        }
                        if (skipChars > 0) {
                            skipChars--
                            continue
                        }
                        val starred = ignorable
                        ignorable = false
                        if (st.skip) continue
                        when (word) {
                            "shppict", "ud", "upr" -> Unit
                            "footnote" -> if (st.dest == null) {
                                drain()
                                val k = notes.size + 1
                                line.add("[$k]", false, false, false, true, false, "#note_r$k")
                                st.dest = "footnote"
                                noteBuf.setLength(0)
                            } else {
                                st.dest = "skip"
                                st.skip = true
                            }
                            "ansicpg" -> ansi = param ?: ansi
                            "fonttbl", "stylesheet", "info" -> {
                                drain()
                                st.dest = word
                            }
                            "title", "author" -> if (st.dest == "info" || stack.lastOrNull()?.dest == "info") {
                                st.dest = word
                                captured.setLength(0)
                            }
                            "f" -> if (st.dest == "fonttbl") {
                                fontNo = param ?: 0
                            } else {
                                drain()
                                st.font = param ?: 0
                            }
                            "fcharset" -> if (st.dest == "fonttbl") fontSet = param ?: 0
                            "s" -> if (st.dest == "stylesheet") styleNo = param ?: 0 else {
                                val next = param ?: 0
                                if (next != st.style && (styleHeading[next] ?: 0) != (styleHeading[st.style] ?: 0)) flush()
                                st.style = next
                            }
                            "outlinelevel" -> if (st.dest == "stylesheet") styleOutline = param ?: -1 else st.level = (param ?: -1).let { if (it in 0..5) it + 1 else 0 }
                            "pict" -> {
                                drain()
                                st.dest = "pict"
                                groupDepthOfPict = stack.size
                                pict.setLength(0)
                                pictKind = null
                            }
                            "fldrslt" -> Unit
                            "par" -> flush()
                            "line" -> if (st.level > 0 || (styleHeading[st.style] ?: 0) > 0) { drain(); out(" ") } else flush()
                            "sect" -> flush()
                            "page" -> {
                                flush()
                                doc.blocks += Block.Break
                            }
                            "pard" -> {
                                st.align = Align.Start
                                st.level = 0
                                st.style = 0
                            }
                            "qc" -> st.align = Align.Center
                            "qr" -> st.align = Align.End
                            "ql", "qj" -> st.align = Align.Start
                            "plain" -> {
                                drain()
                                st.bold = false; st.italic = false; st.underline = false; st.sup = false; st.sub = false
                            }
                            "b" -> { drain(); st.bold = param != 0 }
                            "i" -> { drain(); st.italic = param != 0 }
                            "ul", "uld", "uldb", "ulw" -> { drain(); st.underline = param != 0 }
                            "ulnone" -> { drain(); st.underline = false }
                            "super" -> { drain(); st.sup = true; st.sub = false }
                            "sub" -> { drain(); st.sub = true; st.sup = false }
                            "nosupersub" -> { drain(); st.sup = false; st.sub = false }
                            "tab" -> out(" ")
                            "cell" -> out("  ·  ")
                            "row" -> flush()
                            "emdash" -> { drain(); out("—") }
                            "endash" -> { drain(); out("–") }
                            "bullet" -> { drain(); out("•") }
                            "lquote" -> { drain(); out("‘") }
                            "rquote" -> { drain(); out("’") }
                            "ldblquote" -> { drain(); out("«") }
                            "rdblquote" -> { drain(); out("»") }
                            "uc" -> st.uc = param ?: 1
                            "u" -> {
                                drain()
                                var code = param ?: 0
                                if (code < 0) code += 65536
                                out(code.toChar().toString())
                                skipChars = st.uc
                            }
                            else -> if (starred || word in skipDest) {
                                drain()
                                st.dest = "skip"
                                st.skip = true
                            }
                        }
                    } else {
                        i++
                        if (st.dest == "pict") continue
                        when (d) {
                            '\'' -> {
                                if (i + 2 <= n) {
                                    val hex = src.substring(i, i + 2)
                                    i += 2
                                    if (skipChars > 0) {
                                        skipChars--
                                    } else {
                                        hex.toIntOrNull(16)?.let { bytes.write(it) }
                                    }
                                }
                            }
                            '*' -> ignorable = true
                            '~' -> { drain(); out(" ") }
                            '-' -> Unit
                            '_' -> { drain(); out("-") }
                            '\\', '{', '}' -> {
                                if (skipChars > 0) skipChars-- else { drain(); out(d.toString()) }
                            }
                            '\n', '\r' -> flush()
                            else -> Unit
                        }
                    }
                }
                c == '\r' || c == '\n' -> i++
                else -> {
                    if (st.dest == "pict") {
                        if (stack.size == groupDepthOfPict) pict.append(c)
                    } else if (skipChars > 0) {
                        skipChars--
                    } else {
                        drain()
                        if (st.dest == "fonttbl" || st.dest == "stylesheet") {
                            if (c == ';') {
                                if (st.dest == "fonttbl" && fontNo >= 0) fontCharset[fontNo] = fontSet
                            } else captured.append(c)
                        } else {
                            out(c.toString())
                        }
                    }
                    i++
                }
            }
        }
        flush()
        if (notes.isNotEmpty()) {
            doc.blocks += Block.Break
            doc.blocks += Block.Heading(1, listOf(Run("Примечания")))
            notes.forEachIndexed { i, text ->
                doc.blocks += Block.Anchor("note_r${i + 1}")
                text.split('\n').map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.forEachIndexed { j, part ->
                    doc.blocks += Block.Para(if (j == 0) listOf(Run("${i + 1}. ", bold = true), Run(part)) else listOf(Run(part)))
                }
            }
        }
        infoTitle?.let { doc.title = it }
        infoAuthor?.let { doc.authors = listOf(it) }
        return doc
    }
}
