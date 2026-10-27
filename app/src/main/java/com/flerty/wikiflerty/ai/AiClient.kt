package com.flerty.wikiflerty.ai

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** An AI company the app can talk to with the user's own API key. */
enum class AiProvider(
    val id: String,
    val title: String,
    val company: String,
    /** Where to create an API key. */
    val keyUrl: String,
    /** Used until the real list is loaded with the key; the first one is the recommended default. */
    val fallbackModels: List<String>,
) {
    Claude(
        "claude", "Claude", "Anthropic",
        "https://console.anthropic.com/settings/keys",
        listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5", "claude-fable-5-1"),
    ),
    ChatGpt(
        "openai", "ChatGPT", "OpenAI",
        "https://platform.openai.com/api-keys",
        listOf("gpt-6.1-sol", "gpt-6-luna", "gpt-5.5"),
    ),
    Gemini(
        "gemini", "Gemini", "Google",
        "https://aistudio.google.com/apikey",
        listOf("gemini-3-flash-preview", "gemini-3.1-pro-preview", "gemini-3.1-flash-lite"),
    ),
    DeepSeek(
        "deepseek", "DeepSeek", "DeepSeek",
        "https://platform.deepseek.com/api_keys",
        listOf("deepseek-v4-flash", "deepseek-v4-pro"),
    ),
    /** One key for models of many companies (Claude, GPT, Gemini, DeepSeek…), sent from OpenRouter's servers. */
    OpenRouter(
        "openrouter", "OpenRouter", "OpenRouter",
        "https://openrouter.ai/settings/keys",
        listOf("openrouter/auto"),
    ),
    ;

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

data class AiModel(val id: String, val name: String)

data class ChatMessage(val fromUser: Boolean, val text: String)

open class AiException(message: String) : Exception(message)

/** The model is overloaded right now; trying again a bit later usually works. */
class AiBusyException : AiException(
    "Модель сейчас перегружена — так бывает у новых и популярных моделей. Повтори чуть позже или выбери другую модель.",
)

/**
 * Talks to Claude, ChatGPT, Gemini and DeepSeek over HTTPS.
 * Answers are streamed piece by piece, so text appears while the model is still writing.
 */
object AiClient {

    private const val ANTHROPIC_VERSION = "2023-06-01"

    /** Loads the models available to this key, newest/most useful first. */
    suspend fun listModels(provider: AiProvider, key: String): List<AiModel> = withContext(Dispatchers.IO) {
        when (provider) {
            AiProvider.Claude -> {
                val json = getJson("https://api.anthropic.com/v1/models?limit=100", anthropicHeaders(key))
                json.optJSONArray("data").objects().map {
                    AiModel(it.getString("id"), it.optString("display_name").ifBlank { it.getString("id") })
                }
            }
            AiProvider.ChatGpt -> {
                val json = getJson("https://api.openai.com/v1/models", bearer(key))
                json.optJSONArray("data").objects()
                    .sortedByDescending { it.optLong("created") }
                    .map { it.getString("id") }
                    .filter { isOpenAiChatModel(it) }
                    .map { AiModel(it, it) }
            }
            AiProvider.Gemini -> {
                val json = getJson(
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=${enc(key)}",
                    emptyMap(),
                )
                json.optJSONArray("models").objects()
                    .filter { m ->
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        val name = m.getString("name")
                        (0 until (methods?.length() ?: 0)).any { methods!!.optString(it) == "generateContent" } &&
                            name.contains("gemini") &&
                            listOf("embedding", "image", "tts", "live", "audio", "computer", "robotics")
                                .none { name.contains(it) }
                    }
                    .map {
                        val id = it.getString("name").removePrefix("models/")
                        AiModel(id, it.optString("displayName").ifBlank { id })
                    }
                    .reversed()
            }
            AiProvider.DeepSeek -> {
                val json = getJson("https://api.deepseek.com/models", bearer(key))
                json.optJSONArray("data").objects().map { AiModel(it.getString("id"), it.getString("id")) }
            }
            AiProvider.OpenRouter -> {
                // The key is checked separately, then the list of text models is loaded.
                getJson("https://openrouter.ai/api/v1/key", bearer(key))
                val json = getJson("https://openrouter.ai/api/v1/models", bearer(key))
                json.optJSONArray("data").objects()
                    .filter { m ->
                        val outputs = m.optJSONObject("architecture")?.optJSONArray("output_modalities")
                        outputs == null || (0 until outputs.length()).any { outputs.optString(it) == "text" }
                    }
                    .sortedByDescending { it.optLong("created") }
                    .map { AiModel(it.getString("id"), it.optString("name").ifBlank { it.getString("id") }) }
            }
        }.ifEmpty { throw AiException("Список моделей пуст") }
    }

    private fun isOpenAiChatModel(id: String): Boolean {
        val chatLike = id.startsWith("gpt-") || id.startsWith("chatgpt") || id.startsWith("chat-") ||
            Regex("^o\\d").containsMatchIn(id)
        val excluded = listOf(
            "embedding", "tts", "whisper", "dall-e", "image", "audio", "realtime", "transcribe",
            "moderation", "search", "instruct", "codex", "computer",
        )
        return chatLike && excluded.none { id.contains(it) }
    }

    /** Streams an answer. Each emitted string is the next piece of text. */
    fun streamChat(
        provider: AiProvider,
        key: String,
        model: String,
        system: String,
        messages: List<ChatMessage>,
    ): Flow<String> = flow {
        val connection: HttpURLConnection
        val parse: (JSONObject) -> String?
        when (provider) {
            AiProvider.Claude -> {
                val body = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 2048)
                    put("system", system)
                    put("stream", true)
                    put("messages", JSONArray().apply {
                        messages.forEach { m ->
                            put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
                        }
                    })
                }
                connection = post("https://api.anthropic.com/v1/messages", anthropicHeaders(key), body)
                parse = { event ->
                    if (event.optString("type") == "content_block_delta") {
                        event.optJSONObject("delta")?.optString("text")
                    } else if (event.optString("type") == "error") {
                        if (event.optJSONObject("error")?.optString("type") == "overloaded_error") throw AiBusyException()
                        throw AiException(event.optJSONObject("error")?.optString("message") ?: "Ошибка Claude")
                    } else {
                        null
                    }
                }
            }
            AiProvider.ChatGpt, AiProvider.DeepSeek, AiProvider.OpenRouter -> {
                val url = when (provider) {
                    AiProvider.ChatGpt -> "https://api.openai.com/v1/chat/completions"
                    AiProvider.OpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
                    else -> "https://api.deepseek.com/chat/completions"
                }
                val body = JSONObject().apply {
                    put("model", model)
                    put("stream", true)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", system))
                        messages.forEach { m ->
                            put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
                        }
                    })
                }
                val headers = if (provider == AiProvider.OpenRouter) bearer(key) + ("X-Title" to "WikiFlerty") else bearer(key)
                connection = post(url, headers, body)
                parse = { event ->
                    event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        ?.let { if (it.isNull("content")) null else it.optString("content") }
                }
            }
            AiProvider.Gemini -> {
                val body = JSONObject().apply {
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    put("contents", JSONArray().apply {
                        messages.forEach { m ->
                            put(
                                JSONObject()
                                    .put("role", if (m.fromUser) "user" else "model")
                                    .put("parts", JSONArray().put(JSONObject().put("text", m.text))),
                            )
                        }
                    })
                }
                connection = post(
                    "https://generativelanguage.googleapis.com/v1beta/models/${enc(model)}:streamGenerateContent?alt=sse&key=${enc(key)}",
                    emptyMap(),
                    body,
                )
                parse = { event ->
                    event.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts").objects().joinToString("") { it.optString("text") }
                        .ifEmpty { null }
                }
            }
        }

        try {
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    val piece = parse(event)
                    if (!piece.isNullOrEmpty()) emit(piece)
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------ HTTP

    private fun anthropicHeaders(key: String) = mapOf("x-api-key" to key, "anthropic-version" to ANTHROPIC_VERSION)

    private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            checkStatus(connection)
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        checkStatus(connection)
        return connection
    }

    /** Turns an HTTP error into a short, readable message. */
    private fun checkStatus(connection: HttpURLConnection) {
        val code = try {
            connection.responseCode
        } catch (e: java.io.IOException) {
            throw AiException("Нет связи с сервером. Проверь интернет.")
        }
        if (code in 200..299) return
        val raw = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val detail = runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: raw.take(200)
        connection.disconnect()
        if (Regex("(?i)location is not supported|unsupported_country|not available in your (country|region)|country, region, or territory not supported|request not allowed from your region").containsMatchIn(detail)) {
            throw AiException(
                "Эта нейросеть не работает в твоей стране. Включи VPN или выбери другую: DeepSeek или OpenRouter.",
            )
        }
        if (code == 503 || code == 529 || (code in 500..599 && Regex("(?i)overload|high demand|unavailable").containsMatchIn(detail))) {
            throw AiBusyException()
        }
        val hint = when (code) {
            400 -> "Запрос отклонён"
            401, 403 -> "Ключ не подошёл"
            402 -> "Закончились деньги на балансе"
            404 -> "Модель не найдена"
            429 -> "Слишком много запросов или исчерпан лимит"
            in 500..599 -> "Сервер нейросети сейчас недоступен"
            else -> "Ошибка $code"
        }
        throw AiException(if (detail.isNotBlank()) "$hint: $detail" else hint)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
