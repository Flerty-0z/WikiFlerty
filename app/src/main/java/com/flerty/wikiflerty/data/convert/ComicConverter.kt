package com.flerty.wikiflerty.data.convert

import com.flerty.wikiflerty.data.convert.EpubBuilder.Companion.esc
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ComicConverter {
    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        if (n.startsWith("__macosx/") || n.substringAfterLast('/').startsWith(".")) return false
        return n.substringAfterLast('.', "") in imageExt
    }

    fun naturalKey(name: String): String = Regex("\\d+").replace(name.lowercase()) { it.value.trimStart('0').padStart(12, '0') }

    private class Page(val name: String, val open: () -> InputStream)

    private class Info(var title: String? = null, var series: String? = null, var number: String? = null, val authors: MutableList<String> = mutableListOf(), var summary: String? = null)

    private fun info(xml: ByteArray?): Info {
        val out = Info()
        val root = xml?.let { Xml.parse(it) } ?: return out
        with(Xml) {
            root.kids().forEach { k ->
                val v = k.text().trim()
                if (v.isEmpty()) return@forEach
                when (k.local().lowercase()) {
                    "title" -> out.title = v
                    "series" -> out.series = v
                    "number" -> out.number = v
                    "writer", "penciller" -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { if (it !in out.authors) out.authors += it }
                    "summary" -> out.summary = v
                }
            }
        }
        return out
    }

    fun convertZip(source: File, target: File, fallbackTitle: String, identifier: String): FileMetaLite = ZipFile(source).use { zip ->
        val entries = zip.entries().asSequence().filter { !it.isDirectory && isImageName(it.name) }.sortedBy { naturalKey(it.name) }.toList()
        if (entries.isEmpty()) throw IllegalArgumentException("no images")
        val meta = info(zip.entries().asSequence().firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", true) }?.let { e -> zip.getInputStream(e).use { it.readBytes() } })
        write(target, entries.map { e -> Page(e.name, { zip.getInputStream(e) }) }, meta, fallbackTitle, identifier)
    }

    fun convertRar(source: File, target: File, fallbackTitle: String, identifier: String): FileMetaLite {
        val archive = try {
            com.github.junrar.Archive(source)
        } catch (e: com.github.junrar.exception.UnsupportedRarV5Exception) {
            throw IllegalArgumentException("Архивы RAR5 пока не поддерживаются. Перепакуй комикс в CBZ (ZIP)")
        }
        return archive.use { rar ->
            val headers = rar.fileHeaders.filter { !it.isDirectory && isImageName(it.fileName.replace('\\', '/')) }.sortedBy { naturalKey(it.fileName.replace('\\', '/')) }
            if (headers.isEmpty()) throw IllegalArgumentException("no images")
            val infoHeader = rar.fileHeaders.firstOrNull { it.fileName.replace('\\', '/').substringAfterLast('/').equals("ComicInfo.xml", true) }
            val meta = info(infoHeader?.let { h -> rar.getInputStream(h).use { it.readBytes() } })
            write(target, headers.map { h -> Page(h.fileName.replace('\\', '/'), { rar.getInputStream(h) }) }, meta, fallbackTitle, identifier)
        }
    }

    class FileMetaLite(val title: String, val authors: List<String>, val series: String?, val number: String?, val summary: String?)

    private fun write(target: File, pages: List<Page>, meta: Info, fallbackTitle: String, identifier: String): FileMetaLite {
        val title = meta.title ?: meta.series?.let { s -> meta.number?.let { "$s #$it" } ?: s } ?: fallbackTitle
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            })
            zip.write(mimetype)
            zip.closeEntry()
            fun text(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            text("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>
""")
            text("OEBPS/style.css", "html, body { margin: 0; padding: 0; }\ndiv.page { text-align: center; margin: 0; padding: 0; text-indent: 0; page-break-after: always; break-after: page; }\ndiv.page img { display: inline-block; max-width: 100%; max-height: 95vh; width: auto; height: auto; object-fit: contain; }\n")
            val kinds = ArrayList<Pair<String, String>>()
            pages.forEachIndexed { i, p ->
                val ext = p.name.substringAfterLast('.', "jpg").lowercase().let { if (it == "jpeg") "jpg" else it }
                val href = "img/p${i + 1}.$ext"
                zip.putNextEntry(ZipEntry("OEBPS/$href").apply { method = ZipEntry.DEFLATED })
                p.open().use { it.copyTo(zip) }
                zip.closeEntry()
                val mime = when (ext) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"
                }
                kinds += href to mime
                text("OEBPS/p${i + 1}.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><title>${i + 1}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body><div class="page"><img src="$href" alt=""/></div></body></html>
""")
            }
            text("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><meta charset="UTF-8"/><title>${esc(title)}</title></head>
<body><nav epub:type="toc" id="toc"><ol><li><a href="p1.xhtml">${esc(title)}</a></li></ol></nav></body></html>
""")
            val opf = StringBuilder()
            opf.append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">urn:wikiflerty:${esc(identifier)}</dc:identifier>
<dc:title>${esc(title)}</dc:title>
""")
            meta.authors.forEach { opf.append("<dc:creator>${esc(it)}</dc:creator>\n") }
            opf.append("<dc:language>und</dc:language>\n")
            meta.summary?.let { opf.append("<dc:description>${esc(it)}</dc:description>\n") }
            opf.append("""<meta property="dcterms:modified">2020-01-01T00:00:00Z</meta>
<meta name="cover" content="img1"/>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="css" href="style.css" media-type="text/css"/>
""")
            kinds.forEachIndexed { i, (href, mime) ->
                opf.append("""<item id="img${i + 1}" href="$href" media-type="$mime"${if (i == 0) " properties=\"cover-image\"" else ""}/>""").append('\n')
                opf.append("""<item id="p${i + 1}" href="p${i + 1}.xhtml" media-type="application/xhtml+xml"/>""").append('\n')
            }
            opf.append("</manifest>\n<spine>\n")
            kinds.indices.forEach { i -> opf.append("""<itemref idref="p${i + 1}"/>""").append('\n') }
            opf.append("</spine>\n</package>\n")
            text("OEBPS/content.opf", opf.toString())
        }
        return FileMetaLite(title, meta.authors, meta.series, meta.number, meta.summary)
    }
}
