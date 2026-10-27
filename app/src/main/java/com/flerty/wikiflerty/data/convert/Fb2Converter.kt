package com.flerty.wikiflerty.data.convert

import android.util.Base64
import android.util.Xml
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.XHTML_END
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.esc
import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.xhtmlHead
import java.io.File
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

class Fb2Converter(private val identifier: String) {
    private class El(val name: String, val attrs: Map<String, String>) {
        val children = mutableListOf<Any>()

        fun attr(local: String): String? =
            attrs[local] ?: attrs.entries.firstOrNull { it.key.substringAfter(':') == local }?.value

        fun child(name: String): El? = children.firstOrNull { it is El && it.name == name } as El?
        fun all(name: String): List<El> = children.filterIsInstance<El>().filter { it.name == name }
        fun text(): String = buildString { collect(this@El, this) }

        private fun collect(el: El, out: StringBuilder) {
            el.children.forEach { if (it is String) out.append(it) else collect(it as El, out) }
        }
    }

    private class Image(val href: String, val mediaType: String, val data: ByteArray)

    private val images = mutableMapOf<String, Image>()
    private var noteIds = setOf<String>()
    private var language = "ru"
    private var sectionCounter = 0

    fun convert(input: InputStream, target: File, fallbackTitle: String) {
        val root = parse(input)
        val description = root.child("description")
        val info = description?.child("title-info")
        val title = info?.child("book-title")?.text()?.clean()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
        val authors = info?.all("author").orEmpty().mapNotNull { author ->
            listOf("first-name", "middle-name", "last-name")
                .mapNotNull { author.child(it)?.text()?.clean()?.takeIf { s -> s.isNotEmpty() } }
                .joinToString(" ")
                .ifBlank { author.child("nickname")?.text()?.clean() }
                ?.takeIf { it.isNotBlank() }
        }
        language = info?.child("lang")?.text()?.clean()?.lowercase()?.takeIf { it.matches(Regex("[a-z]{2,3}(-[a-z0-9]+)?")) } ?: "ru"

        root.all("binary").forEach { bin ->
            val id = bin.attr("id") ?: return@forEach
            val type = bin.attr("content-type")?.lowercase() ?: "image/jpeg"
            val data = runCatching { Base64.decode(bin.text(), Base64.DEFAULT) }.getOrNull() ?: return@forEach
            val mediaType = when {
                type.contains("png") -> "image/png"
                type.contains("gif") -> "image/gif"
                type.contains("svg") -> "image/svg+xml"
                type.contains("webp") -> "image/webp"
                else -> "image/jpeg"
            }
            val ext = when (mediaType) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/svg+xml" -> "svg"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            images[id] = Image("images/img${images.size}.$ext", mediaType, data)
        }

        val bodies = root.all("body")
        val main = bodies.firstOrNull { it.attr("name").isNullOrBlank() } ?: bodies.firstOrNull()
        val notesBodies = bodies.filter { it !== main }
        noteIds = notesBodies.flatMap { body -> collectIds(body) }.toSet()

        val book = EpubBuilder(identifier, title, authors, language)
        book.description = info?.child("annotation")?.let { annotationText(it) }
        info?.child("sequence")?.let {
            book.series = it.attr("name")
            book.seriesIndex = it.attr("number")
        }

        val coverId = info?.child("coverpage")?.all("image")?.firstOrNull()?.let { imageId(it) }
        val coverImage = coverId?.let { images[it] }
        if (coverImage != null) {
            book.addPage(
                "cover.xhtml",
                xhtmlHead(title, language) + """<div class="cover"><img src="${esc(coverImage.href)}" alt="${esc(title)}"/></div>""" + "\n" + XHTML_END,
            )
        }

        val titlePage = StringBuilder(xhtmlHead(title, language))
        authors.takeIf { it.isNotEmpty() }?.let { titlePage.append("""<p class="book-author">${esc(it.joinToString(", "))}</p>""").append('\n') }
        titlePage.append("<h1>${esc(title)}</h1>\n")
        book.series?.takeIf { it.isNotBlank() }?.let {
            val n = book.seriesIndex?.takeIf { s -> s.isNotBlank() }?.let { s -> " · $s" }.orEmpty()
            titlePage.append("""<p class="book-series">${esc(it + n)}</p>""").append('\n')
        }
        info?.child("annotation")?.let { annotation ->
            titlePage.append("""<div class="annotation">""")
            annotation.children.filterIsInstance<El>().forEach { titlePage.append(block(it, 3)) }
            titlePage.append("</div>\n")
        }
        titlePage.append(XHTML_END)
        book.addPage("title.xhtml", titlePage.toString())

        val chapters = mutableListOf<Pair<String, StringBuilder>>()
        if (main != null) {
            val intro = StringBuilder()
            main.children.filterIsInstance<El>().forEach { el ->
                when (el.name) {
                    "section" -> {}
                    "title" -> {}
                    else -> intro.append(block(el, 2))
                }
            }
            if (intro.isNotBlank()) chapters += "intro.xhtml" to intro
            val sections = main.all("section")
            sections.forEach { section -> emitSection(section, 1, chapters, book.toc) }
        }
        val owner = HashMap<String, String>()
        val idPattern = Regex("""\sid="([^"]+)"""")
        chapters.forEach { (href, body) -> idPattern.findAll(body).forEach { owner.putIfAbsent(it.groupValues[1], href) } }
        val linkPattern = Regex("""href="#([^"]+)" data-fb2-target="[^"]*"""")
        chapters.forEach { (href, body) ->
            val html = linkPattern.replace(body) { m ->
                val target = m.groupValues[1]
                val file = owner[EpubBuilder.unescape(target)] ?: owner[target]
                if (file == null || file == href) "href=\"#$target\"" else "href=\"$file#$target\""
            }
            book.addPage(href, xhtmlHead(title, language) + html + XHTML_END)
        }

        if (notesBodies.isNotEmpty()) {
            val notes = StringBuilder()
            notesBodies.forEach { body ->
                body.child("title")?.let { notes.append("<h2>${titleHtml(it)}</h2>\n") }
                body.all("section").forEach { notes.append(noteSection(it)) }
            }
            val heading = notesBodies.firstOrNull()?.child("title")?.text()?.clean()?.takeIf { it.isNotEmpty() } ?: "Примечания"
            book.addPage("notes.xhtml", xhtmlHead(heading, language) + notes + XHTML_END)
            book.toc += TocEntry(heading, "notes.xhtml")
        }

        images.forEach { (id, image) ->
            book.addImage(image.href, image.mediaType, image.data, cover = id == coverId)
        }
        book.write(target)
    }

    private fun emitSection(section: El, depth: Int, chapters: MutableList<Pair<String, StringBuilder>>, toc: MutableList<TocEntry>) {
        val href = "ch${chapters.size + 1}.xhtml"
        val body = StringBuilder()
        chapters += href to body
        val id = section.attr("id")
        val heading = section.child("title")?.text()?.clean().orEmpty()
        val entry = TocEntry(heading.ifBlank { "…" }, href)
        if (heading.isNotBlank()) toc += entry
        val children = section.children.filterIsInstance<El>()
        val splitChildren = depth < 3 && children.count { it.name == "section" } > 0
        body.append("<section")
        if (id != null) body.append(""" id="${esc(id)}"""")
        body.append(">\n")
        children.forEach { el ->
            if (el.name == "section" && splitChildren) return@forEach
            body.append(block(el, depth + 1, if (heading.isNotBlank()) entry.children else toc, href))
        }
        body.append("</section>\n")
        if (splitChildren) {
            children.filter { it.name == "section" }.forEach {
                emitSection(it, depth + 1, chapters, if (heading.isNotBlank()) entry.children else toc)
            }
        }
    }

    private fun block(el: El, level: Int, toc: MutableList<TocEntry>? = null, href: String? = null): String = when (el.name) {
        "title" -> "<h${level.coerceIn(1, 6)}>${titleHtml(el)}</h${level.coerceIn(1, 6)}>\n"
        "p" -> "<p${idAttr(el)}>${inline(el)}</p>\n"
        "empty-line" -> "<p class=\"empty\">&#160;</p>\n"
        "subtitle" -> "<p class=\"subtitle\"${idAttr(el)}>${inline(el)}</p>\n"
        "text-author" -> "<p class=\"text-author\">${inline(el)}</p>\n"
        "date" -> "<p class=\"date\">${inline(el)}</p>\n"
        "epigraph" -> "<blockquote class=\"epigraph\">" + el.children.filterIsInstance<El>().joinToString("") { block(it, level) } + "</blockquote>\n"
        "cite" -> "<blockquote class=\"cite\"${idAttr(el)}>" + el.children.filterIsInstance<El>().joinToString("") { block(it, level) } + "</blockquote>\n"
        "annotation" -> "<div class=\"annotation\">" + el.children.filterIsInstance<El>().joinToString("") { block(it, level) } + "</div>\n"
        "poem" -> "<div class=\"poem\"${idAttr(el)}>" + el.children.filterIsInstance<El>().joinToString("") { block(it, level) } + "</div>\n"
        "stanza" -> "<div class=\"stanza\">" + el.children.filterIsInstance<El>().joinToString("") { child ->
            when (child.name) {
                "v" -> "<p class=\"v\">${inline(child)}</p>\n"
                else -> block(child, level)
            }
        } + "</div>\n"
        "v" -> "<p class=\"v\">${inline(el)}</p>\n"
        "image" -> imageId(el)?.let { images[it] }?.let { "<div class=\"image\"><img src=\"${esc(it.href)}\" alt=\"\"/></div>\n" }.orEmpty()
        "table" -> table(el)
        "section" -> {
            val id = el.attr("id") ?: "s${++sectionCounter}"
            val heading = el.child("title")?.text()?.clean().orEmpty()
            val entry = TocEntry(heading, if (href != null) "$href#$id" else "")
            if (heading.isNotBlank() && toc != null && href != null) toc += entry
            "<section id=\"${esc(id)}\">\n" + el.children.filterIsInstance<El>().joinToString("") {
                block(it, level + 1, if (heading.isNotBlank()) entry.children else toc, href)
            } + "</section>\n"
        }
        else -> if (el.children.isNotEmpty()) "<p>${inline(el)}</p>\n" else ""
    }

    private fun titleHtml(el: El): String {
        val parts = el.children.filterIsInstance<El>().filter { it.name == "p" }.map { inline(it) }
        return if (parts.isNotEmpty()) parts.joinToString("<br/>") else esc(el.text().clean())
    }

    private fun table(el: El): String = buildString {
        append("<table>\n")
        el.all("tr").forEach { tr ->
            append("<tr>")
            tr.children.filterIsInstance<El>().forEach { cell ->
                val tag = if (cell.name == "th") "th" else "td"
                append("<$tag")
                cell.attr("colspan")?.let { append(" colspan=\"${esc(it)}\"") }
                cell.attr("rowspan")?.let { append(" rowspan=\"${esc(it)}\"") }
                append(">").append(inline(cell)).append("</$tag>")
            }
            append("</tr>\n")
        }
        append("</table>\n")
    }

    private fun inline(el: El): String = buildString {
        el.children.forEach { node ->
            if (node is String) {
                append(esc(node))
                return@forEach
            }
            val child = node as El
            when (child.name) {
                "strong" -> append("<strong>").append(inline(child)).append("</strong>")
                "emphasis" -> append("<em>").append(inline(child)).append("</em>")
                "strikethrough" -> append("<del>").append(inline(child)).append("</del>")
                "sub" -> append("<sub>").append(inline(child)).append("</sub>")
                "sup" -> append("<sup>").append(inline(child)).append("</sup>")
                "code" -> append("<code>").append(inline(child)).append("</code>")
                "style" -> append("<span>").append(inline(child)).append("</span>")
                "image" -> imageId(child)?.let { images[it] }?.let { append("<img class=\"inline\" src=\"${esc(it.href)}\" alt=\"\"/>") }
                "a" -> append(link(child))
                else -> append(inline(child))
            }
        }
    }

    private fun link(a: El): String {
        val href = a.attr("href").orEmpty()
        val text = inline(a)
        if (href.startsWith("#")) {
            val id = href.substring(1)
            val isNote = a.attr("type") == "note" || id in noteIds
            return if (isNote) {
                val label = if (a.children.any { it is El && it.name == "sup" }) text else "<sup>$text</sup>"
                """<a class="noteref" epub:type="noteref" href="notes.xhtml#${esc(id)}">$label</a>"""
            } else {
                """<a href="#${esc(id)}" data-fb2-target="${esc(id)}">$text</a>"""
            }
        }
        if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:")) {
            return """<a href="${esc(href)}">$text</a>"""
        }
        return text
    }

    private fun noteSection(section: El): String = buildString {
        val id = section.attr("id")
        append("<section class=\"note\"")
        if (id != null) append(" id=\"${esc(id)}\"")
        append(">\n")
        section.children.filterIsInstance<El>().forEach { child ->
            when (child.name) {
                "title" -> append("<p class=\"note-title\">${titleHtml(child)}</p>\n")
                "section" -> append(noteSection(child))
                else -> append(block(child, 4))
            }
        }
        append("</section>\n")
    }

    private fun collectIds(el: El): List<String> =
        listOfNotNull(el.attr("id")) + el.children.filterIsInstance<El>().flatMap { collectIds(it) }

    private fun imageId(el: El): String? = el.attr("href")?.removePrefix("#")?.takeIf { it.isNotBlank() }

    private fun idAttr(el: El): String = el.attr("id")?.let { " id=\"${esc(it)}\"" }.orEmpty()

    private fun annotationText(el: El): String =
        el.children.filterIsInstance<El>().joinToString("\n") { it.text().clean() }.trim()

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private fun parse(input: InputStream): El {
        val parser = Xml.newPullParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val stack = ArrayDeque<El>()
        var root: El? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val attrs = HashMap<String, String>()
                    for (i in 0 until parser.attributeCount) attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                    val el = El(parser.name.substringAfter(':'), attrs)
                    stack.lastOrNull()?.children?.add(el)
                    if (root == null) root = el
                    stack.addLast(el)
                }
                XmlPullParser.END_TAG -> stack.removeLastOrNull()
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF, XmlPullParser.CDSECT -> {
                    val text = parser.text
                    val parent = stack.lastOrNull()
                    if (text != null && parent != null && parent.name !in containerTags) parent.children.add(text)
                }
            }
            event = runCatching { parser.nextToken() }.getOrElse { XmlPullParser.END_DOCUMENT }
        }
        return root ?: throw IllegalArgumentException("Пустой файл FB2")
    }

    private val containerTags = setOf(
        "FictionBook", "description", "title-info", "document-info", "publish-info", "body", "section",
        "poem", "stanza", "epigraph", "cite", "annotation", "table", "tr", "author", "coverpage",
    )
}
