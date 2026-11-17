package com.flerty.wikiflerty.data.convert

import java.io.File
import java.util.zip.ZipFile

object BookConverter {
    enum class Kind(val label: String) {
        Epub("EPUB"), Fb2("FB2"), Fb2Zip("FB2"), Txt("текстовый файл"), Html("HTML"), Md("Markdown"),
        Docx("DOCX"), Odt("ODT"), Rtf("RTF"), Mobi("MOBI/AZW3"), Cbz("CBZ"), Cbr("CBR"), Pdf("PDF"), Unknown("файл")
    }

    fun detect(file: File, name: String? = null): Kind {
        val ext = name?.lowercase()?.substringAfterLast('.', "").orEmpty()
        val head = ByteArray(4096)
        val read = file.inputStream().use { it.read(head) }.coerceAtLeast(0)
        if (read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return runCatching {
                ZipFile(file).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toList()
                    val mimetype = zip.getEntry("mimetype")?.let { e -> zip.getInputStream(e).use { String(it.readBytes(), Charsets.US_ASCII).trim() } }
                    when {
                        names.any { it == "META-INF/container.xml" } && mimetype != "application/vnd.oasis.opendocument.text" -> Kind.Epub
                        names.any { it == "word/document.xml" } -> Kind.Docx
                        mimetype?.startsWith("application/vnd.oasis.opendocument.text") == true || (names.any { it == "content.xml" } && ext == "odt") -> Kind.Odt
                        names.any { it.lowercase().endsWith(".fb2") } -> Kind.Fb2Zip
                        names.count { ComicConverter.isImageName(it) } >= 2 && names.count { ComicConverter.isImageName(it) } * 2 >= names.count { !it.endsWith("/") } -> Kind.Cbz
                        else -> Kind.Unknown
                    }
                }
            }.getOrDefault(Kind.Unknown)
        }
        if (read >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "Rar!") return Kind.Cbr
        if (read >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF") return Kind.Pdf
        if (MobiConverter.isMobi(head, read)) return Kind.Mobi
        val sample = String(head, 0, read, Charsets.ISO_8859_1)
        if (sample.trimStart('﻿', 'ï', '»', '¿', ' ', '\n', '\r', '\t').startsWith("{\\rtf")) return Kind.Rtf
        if (sample.contains("<FictionBook")) return Kind.Fb2
        if (HtmlText.looksLikeHtml(sample) || HtmlText.looksLikeHtml(String(head, 0, read, Charsets.UTF_8))) return Kind.Html
        val binary = (0 until read).count { i -> val b = head[i].toInt() and 0xFF; b == 0 || (b < 0x09) || (b in 0x0E..0x1A) }
        val utf16 = read >= 2 && ((head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) || (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()))
        val text = utf16 || (read > 0 && binary <= read / 100)
        if (!text) return Kind.Unknown
        return if (ext == "md" || ext == "markdown") Kind.Md else Kind.Txt
    }

    fun toEpub(source: File, kind: Kind, target: File, fallbackTitle: String, identifier: String, name: String? = null): com.flerty.wikiflerty.data.FileMeta? {
        val ext = name?.lowercase()?.substringAfterLast('.', "").orEmpty()
        return when (kind) {
            Kind.Fb2 -> source.inputStream().buffered().use { input -> Fb2Converter(identifier).let { c -> c.convert(input, target, fallbackTitle); c.meta } }
            Kind.Fb2Zip -> ZipFile(source).use { zip ->
                val entry = zip.entries().asSequence().first { it.name.lowercase().endsWith(".fb2") }
                zip.getInputStream(entry).buffered().use { input -> Fb2Converter(identifier).let { c -> c.convert(input, target, fallbackTitle); c.meta } }
            }
            Kind.Txt -> {
                TxtConverter(identifier).convert(source.readBytes(), target, fallbackTitle)
                null
            }
            Kind.Html -> {
                val page = ext !in setOf("html", "htm", "xhtml", "xht", "shtml", "mht")
                val doc = HtmlConverter.read(source, fallbackTitle, page)
                if (page && doc.blocks.sumOf { b -> (b as? Block.Para)?.runs?.plain?.length ?: 0 } < 3000) throw NotABookException()
                finish(doc, target, identifier)
            }
            Kind.Md -> finish(MarkdownConverter.read(source, fallbackTitle), target, identifier)
            Kind.Docx -> finish(DocxConverter.read(source, fallbackTitle), target, identifier)
            Kind.Odt -> finish(OdtConverter.read(source, fallbackTitle), target, identifier)
            Kind.Rtf -> finish(RtfConverter.read(source, fallbackTitle), target, identifier)
            Kind.Mobi -> finish(MobiConverter.read(source, fallbackTitle), target, identifier)
            Kind.Cbz -> comic(ComicConverter.convertZip(source, target, fallbackTitle, identifier))
            Kind.Cbr -> comic(ComicConverter.convertRar(source, target, fallbackTitle, identifier))
            else -> throw IllegalArgumentException("unsupported")
        }
    }

    private fun finish(doc: RichDoc, target: File, identifier: String): com.flerty.wikiflerty.data.FileMeta? {
        if (!doc.hasText() && doc.imageCount() == 0) throw IllegalArgumentException("В файле не нашлось текста")
        doc.write(target, identifier)
        return doc.description?.takeIf { it.isNotBlank() }?.let { com.flerty.wikiflerty.data.FileMeta(description = it) }
    }

    private fun comic(meta: ComicConverter.FileMetaLite): com.flerty.wikiflerty.data.FileMeta? {
        val series = meta.series?.let { s -> listOf(com.flerty.wikiflerty.data.BookSeries(s, meta.number?.replace(',', '.')?.toDoubleOrNull())) }.orEmpty()
        return com.flerty.wikiflerty.data.FileMeta(description = meta.summary.orEmpty(), series = series)
    }
}
