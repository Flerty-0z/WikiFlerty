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

    private class Info(var title: String? = null, var series: String? = null, var number: String? = null, val authors: MutableList<String> = mutableListOf(), var summary: String? = null, var language: String? = null)

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
                    "languageiso" -> out.language = v
                }
            }
        }
        return out
    }

    private fun Info.merge(hint: BookConverter.Hint?): Info {
        if (hint == null) return this
        hint.title?.let { title = it }
        if (hint.authors.isNotEmpty()) {
            authors.clear()
            authors += hint.authors
        }
        hint.meta?.description?.takeIf { it.isNotBlank() }?.let { summary = it }
        language = hint.language ?: language
        return this
    }

    fun convertZip(source: File, target: File, fallbackTitle: String, identifier: String, hint: BookConverter.Hint? = null): FileMetaLite = ZipFile(source).use { zip ->
        val entries = zip.entries().asSequence().filter { !it.isDirectory && isImageName(it.name) }.sortedBy { naturalKey(it.name) }.toList()
        if (entries.isEmpty()) throw IllegalArgumentException("no images")
        val meta = info(zip.entries().asSequence().firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", true) }?.let { e -> zip.getInputStream(e).use { it.readBytes() } })
        write(target, entries.map { e -> Page(e.name, { zip.getInputStream(e) }) }, meta.merge(hint), fallbackTitle, identifier)
    }

    fun convertRar(source: File, target: File, fallbackTitle: String, identifier: String, hint: BookConverter.Hint? = null): FileMetaLite {
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
            write(target, headers.map { h -> Page(h.fileName.replace('\\', '/'), { rar.getInputStream(h) }) }, meta.merge(hint), fallbackTitle, identifier)
        }
    }

    class FileMetaLite(val title: String, val authors: List<String>, val series: String?, val number: String?, val summary: String?)

    private const val CHUNK = 20

    fun sizeOf(b: ByteArray): Pair<Int, Int>? {
        fun u8(i: Int) = b[i].toInt() and 0xFF
        fun be16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        fun be32(i: Int) = (u8(i) shl 24) or (u8(i + 1) shl 16) or (u8(i + 2) shl 8) or u8(i + 3)
        return runCatching {
            when {
                b.size > 24 && u8(0) == 0x89 && u8(1) == 0x50 -> be32(16) to be32(20)
                b.size > 10 && u8(0) == 0x47 && u8(1) == 0x49 -> (u8(6) or (u8(7) shl 8)) to (u8(8) or (u8(9) shl 8))
                b.size > 4 && u8(0) == 0xFF && u8(1) == 0xD8 -> {
                    var i = 2
                    var out: Pair<Int, Int>? = null
                    while (i + 9 < b.size) {
                        if (u8(i) != 0xFF) { i++; continue }
                        val m = u8(i + 1)
                        if (m == 0xFF) { i++; continue }
                        if (m == 0xD8 || m == 0x01 || m in 0xD0..0xD7) { i += 2; continue }
                        val len = be16(i + 2)
                        if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                            out = be16(i + 7) to be16(i + 5)
                            break
                        }
                        if (m == 0xDA || len < 2) break
                        i += 2 + len
                    }
                    out
                }
                else -> null
            }
        }.getOrNull()?.takeIf { it.first in 1..30000 && it.second in 1..30000 }
    }

    private fun tidy(name: String): String =
        name.replace('_', ' ').replace(Regex("(?i)\\s+(cbz|cbr|zip|rar)$"), "").replace(Regex("\\s+"), " ").trim().ifBlank { name }

    private fun guessTitle(names: List<String>): String? {
        val base = names.map { it.substringAfterLast('/').substringBeforeLast('.') }
        if (base.size < 2) return null
        var prefix = base.first()
        base.forEach { n -> while (!n.startsWith(prefix)) prefix = prefix.dropLast(1) }
        val t = prefix.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").replace('_', ' ')
            .replace(Regex("[\\s\\d.#-]+$"), "").replace(Regex("\\s+"), " ").trim()
        if (t.count { it.isLetter() } < 4) return null
        val number = Regex("(\\d+)\\s*(\\([^)]*\\)\\s*)*\\d*$").find(prefix.replace('_', ' ').trim())?.groupValues?.get(1)?.trimStart('0')?.takeIf { it.isNotEmpty() && prefix.substringAfter(t, "").contains(Regex("\\d")) }
        return if (number != null && !t.endsWith(number)) "$t #$number" else t
    }

    private fun write(target: File, pages: List<Page>, meta: Info, fallbackTitle: String, identifier: String): FileMetaLite {
        val title = meta.title ?: meta.series?.let { s -> meta.number?.let { "$s #$it" } ?: s } ?: guessTitle(pages.map { it.name }) ?: tidy(fallbackTitle)
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
            text("OEBPS/style.css", "html, body { margin: 0; padding: 0; }\ndiv.page { text-align: center; margin: 0 0 4px; padding: 0; text-indent: 0; break-inside: avoid; page-break-inside: avoid; -webkit-column-break-inside: avoid; break-after: column; -webkit-column-break-after: always; }\ndiv.page img { display: block; margin: 0 auto; max-width: 100%; max-height: 95vh; width: auto; height: auto; object-fit: contain; }\n")
            class Shot(val href: String, val mime: String, val size: Pair<Int, Int>?)
            val kinds = ArrayList<Shot>()
            pages.forEachIndexed { i, p ->
                val ext = p.name.substringAfterLast('.', "jpg").lowercase().let { if (it == "jpeg") "jpg" else it }
                val href = "img/p${i + 1}.$ext"
                val bytes = p.open().use { it.readBytes() }
                zip.putNextEntry(ZipEntry("OEBPS/$href").apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                })
                zip.write(bytes)
                zip.closeEntry()
                val mime = when (ext) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"
                }
                kinds += Shot(href, mime, sizeOf(bytes))
            }
            val chunks = kinds.indices.chunked(CHUNK)
            chunks.forEachIndexed { c, part ->
                val body = StringBuilder()
                part.forEach { i ->
                    val shot = kinds[i]
                    val dims = shot.size?.let { (w, h) -> " width=\"$w\" height=\"$h\"" }.orEmpty()
                    val lazy = if (i - part.first() >= 2) " loading=\"lazy\"" else ""
                    body.append("<div class=\"page\" id=\"page${i + 1}\"><img src=\"${shot.href}\"$dims$lazy alt=\"\"/></div>\n")
                }
                text("OEBPS/p${c + 1}.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><title>${part.first() + 1}–${part.last() + 1}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
$body</body></html>
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
            opf.append("<dc:language>${esc(meta.language?.takeIf { it.isNotBlank() } ?: "und")}</dc:language>\n")
            meta.summary?.let { opf.append("<dc:description>${esc(it)}</dc:description>\n") }
            opf.append("""<meta property="dcterms:modified">2020-01-01T00:00:00Z</meta>
<meta name="cover" content="img1"/>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="css" href="style.css" media-type="text/css"/>
""")
            kinds.forEachIndexed { i, shot ->
                opf.append("""<item id="img${i + 1}" href="${shot.href}" media-type="${shot.mime}"${if (i == 0) " properties=\"cover-image\"" else ""}/>""").append('\n')
            }
            chunks.indices.forEach { c ->
                opf.append("""<item id="p${c + 1}" href="p${c + 1}.xhtml" media-type="application/xhtml+xml"/>""").append('\n')
            }
            opf.append("</manifest>\n<spine>\n")
            chunks.indices.forEach { c -> opf.append("""<itemref idref="p${c + 1}"/>""").append('\n') }
            opf.append("</spine>\n</package>\n")
            text("OEBPS/content.opf", opf.toString())
        }
        return FileMetaLite(title, meta.authors, meta.series, meta.number, meta.summary)
    }
}
