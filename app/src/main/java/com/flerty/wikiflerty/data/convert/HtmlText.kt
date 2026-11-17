package com.flerty.wikiflerty.data.convert

class NotABookException : Exception("Это не файл книги, а страница сайта. Скачай книгу заново — на сайте выбери «Скачать» в формате FB2 или EPUB, а не «Читать онлайн»")

object HtmlText {
    class Page(val title: String?, val paragraphs: List<String>)

    private val junk = Regex("(?is)<!--.*?-->|<(script|style|noscript|head|svg|nav|header|footer|form|aside|button|select|template|iframe)\\b[^>]*>.*?</\\1\\s*>")
    private val breaks = Regex("(?i)<\\s*(?:br\\b[^>]*|/?\\s*(?:p|div|h[1-6]|li|ul|ol|blockquote|section|article|main|tr|td|th|dd|dt|pre|figcaption|table|hr|center)\\b[^>]*)>")
    private val tags = Regex("<[^>]*>")
    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "laquo" to "«", "raquo" to "»", "hellip" to "…",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "shy" to "", "thinsp" to " ", "ensp" to " ", "emsp" to " ", "copy" to "©",
    )

    fun looksLikeHtml(sample: String): Boolean {
        val head = sample.trimStart('\uFEFF', ' ', '\n', '\r', '\t').take(2048).lowercase()
        if (head.contains("<fictionbook")) return false
        return head.startsWith("<!doctype html") || head.startsWith("<html") || (head.contains("<html") && (head.contains("<head") || head.contains("<body")))
    }

    private fun unescape(s: String): String = entity.replace(s) { m ->
        val v = m.groupValues[1]
        when {
            v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
            v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
            else -> named[v.lowercase()] ?: m.value
        }
    }

    private fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = Regex("(?i)charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)").find(head)?.groupValues?.get(1)
        val charset = name?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    private fun clean(s: String) = unescape(tags.replace(s, " ")).replace(Regex("\\s+"), " ").trim()

    fun extract(bytes: ByteArray): Page {
        val html = decode(bytes)
        val title = Regex("(?is)<h1\\b[^>]*>(.*?)</h1>").find(html)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.length in 2..160 }
            ?: Regex("(?is)property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)").find(html)?.groupValues?.get(1)?.let(::clean)
            ?: Regex("(?is)<title\\b[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::clean)
        val bodyStart = Regex("(?i)<body\\b[^>]*>").find(html)?.range?.last?.plus(1) ?: 0
        val bodyEnd = html.lastIndexOf("</body", ignoreCase = true).takeIf { it > bodyStart } ?: html.length
        val body = junk.replace(html.substring(bodyStart, bodyEnd), "\n")
        val blocks = breaks.replace(body, "\n").split('\n').map(::clean).filter { b -> b.any { it.isLetter() } }
        val long = blocks.indices.filter { blocks[it].length >= 60 }
        val kept = if (long.isEmpty()) blocks else blocks.subList((long.first() - 3).coerceAtLeast(0), (long.last() + 3).coerceAtMost(blocks.size))
        val cleanTitle = title?.replace(Regex("(?i)\\s*[-—|–]\\s*(читать|скачать|онлайн|бесплатно).*$"), "")?.trim()?.takeIf { it.isNotBlank() }
        return Page(cleanTitle, kept)
    }
}
