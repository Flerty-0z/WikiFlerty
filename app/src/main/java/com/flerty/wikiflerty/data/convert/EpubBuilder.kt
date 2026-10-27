package com.flerty.wikiflerty.data.convert

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TocEntry(val title: String, val href: String, val children: MutableList<TocEntry> = mutableListOf())

class EpubBuilder(
    private val identifier: String,
    private val title: String,
    private val authors: List<String>,
    private val language: String,
) {
    private class Item(val id: String, val href: String, val mediaType: String, val data: ByteArray, val properties: String?, val linear: Boolean?)

    private val items = mutableListOf<Item>()
    val toc = mutableListOf<TocEntry>()
    var description: String? = null
    var series: String? = null
    var seriesIndex: String? = null

    fun addPage(href: String, xhtml: String, linear: Boolean = true) {
        items += Item("p${items.size}", href, "application/xhtml+xml", xhtml.toByteArray(Charsets.UTF_8), null, linear)
    }

    fun addImage(href: String, mediaType: String, data: ByteArray, cover: Boolean = false) {
        items += Item("i${items.size}", href, mediaType, data, if (cover) "cover-image" else null, null)
    }

    fun write(target: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                    time = FIXED_TIME
                },
            )
            zip.write(mimetype)
            zip.closeEntry()
            entry(zip, "META-INF/container.xml", CONTAINER.toByteArray(Charsets.UTF_8))
            entry(zip, "OEBPS/style.css", CSS.toByteArray(Charsets.UTF_8))
            entry(zip, "OEBPS/nav.xhtml", nav().toByteArray(Charsets.UTF_8))
            entry(zip, "OEBPS/content.opf", opf().toByteArray(Charsets.UTF_8))
            items.forEach { entry(zip, "OEBPS/${it.href}", it.data) }
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
        zip.write(data)
        zip.closeEntry()
    }

    private fun opf(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="${esc(language)}">""").append('\n')
        append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""").append('\n')
        append("""<dc:identifier id="uid">urn:wikiflerty:${esc(identifier)}</dc:identifier>""").append('\n')
        append("<dc:title>${esc(title)}</dc:title>\n")
        authors.forEach { append("<dc:creator>${esc(it)}</dc:creator>\n") }
        append("<dc:language>${esc(language)}</dc:language>\n")
        description?.takeIf { it.isNotBlank() }?.let { append("<dc:description>${esc(it)}</dc:description>\n") }
        series?.takeIf { it.isNotBlank() }?.let {
            append("""<meta property="belongs-to-collection" id="series">${esc(it)}</meta>""").append('\n')
            append("""<meta refines="#series" property="collection-type">series</meta>""").append('\n')
            seriesIndex?.takeIf { s -> s.isNotBlank() }?.let { n ->
                append("""<meta refines="#series" property="group-position">${esc(n)}</meta>""").append('\n')
            }
        }
        append("""<meta property="dcterms:modified">2020-01-01T00:00:00Z</meta>""").append('\n')
        items.firstOrNull { it.properties == "cover-image" }?.let { append("""<meta name="cover" content="${it.id}"/>""").append('\n') }
        append("</metadata>\n<manifest>\n")
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""").append('\n')
        append("""<item id="css" href="style.css" media-type="text/css"/>""").append('\n')
        items.forEach { item ->
            append("""<item id="${item.id}" href="${esc(item.href)}" media-type="${item.mediaType}"""")
            if (item.properties != null) append(""" properties="${item.properties}"""")
            append("/>\n")
        }
        append("</manifest>\n<spine>\n")
        items.filter { it.linear != null }.forEach { item ->
            append("""<itemref idref="${item.id}"""")
            if (item.linear == false) append(""" linear="no"""")
            append("/>\n")
        }
        append("</spine>\n</package>\n")
    }

    private fun nav(): String = buildString {
        append(xhtmlHead("Содержание", language))
        append("""<nav epub:type="toc" id="toc"><h1>Содержание</h1>""").append('\n')
        val entries = toc.ifEmpty { items.firstOrNull { it.linear == true }?.let { mutableListOf(TocEntry(title, it.href)) } ?: mutableListOf() }
        appendList(this, entries)
        append("</nav>\n</body>\n</html>\n")
    }

    private fun appendList(out: StringBuilder, entries: List<TocEntry>) {
        if (entries.isEmpty()) return
        out.append("<ol>\n")
        entries.forEach { e ->
            out.append("""<li><a href="${esc(e.href)}">${esc(e.title.ifBlank { "…" })}</a>""")
            if (e.children.isNotEmpty()) {
                out.append('\n')
                appendList(out, e.children)
            }
            out.append("</li>\n")
        }
        out.append("</ol>\n")
    }

    companion object {
        private const val FIXED_TIME = 315532800000L + 43_200_000L

        fun esc(text: String): String = buildString(text.length) {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> if (c.code < 0x20 && c != '\n' && c != '\t' && c != '\r') Unit else append(c)
                }
            }
        }

        fun unescape(text: String): String =
            text.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

        fun xhtmlHead(title: String, language: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${esc(language)}" lang="${esc(language)}">
<head>
<meta charset="UTF-8"/>
<title>${esc(title)}</title>
<link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
"""

        const val XHTML_END = "</body>\n</html>\n"

        private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles>
</container>
"""

        private const val CSS = """h1, h2, h3, h4, h5, h6 { text-align: center; text-indent: 0; margin: 1.2em 0 0.8em; line-height: 1.3; }
h1 { font-size: 1.5em; }
h2 { font-size: 1.3em; }
h3 { font-size: 1.15em; }
h4, h5, h6 { font-size: 1.05em; }
p { margin: 0; text-indent: 1.5em; }
p.empty { text-indent: 0; margin: 0; }
p.subtitle { text-align: center; text-indent: 0; font-weight: bold; margin: 1em 0 0.6em; }
p.text-author { text-align: right; text-indent: 0; font-style: italic; margin-top: 0.3em; }
p.date { text-align: right; text-indent: 0; font-size: 0.9em; }
blockquote { margin: 1em 0 1em 2em; }
blockquote.epigraph { margin: 1em 0 1.2em 30%; font-size: 0.95em; font-style: italic; }
blockquote.epigraph p { text-indent: 0; }
div.poem { margin: 1em 0 1em 2em; }
div.stanza { margin: 0 0 0.8em; }
p.v { text-indent: 0; }
div.image { text-align: center; margin: 1em 0; text-indent: 0; }
div.image img, img.inline { max-width: 100%; height: auto; }
div.cover { text-align: center; margin: 0; padding: 0; }
div.cover img { max-width: 100%; max-height: 100vh; }
p.book-author { text-align: center; text-indent: 0; font-size: 1.1em; margin: 2em 0 0.5em; }
p.book-series { text-align: center; text-indent: 0; font-style: italic; margin: 0.5em 0 2em; }
section.note { margin: 0 0 1.2em; }
p.note-title { text-indent: 0; font-weight: bold; margin: 0.6em 0 0.2em; }
a.noteref { text-decoration: none; }
table { border-collapse: collapse; margin: 1em auto; }
td, th { border: 1px solid currentColor; padding: 0.2em 0.4em; }
"""
    }
}
