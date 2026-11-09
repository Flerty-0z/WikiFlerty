package com.flerty.wikiflerty.reader

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

object Wiktionary {
    private const val API = "https://ru.wiktionary.org/w/api.php"

    fun meanings(word: String): Pair<String, List<String>>? {
        val clean = word.trim()
        if (clean.isEmpty() || clean.length > 40) return null
        val tried = mutableSetOf<String>()
        fun attempt(title: String): Pair<String, List<String>>? {
            if (!tried.add(title)) return null
            val page = parse(title) ?: return null
            if (page.meanings.isNotEmpty()) return title to page.meanings
            val lemma = page.lemma ?: return null
            if (!tried.add(lemma)) return null
            val base = parse(lemma) ?: return null
            return if (base.meanings.isNotEmpty()) lemma to base.meanings else null
        }
        listOf(clean, clean.lowercase()).distinct().forEach { t -> attempt(t)?.let { return it } }
        search(clean.lowercase()).forEach { t -> attempt(t)?.let { return it } }
        return null
    }

    private class Page(val meanings: List<String>, val lemma: String?)

    private fun get(query: String): JSONObject? {
        val conn = (URL("$API?$query&format=json&formatversion=2").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "WikiFlerty/1.0 (Android reader)")
        }
        return try {
            if (conn.responseCode !in 200..299) null else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun search(word: String): List<String> {
        val json = get("action=query&list=search&srnamespace=0&srlimit=5&srsearch=" + URLEncoder.encode(word, "UTF-8")) ?: return emptyList()
        val arr = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        val prefix = word.take(2)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("title") }
            .filter { it.isNotBlank() && it != word && !it.contains(' ') && it.lowercase().startsWith(prefix) }
            .take(2)
    }

    private val languageHeader = Regex("(?m)^=\\s*\\{\\{-[a-z-]+-\\}\\}")
    private val meaningBlock = Regex("(?ms)^=+\\s*Значение\\s*=+\\s*$(.*?)(?=^=|\\z)")
    private val definitionLine = Regex("^#(?![:*#])\\s*(.*)")
    private val firstLink = Regex("\\[\\[([^\\]|#]+)")
    private val formTemplate = Regex("(?i)\\{\\{(?:форма-[^|}]*|словоформа)[^}]*?\\|\\s*([а-яё-]{2,})\\s*(?:\\||\\}\\})")

    private fun parse(title: String): Page? {
        val json = get("action=parse&prop=wikitext&redirects=1&page=" + URLEncoder.encode(title, "UTF-8")) ?: return null
        val text = json.optJSONObject("parse")?.optString("wikitext").orEmpty()
        if (text.isBlank()) return null
        val start = text.indexOf("{{-ru-}}")
        if (start < 0) return null
        val rest = text.substring(start + 8)
        val next = languageHeader.find(rest)?.range?.first ?: rest.length
        val section = rest.substring(0, next)
        val meanings = mutableListOf<String>()
        var lemma: String? = null
        meaningBlock.findAll(section).forEach { m ->
            m.groupValues[1].lines().forEach { line ->
                val raw = definitionLine.find(line.trim())?.groupValues?.get(1) ?: return@forEach
                if (lemma == null && raw.contains("форм", ignoreCase = true)) {
                    lemma = firstLink.find(raw)?.groupValues?.get(1)?.trim()
                }
                val cleaned = clean(raw)
                if (cleaned.length >= 3 && meanings.size < 6) meanings += cleaned
            }
        }
        if (lemma == null) lemma = formTemplate.find(section)?.groupValues?.get(1)
        val found = lemma?.takeIf { it != title }
        val real = if (found != null) meanings.filterNot { it.contains("форм", ignoreCase = true) } else meanings
        return Page(real, found)
    }

    private fun clean(raw: String): String {
        var s = stripTemplates(raw)
        s = s.replace(Regex("<ref[^>]*/>"), "").replace(Regex("(?s)<ref.*?</ref>"), "").replace(Regex("<[^>]+>"), "")
        s = s.replace(Regex("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]"), "$1").replace(Regex("\\[\\[([^\\]]*)\\]\\]"), "$1")
        s = s.replace("'''", "").replace("''", "")
        s = s.replace(Regex("\\s+"), " ").replace(Regex("\\s+([,.;:])"), "$1").trim().trim(';', ',', ' ')
        return s
    }

    private fun stripTemplates(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s.startsWith("{{", i)) {
                var depth = 0
                var j = i
                while (j < s.length) {
                    if (s.startsWith("{{", j)) {
                        depth++
                        j += 2
                        continue
                    }
                    if (s.startsWith("}}", j)) {
                        depth--
                        j += 2
                        if (depth == 0) break
                        continue
                    }
                    j++
                }
                val inner = s.substring(i + 2, (j - 2).coerceAtLeast(i + 2))
                out.append(template(inner))
                i = j
            } else {
                out.append(s[i])
                i++
            }
        }
        return out.toString()
    }

    private fun template(inner: String): String {
        val parts = inner.split('|')
        val name = parts.first().trim()
        val lower = name.lowercase()
        return when {
            lower.startsWith("пример") || lower.startsWith("цитата") || lower == "семантика" -> ""
            lower == "помета" || lower == "помета." -> parts.getOrNull(1)?.trim()?.let { "*" + stripTemplates(it) + "* " }.orEmpty()
            lower == "=" || lower == "то же" -> "то же, что " + parts.drop(1).firstOrNull { !it.contains('=') }?.trim().orEmpty()
            parts.size == 1 && name.endsWith(".") && name.length <= 14 -> "*$name* "
            else -> ""
        }
    }
}

object EnglishWiktionary {
    fun define(word: String, source: String): List<String> {
        val encoded = URLEncoder.encode(word.trim(), "UTF-8").replace("+", "%20")
        val conn = (URL("https://en.wiktionary.org/api/rest_v1/page/definition/$encoded").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "WikiFlerty/1.0 (Android reader)")
        }
        val json = try {
            if (conn.responseCode !in 200..299) return emptyList()
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val key = source.substringBefore('-').lowercase()
        val list = json.optJSONArray(key) ?: json.optJSONArray("en") ?: json.keys().asSequence().firstOrNull()?.let { json.optJSONArray(it) } ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            val pos = entry.optString("partOfSpeech").lowercase()
            val defs = entry.optJSONArray("definitions") ?: continue
            for (j in 0 until defs.length()) {
                val raw = defs.optJSONObject(j)?.optString("definition").orEmpty()
                val text = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()
                if (text.length < 3) continue
                out += if (pos.isNotBlank()) "*$pos* $text" else text
                if (out.size >= 4) return out
            }
        }
        return out
    }
}
