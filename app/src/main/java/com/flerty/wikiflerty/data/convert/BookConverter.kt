package com.flerty.wikiflerty.data.convert

import java.io.File
import java.util.zip.ZipFile

object BookConverter {
    enum class Kind(val label: String) {
        Epub("EPUB"), Fb2("FB2"), Fb2Zip("FB2"), Txt("текстовый файл"), Html("HTML"), Md("Markdown"),
        Docx("DOCX"), Odt("ODT"), Rtf("RTF"), Mobi("MOBI/AZW3"), Cbz("CBZ"), Cbr("CBR"), Pdf("PDF"), Unknown("файл")
    }

    class Unwrapped(val name: String?, val fbd: ByteArray?)

    private val bookExt = setOf("epub", "fb2", "mobi", "azw3", "azw", "prc", "pdf", "docx", "odt", "rtf", "txt", "html", "htm", "md", "cbz", "cbr", "zip")
    private val sideExt = setOf("fbd", "nfo", "url", "sfv", "md5", "diz")

    private fun extOf(name: String) = name.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun unwrap(source: File, name: String?): Unwrapped {
        var current = name
        var fbd: ByteArray? = null
        repeat(3) {
            val head = ByteArray(2)
            if (source.inputStream().use { it.read(head) } < 2 || head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) return Unwrapped(current, fbd)
            val inner = File(source.path + ".in")
            val step = runCatching {
                ZipFile(source).use { zip ->
                    val files = zip.entries().asSequence().filter { e ->
                        val n = e.name.substringAfterLast('/')
                        !e.isDirectory && !e.name.startsWith("__MACOSX/") && !n.startsWith(".") && n.isNotEmpty()
                    }.toList()
                    val books = files.filter { extOf(it.name) in bookExt }
                    if (books.size != 1) return@use null
                    val rest = files.filter { it !== books[0] }
                    val images = rest.count { ComicConverter.isImageName(it.name) }
                    if (images > 1 || rest.any { extOf(it.name) !in sideExt && !ComicConverter.isImageName(it.name) }) return@use null
                    val side = rest.firstOrNull { extOf(it.name) == "fbd" }?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
                    zip.getInputStream(books[0]).use { i -> inner.outputStream().use { i.copyTo(it) } }
                    books[0].name.substringAfterLast('/') to side
                }
            }.getOrNull()
            if (step == null) {
                inner.delete()
                return Unwrapped(current, fbd)
            }
            if (!source.delete() || !inner.renameTo(source)) {
                inner.copyTo(source, overwrite = true)
                inner.delete()
            }
            current = step.first
            if (step.second != null) fbd = step.second
        }
        return Unwrapped(current, fbd)
    }

    class Hint(val title: String?, val authors: List<String>, val language: String?, val meta: com.flerty.wikiflerty.data.FileMeta?)

    fun hint(fbd: ByteArray?): Hint? {
        if (fbd == null || fbd.isEmpty()) return null
        return runCatching {
            val c = Fb2Converter("hint")
            c.describe(fbd.inputStream())
            val head = c.head ?: return null
            val base = c.meta ?: com.flerty.wikiflerty.data.FileMeta()
            Hint(head.title, head.authors, head.language, com.flerty.wikiflerty.data.FileMeta(head.annotation.orEmpty(), base.facts, base.series))
        }.getOrNull()
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

    fun toEpub(source: File, kind: Kind, target: File, fallbackTitle: String, identifier: String, name: String? = null, hint: Hint? = null): com.flerty.wikiflerty.data.FileMeta? {
        val ext = name?.lowercase()?.substringAfterLast('.', "").orEmpty()
        val own = convert(source, kind, target, fallbackTitle, identifier, ext, hint)
        val extra = hint?.meta ?: return own
        return (own ?: com.flerty.wikiflerty.data.FileMeta()).over(extra)
    }

    private fun apply(doc: RichDoc, hint: Hint?): RichDoc {
        if (hint == null) return doc
        hint.title?.let { doc.title = it }
        if (hint.authors.isNotEmpty()) doc.authors = hint.authors
        if (doc.language.isNullOrBlank()) hint.language?.let { doc.language = it }
        return doc
    }

    private fun convert(source: File, kind: Kind, target: File, fallbackTitle: String, identifier: String, ext: String, hint: Hint?): com.flerty.wikiflerty.data.FileMeta? {
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
                finish(apply(doc, hint), target, identifier)
            }
            Kind.Md -> finish(apply(MarkdownConverter.read(source, fallbackTitle), hint), target, identifier)
            Kind.Docx -> finish(apply(DocxConverter.read(source, fallbackTitle), hint), target, identifier)
            Kind.Odt -> finish(apply(OdtConverter.read(source, fallbackTitle), hint), target, identifier)
            Kind.Rtf -> finish(apply(RtfConverter.read(source, fallbackTitle), hint), target, identifier)
            Kind.Mobi -> finish(apply(MobiConverter.read(source, fallbackTitle), hint), target, identifier)
            Kind.Cbz -> comic(ComicConverter.convertZip(source, target, fallbackTitle, identifier, hint))
            Kind.Cbr -> comic(ComicConverter.convertRar(source, target, fallbackTitle, identifier, hint))
            else -> throw IllegalArgumentException("unsupported")
        }
    }

    private fun finish(doc: RichDoc, target: File, identifier: String): com.flerty.wikiflerty.data.FileMeta? {
        if (!doc.hasText() && doc.imageCount() == 0) throw IllegalArgumentException("В файле не нашлось текста")
        doc.write(target, identifier)
        return doc.description?.takeIf { it.isNotBlank() }?.let { com.flerty.wikiflerty.data.FileMeta(description = it) }
    }

    private fun comic(meta: ComicConverter.FileMetaLite): com.flerty.wikiflerty.data.FileMeta? {
        val series = meta.series?.let { s -> listOf(com.flerty.wikiflerty.data.BookSeries(s, meta.number?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 })) }.orEmpty()
        return com.flerty.wikiflerty.data.FileMeta(description = meta.summary.orEmpty(), series = series)
    }
}
