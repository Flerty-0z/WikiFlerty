package com.flerty.wikiflerty.data.convert

import java.io.File

object HtmlConverter {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = Regex("(?i)(?:charset|encoding)\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)").find(head)?.groupValues?.get(1)
        val declared = name?.let { runCatching { charset(it) }.getOrNull() }
        if (declared != null) return String(bytes, declared)
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        return utf8 ?: String(bytes, charset("windows-1251"))
    }

    private fun clean(s: String) = Entities.unescape(s.replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()

    fun titleOf(html: String): String? {
        val og = Regex("(?is)property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)").find(html)?.groupValues?.get(1)?.let(::clean)
        val h1s = Regex("(?is)<h1\\b[^>]*>(.*?)</h1>").findAll(html).take(2).toList()
        val h1 = h1s.singleOrNull()?.groupValues?.get(1)?.let(::clean)?.takeIf { it.length in 2..160 }
        val t = Regex("(?is)<title\\b[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::clean)
        return (og ?: h1 ?: t)?.replace(Regex("(?i)\\s*[-—|–]\\s*(читать|скачать|онлайн|бесплатно|read online).*$"), "")?.trim()?.takeIf { it.isNotBlank() }
    }

    fun read(file: File, fallbackTitle: String, page: Boolean): RichDoc {
        val html = decode(file.readBytes())
        val doc = RichDoc(titleOf(html) ?: fallbackTitle)
        Regex("(?is)<meta[^>]+name=[\"']author[\"'][^>]*content=[\"']([^\"']+)").find(html)?.groupValues?.get(1)?.let { doc.authors = listOf(clean(it)) }
        Regex("(?is)<html[^>]*\\blang=[\"']([a-zA-Z-]+)").find(html)?.groupValues?.get(1)?.let { doc.language = it }
        val styles = Regex("(?is)<style\\b[^>]*>(.*?)</style>").findAll(html).joinToString("\n") { it.groupValues[1] }
        HtmlBlocks.parse(html, doc, HtmlBlocks.css(styles)) { src, _ ->
            if (src.startsWith("data:image", ignoreCase = true) && src.contains(";base64,")) {
                runCatching { base64(src.substringAfter(";base64,")) }.getOrNull()?.let { doc.addImage(it) }
            } else null
        }
        if (page) trimPage(doc)
        return doc
    }

    private fun trimPage(doc: RichDoc) {
        val blocks = doc.blocks
        val long = blocks.indices.filter { i -> (blocks[i] as? Block.Para)?.runs?.plain?.length?.let { it >= 60 } == true }
        if (long.isEmpty()) return
        val from = (long.first() - 3).coerceAtLeast(0)
        val to = (long.last() + 3).coerceAtMost(blocks.size)
        val kept = blocks.subList(from, to).toList()
        blocks.clear()
        blocks += kept
    }
}

object MarkdownConverter {
    private val inline = Regex("(\\*\\*|__)(.+?)\\1|(\\*|_)(?!\\s)(.+?)(?<!\\s)\\3|`([^`]+)`|!?\\[([^\\]]*)]\\([^)]*\\)")

    private fun runs(text: String, line: LineBuilder) {
        var at = 0
        inline.findAll(text).forEach { m ->
            if (m.range.first > at) line.add(text.substring(at, m.range.first))
            when {
                m.groups[2] != null -> line.add(m.groupValues[2], bold = true)
                m.groups[4] != null -> line.add(m.groupValues[4], italic = true)
                m.groups[5] != null -> line.add(m.groupValues[5])
                m.groups[6] != null -> if (!m.value.startsWith("!")) line.add(m.groupValues[6])
            }
            at = m.range.last + 1
        }
        if (at < text.length) line.add(text.substring(at))
    }

    fun read(file: File, fallbackTitle: String): RichDoc {
        val text = HtmlConverter.decode(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        val doc = RichDoc(fallbackTitle)
        var body = text
        val front = Regex("^\\s*---\\s*\n(.*?)\n---\\s*\n", RegexOption.DOT_MATCHES_ALL).find(body)
        if (front != null) {
            front.groupValues[1].lines().forEach { l ->
                val k = l.substringBefore(':').trim().lowercase()
                val v = l.substringAfter(':', "").trim().trim('"', '\'')
                if (v.isNotEmpty()) when (k) {
                    "title" -> doc.title = v
                    "author", "authors" -> doc.authors = v.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    "lang", "language" -> doc.language = v
                    "description" -> doc.description = v
                }
            }
            body = body.substring(front.range.last + 1)
        }
        val para = StringBuilder()
        var quote = false
        fun flush() {
            if (para.isBlank()) {
                para.setLength(0)
                return
            }
            val line = LineBuilder()
            runs(para.toString().trim(), line)
            val r = line.take()
            if (r.isNotEmpty()) doc.blocks += Block.Para(r, quote = quote)
            para.setLength(0)
            quote = false
        }
        var fence = false
        body.lines().forEach { raw ->
            val l = raw.trimEnd()
            if (l.trimStart().startsWith("```")) {
                flush()
                fence = !fence
                return@forEach
            }
            if (fence) {
                if (l.isNotBlank()) doc.blocks += Block.Para(listOf(Run(l)))
                return@forEach
            }
            val t = l.trim()
            val h = Regex("^(#{1,6})\\s+(.*?)\\s*#*$").find(t)
            when {
                t.isEmpty() -> flush()
                h != null -> {
                    flush()
                    val line = LineBuilder()
                    runs(h.groupValues[2], line)
                    val r = line.take()
                    if (r.isNotEmpty()) doc.blocks += Block.Heading(h.groupValues[1].length, r)
                }
                Regex("^([-*_]\\s*){3,}$").matches(t) -> {
                    flush()
                    doc.blocks += Block.Rule
                }
                Regex("^([-*+]|\\d+[.)])\\s+").containsMatchIn(t) -> {
                    flush()
                    val item = t.replaceFirst(Regex("^([-*+]|\\d+[.)])\\s+"), "")
                    val line = LineBuilder()
                    line.add("• ")
                    runs(item, line)
                    doc.blocks += Block.Para(line.take())
                }
                t.startsWith(">") -> {
                    if (!quote) flush()
                    quote = true
                    para.append(t.removePrefix(">").trim()).append(' ')
                }
                else -> {
                    if (quote) flush()
                    para.append(t).append(' ')
                }
            }
        }
        flush()
        if (front == null) (doc.blocks.firstOrNull() as? Block.Heading)?.takeIf { it.level == 1 && doc.blocks.count { b -> b is Block.Heading && b.level == 1 } == 1 }?.let {
            doc.title = it.runs.plain.trim()
        }
        return doc
    }
}

internal fun base64(text: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val map = IntArray(128) { -1 }
    alphabet.forEachIndexed { i, c -> map[c.code] = i }
    map['-'.code] = 62
    map['_'.code] = 63
    val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
    var buf = 0
    var bits = 0
    for (c in text) {
        if (c == '=') break
        val v = if (c.code < 128) map[c.code] else -1
        if (v < 0) continue
        buf = (buf shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.write((buf shr bits) and 0xFF)
        }
    }
    return out.toByteArray()
}
