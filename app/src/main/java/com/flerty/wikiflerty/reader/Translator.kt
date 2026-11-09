package com.flerty.wikiflerty.reader

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class TranslationResult(
    val text: String,
    val source: String,
    val variants: List<Pair<String, List<String>>>,
    val definitions: List<Pair<String, String>>,
) {
    fun card(): String = buildString {
        append(text)
        variants.forEach { (pos, terms) ->
            append("\n\n")
            if (pos.isNotBlank()) append("*").append(pos).append("* ")
            append(terms.joinToString(", "))
        }
        definitions.take(3).forEach { (pos, gloss) ->
            append("\n\n")
            if (pos.isNotBlank()) append("*").append(pos).append("* ")
            append(gloss)
        }
    }.trim()
}

data class TranslateLanguage(val code: String, val title: String)

object Translator {
    val languages = listOf(
        TranslateLanguage("ru", "Русский"),
        TranslateLanguage("en", "Английский"),
        TranslateLanguage("uk", "Украинский"),
        TranslateLanguage("be", "Белорусский"),
        TranslateLanguage("kk", "Казахский"),
        TranslateLanguage("uz", "Узбекский"),
        TranslateLanguage("az", "Азербайджанский"),
        TranslateLanguage("hy", "Армянский"),
        TranslateLanguage("ka", "Грузинский"),
        TranslateLanguage("tr", "Турецкий"),
        TranslateLanguage("de", "Немецкий"),
        TranslateLanguage("fr", "Французский"),
        TranslateLanguage("es", "Испанский"),
        TranslateLanguage("it", "Итальянский"),
        TranslateLanguage("pt", "Португальский"),
        TranslateLanguage("pl", "Польский"),
        TranslateLanguage("cs", "Чешский"),
        TranslateLanguage("ar", "Арабский"),
        TranslateLanguage("zh-CN", "Китайский"),
        TranslateLanguage("ja", "Японский"),
        TranslateLanguage("ko", "Корейский"),
        TranslateLanguage("la", "Латынь"),
    )

    fun title(code: String) = languages.firstOrNull { it.code.equals(code, ignoreCase = true) }?.title ?: code

    suspend fun translate(text: String, target: String): TranslationResult = withContext(Dispatchers.IO) {
        val clean = text.replace(Regex("[ \\t]+"), " ").trim().take(5000)
        val pieces = split(clean)
        val parts = pieces.map { request(it, target, dictionary = pieces.size == 1 && clean.length <= 60) }
        TranslationResult(
            text = parts.joinToString(" ") { it.text }.trim(),
            source = parts.firstOrNull()?.source.orEmpty(),
            variants = parts.flatMap { it.variants },
            definitions = parts.flatMap { it.definitions },
        )
    }

    private fun split(text: String): List<String> {
        if (text.length <= 1500) return listOf(text)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        Regex("[^.!?…\\n]+[.!?…\\n]*").findAll(text).forEach { m ->
            if (current.length + m.value.length > 1500 && current.isNotEmpty()) {
                out += current.toString()
                current.clear()
            }
            current.append(m.value)
            while (current.length > 1500) {
                out += current.substring(0, 1500)
                current.delete(0, 1500)
            }
        }
        if (current.isNotBlank()) out += current.toString()
        return out
    }

    private fun request(text: String, target: String, dictionary: Boolean): TranslationResult {
        val kinds = if (dictionary) "&dt=t&dt=bd&dt=md" else "&dt=t"
        val url = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&dj=1&sl=auto" +
                "&tl=" + URLEncoder.encode(target, "UTF-8") +
                "&hl=" + URLEncoder.encode(target, "UTF-8") + kinds +
                "&q=" + URLEncoder.encode(text, "UTF-8"),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val sentences = json.optJSONArray("sentences")
            val translated = buildString {
                for (i in 0 until (sentences?.length() ?: 0)) append(sentences!!.optJSONObject(i)?.optString("trans").orEmpty())
            }
            val variants = mutableListOf<Pair<String, List<String>>>()
            json.optJSONArray("dict")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val terms = d.optJSONArray("terms") ?: continue
                    val list = (0 until terms.length()).mapNotNull { terms.optString(it).takeIf { t -> t.isNotBlank() } }.take(6)
                    if (list.isNotEmpty()) variants += d.optString("pos") to list
                }
            }
            val definitions = mutableListOf<Pair<String, String>>()
            json.optJSONArray("definitions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val entry = d.optJSONArray("entry") ?: continue
                    entry.optJSONObject(0)?.optString("gloss")?.takeIf { it.isNotBlank() }?.let { definitions += d.optString("pos") to it }
                }
            }
            return TranslationResult(translated.trim(), json.optString("src"), variants, definitions)
        } finally {
            conn.disconnect()
        }
    }
}
