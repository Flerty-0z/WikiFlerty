package com.flerty.wikiflerty.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Base64
import com.flerty.wikiflerty.App
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsPreferencesEditor
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Try

data class NeuralVoice(val id: String, val title: String, val hint: String)

object NeuralVoices {
    const val GEMINI = "neural:gemini"
    const val OPENAI = "neural:openai"

    fun isNeural(engine: String) = engine.startsWith("neural:")

    fun providerId(engine: String) = when (engine) {
        GEMINI -> "gemini"
        OPENAI -> "openai"
        else -> ""
    }

    private fun g(id: String, female: Boolean, hint: String) = NeuralVoice(id, id, (if (female) "Женский" else "Мужской") + " · " + hint)

    val gemini = listOf(
        g("Kore", true, "твёрдый, уверенный"),
        g("Charon", false, "спокойный рассказчик"),
        g("Sulafat", true, "тёплый"),
        g("Iapetus", false, "чёткий"),
        g("Aoede", true, "лёгкий, воздушный"),
        g("Orus", false, "твёрдый"),
        g("Vindemiatrix", true, "мягкий, нежный"),
        g("Sadaltager", false, "знающий, лектор"),
        g("Achernar", true, "тихий, мягкий"),
        g("Algieba", false, "плавный"),
        g("Despina", true, "плавный"),
        g("Rasalgethi", false, "информативный"),
        g("Callirrhoe", true, "непринуждённый"),
        g("Schedar", false, "ровный"),
        g("Gacrux", true, "зрелый"),
        g("Alnilam", false, "уверенный"),
        g("Leda", true, "молодой"),
        g("Puck", false, "бодрый"),
        g("Zephyr", true, "яркий"),
        g("Enceladus", false, "с придыханием"),
        g("Erinome", true, "ясный"),
        g("Umbriel", false, "непринуждённый"),
        g("Autonoe", true, "яркий"),
        g("Algenib", false, "с хрипотцой"),
        g("Laomedeia", true, "живой"),
        g("Achird", false, "дружелюбный"),
        g("Pulcherrima", true, "напористый"),
        g("Zubenelgenubi", false, "разговорный"),
        g("Sadachbia", false, "живой"),
        g("Fenrir", false, "эмоциональный"),
    )

    val openai = listOf(
        NeuralVoice("coral", "Coral", "Женский · тёплый"),
        NeuralVoice("onyx", "Onyx", "Мужской · низкий"),
        NeuralVoice("sage", "Sage", "Женский · спокойный"),
        NeuralVoice("ash", "Ash", "Мужской · мягкий"),
        NeuralVoice("nova", "Nova", "Женский · живой"),
        NeuralVoice("echo", "Echo", "Мужской · ровный"),
        NeuralVoice("shimmer", "Shimmer", "Женский · светлый"),
        NeuralVoice("ballad", "Ballad", "Мужской · выразительный"),
        NeuralVoice("fable", "Fable", "Нейтральный · сказочный"),
        NeuralVoice("alloy", "Alloy", "Нейтральный"),
        NeuralVoice("verse", "Verse", "Мужской · артистичный"),
    )

    fun voices(engine: String) = when (engine) {
        GEMINI -> gemini
        OPENAI -> openai
        else -> emptyList()
    }

    fun voiceOrDefault(engine: String, id: String) = voices(engine).firstOrNull { it.id == id }?.id ?: voices(engine).firstOrNull()?.id.orEmpty()
}

class NeuralException(message: String, val network: Boolean = false) : Exception(message)

private class Pcm(val samples: ShortArray, val rate: Int)

class NeuralTts(
    private val app: App,
    private val engineId: String,
    private val metadata: Metadata,
    initialPreferences: AndroidTtsPreferences,
) : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {

    override val voices: Set<AndroidTtsEngine.Voice> = emptySet()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null
    private val cache = ConcurrentHashMap<String, CompletableDeferred<Pcm>>()
    private val order = ArrayDeque<String>()
    private val groups = Channel<List<String>>(Channel.UNLIMITED)
    private var playing: Job? = null
    private var currentId: TtsEngine.RequestId? = null
    private var started = false
    private var track: AudioTrack? = null
    private var trackRate = 0
    private var firstGroup = true
    @Volatile private var workingModel: String? = null

    private val _settings = MutableStateFlow(resolve(initialPreferences))
    override val settings: StateFlow<AndroidTtsSettings> = _settings.asStateFlow()

    init {
        lastError = null
        scope.launch {
            for (group in groups) synthGroup(group)
        }
    }

    private fun resolve(p: AndroidTtsPreferences) = AndroidTtsSettings(
        language = p.language ?: metadata.language ?: Language(Locale.getDefault()),
        overrideContentLanguage = p.language != null,
        pitch = p.pitch ?: 1.0,
        speed = p.speed ?: 1.0,
        voices = p.voices.orEmpty(),
    )

    override fun submitPreferences(preferences: AndroidTtsPreferences) {
        _settings.value = resolve(preferences)
        applyParams()
    }

    override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
        this.listener = listener
    }

    fun prefetch(sentences: List<String>) {
        checkVoice()
        val clean = sentences.filter { s -> s.any { it.isLetterOrDigit() } && !cache.containsKey(s) }
        if (clean.isEmpty()) return
        val limit = if (firstGroup) 260 else 900
        firstGroup = false
        var chunk = mutableListOf<String>()
        var size = 0
        clean.forEach { s ->
            if (chunk.isNotEmpty() && size + s.length > limit) {
                enqueue(chunk)
                chunk = mutableListOf()
                size = 0
            }
            chunk += s
            size += s.length
        }
        if (chunk.isNotEmpty()) enqueue(chunk)
    }

    private fun enqueue(chunk: List<String>) {
        chunk.forEach { remember(it) }
        groups.trySend(chunk)
    }

    private fun remember(text: String): CompletableDeferred<Pcm> {
        cache[text]?.let { return it }
        val d = CompletableDeferred<Pcm>()
        cache[text] = d
        synchronized(order) {
            order.addLast(text)
            while (order.size > 120) order.removeFirstOrNull()?.let { cache.remove(it) }
        }
        return d
    }

    private suspend fun synthGroup(group: List<String>) {
        val pending = group.filter { cache[it]?.isCompleted == false }
        if (pending.isEmpty()) return
        try {
            val pcm = synthesize(pending.joinToString(" "))
            val slices = split(pcm, pending)
            pending.forEachIndexed { i, s -> cache[s]?.complete(slices[i]) }
        } catch (e: Exception) {
            pending.forEach { cache[it]?.completeExceptionally(e) }
        }
    }

    private var cacheVoice: String? = null

    private fun checkVoice() {
        val v = app.readerSettings.settings.value.ttsVoice
        if (v != cacheVoice) {
            cacheVoice = v
            cache.clear()
            synchronized(order) { order.clear() }
        }
    }

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        checkVoice()
        playing?.cancel()
        currentId = requestId
        started = false
        val known = cache[text]
        val deferred = known ?: remember(text)
        playing = scope.launch {
            if (known == null || !kotlinx.coroutines.withTimeoutOrNull(2_500) { runCatching { deferred.await() }; true }.let { it == true }) {
                if (!deferred.isCompleted) {
                    scope.launch {
                        runCatching { synthesize(text) }
                            .onSuccess { deferred.complete(it) }
                            .onFailure { if (!deferred.isCompleted) deferred.completeExceptionally(it) }
                    }
                }
            }
            val pcm = try {
                deferred.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                cache.remove(text)
                lastError = e.message
                val err = if ((e as? NeuralException)?.network == true || e is java.io.IOException) AndroidTtsEngine.Error.Network else AndroidTtsEngine.Error.Synthesis
                withContext(Dispatchers.Main) { listener?.onError(requestId, err) }
                return@launch
            }
            play(requestId, text, pcm)
        }
    }

    private suspend fun play(id: TtsEngine.RequestId, text: String, pcm: Pcm) {
        val t = ensureTrack(pcm.rate)
        withContext(Dispatchers.Main) {
            started = true
            listener?.onStart(id)
        }
        val samples = pcm.samples
        if (samples.isEmpty()) {
            withContext(Dispatchers.Main) { listener?.onDone(id) }
            return
        }
        val words = Regex("\\S+").findAll(text).map { it.range }.toList()
        val startHead = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        t.play()
        var written = 0
        var lastWord = -1
        val step = pcm.rate / 10
        while (scope.isActive && kotlinx.coroutines.currentCoroutineContext().isActive) {
            if (written < samples.size) {
                val n = minOf(step, samples.size - written)
                val r = t.write(samples, written, n)
                if (r < 0) break
                written += r
            } else {
                delay(30)
            }
            val played = ((t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) - startHead).coerceAtLeast(0)
            val fraction = (played.toDouble() / samples.size).coerceIn(0.0, 1.0)
            if (words.isNotEmpty()) {
                val w = (fraction * words.size).toInt().coerceIn(0, words.lastIndex)
                if (w != lastWord) {
                    lastWord = w
                    val range = words[w]
                    withContext(Dispatchers.Main) { listener?.onRange(id, range) }
                }
            }
            if (written >= samples.size && played >= samples.size - pcm.rate / 50) break
        }
        if (kotlinx.coroutines.currentCoroutineContext().isActive && currentId == id) {
            withContext(Dispatchers.Main) { listener?.onDone(id) }
        }
    }

    private fun ensureTrack(rate: Int): AudioTrack {
        track?.takeIf { trackRate == rate }?.let { return it }
        track?.runCatching { release() }
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(min * 2, rate / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        trackRate = rate
        applyParams()
        return t
    }

    private fun applyParams() {
        val t = track ?: return
        val s = _settings.value
        runCatching {
            t.playbackParams = PlaybackParams()
                .setSpeed(s.speed.toFloat().coerceIn(0.5f, 3f))
                .setPitch(s.pitch.toFloat().coerceIn(0.5f, 2f))
        }
    }

    override fun stop() {
        val id = currentId
        playing?.cancel()
        playing = null
        track?.runCatching {
            pause()
            flush()
        }
        if (id != null) {
            currentId = null
            if (started) listener?.onInterrupted(id) else listener?.onFlushed(id)
        }
    }

    override fun close() {
        playing?.cancel()
        groups.close()
        scope.cancel()
        track?.runCatching { release() }
        track = null
        cache.clear()
    }

    private suspend fun synthesize(text: String): Pcm = withContext(Dispatchers.IO) {
        val provider = NeuralVoices.providerId(engineId)
        val key = app.ai.vault.get(provider) ?: throw NeuralException(
            if (provider == "gemini") "Для голосов Gemini нужен ключ Gemini в настройках ИИ" else "Для голосов OpenAI нужен ключ ChatGPT в настройках ИИ",
        )
        val voice = NeuralVoices.voiceOrDefault(engineId, app.readerSettings.settings.value.ttsVoice)
        when (engineId) {
            NeuralVoices.GEMINI -> gemini(key, voice, text)
            else -> openai(key, voice, text)
        }
    }

    private fun gemini(key: String, voice: String, text: String): Pcm {
        val models = listOfNotNull(workingModel) + GEMINI_MODELS.filter { it != workingModel }
        var last: Exception? = null
        for (model in models) {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put(
                            "speechConfig",
                            JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))),
                        ),
                )
            val conn = open("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", mapOf("x-goog-api-key" to key), body)
            val code = conn.responseCode
            if (code == 404 || code == 400 && model != models.last()) {
                last = NeuralException(errorText("Gemini", code, conn))
                continue
            }
            if (code !in 200..299) throw NeuralException(errorText("Gemini", code, conn))
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val out = ByteArrayOutputStream()
            var rate = 24000
            for (i in 0 until (parts?.length() ?: 0)) {
                val inline = parts!!.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                Regex("rate=(\\d+)").find(inline.optString("mimeType"))?.groupValues?.get(1)?.toIntOrNull()?.let { rate = it }
                out.write(Base64.decode(inline.optString("data"), Base64.DEFAULT))
            }
            if (out.size() == 0) throw NeuralException("Gemini не вернул звук. Попробуй другой голос")
            workingModel = model
            return Pcm(toShorts(out.toByteArray()), rate)
        }
        throw last ?: NeuralException("Голоса Gemini недоступны")
    }

    private fun openai(key: String, voice: String, text: String): Pcm {
        var last: Exception? = null
        for (model in OPENAI_MODELS) {
            val body = JSONObject()
                .put("model", model)
                .put("voice", voice)
                .put("input", text)
                .put("response_format", "pcm")
            if (model != "tts-1") {
                body.put("instructions", "Читай по-русски естественно и выразительно, как профессиональный диктор аудиокниги, в спокойном темпе.")
            }
            val conn = open("https://api.openai.com/v1/audio/speech", mapOf("Authorization" to "Bearer $key"), body)
            val code = conn.responseCode
            if ((code == 404 || code == 400) && model != OPENAI_MODELS.last()) {
                last = NeuralException(errorText("OpenAI", code, conn))
                continue
            }
            if (code !in 200..299) throw NeuralException(errorText("OpenAI", code, conn))
            val bytes = conn.inputStream.use { it.readBytes() }
            return Pcm(toShorts(bytes), 24000)
        }
        throw last ?: NeuralException("Голоса OpenAI недоступны")
    }

    private fun open(url: String, headers: Map<String, String>, body: JSONObject): HttpURLConnection {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            return conn
        } catch (e: java.io.IOException) {
            throw NeuralException("Нет связи с сервером озвучки. Проверь интернет", network = true)
        }
    }

    private fun errorText(name: String, code: Int, conn: HttpURLConnection): String {
        val detail = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        return when (code) {
            401, 403 -> "Ключ $name не подходит для озвучки"
            429 -> "Лимит озвучки $name исчерпан. Попробуй позже или выбери другой голос"
            else -> "Ошибка озвучки $name ($code)" + (Regex("\"message\"\\s*:\\s*\"([^\"]{1,140})").find(detail)?.groupValues?.get(1)?.let { ": $it" } ?: "")
        }
    }

    private fun toShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        for (i in out.indices) out[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
        return out
    }

    private fun split(pcm: Pcm, sentences: List<String>): List<Pcm> {
        if (sentences.size == 1) return listOf(pcm)
        val s = pcm.samples
        val frame = pcm.rate / 100
        val frames = s.size / frame
        if (frames < sentences.size * 4) return evenSplit(pcm, sentences)
        val rms = DoubleArray(frames) { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) sum += s[i].toDouble() * s[i]
            sqrt(sum / frame)
        }
        val peak = rms.maxOrNull() ?: 1.0
        val quiet = maxOf(peak * 0.04, 180.0)
        var first = rms.indexOfFirst { it > quiet }.coerceAtLeast(0)
        var last = rms.indexOfLast { it > quiet }.coerceAtLeast(first + 1)
        val gaps = mutableListOf<Pair<Int, Int>>()
        var run = -1
        for (f in first..last) {
            if (rms[f] <= quiet) {
                if (run < 0) run = f
            } else if (run >= 0) {
                if (f - run >= 7) gaps += (run + f) / 2 to (f - run)
                run = -1
            }
        }
        val weights = sentences.map { it.length.toDouble() + 12 }
        val total = weights.sum()
        val span = (last - first).toDouble()
        val cuts = mutableListOf<Int>()
        var acc = 0.0
        var prev = first
        for (k in 0 until sentences.size - 1) {
            acc += weights[k]
            val expected = first + span * acc / total
            val tolerance = span * weights[k] / total * 0.45 + 15
            val best = gaps
                .filter { it.first > prev + 10 && abs(it.first - expected) <= tolerance }
                .minByOrNull { abs(it.first - expected) - it.second * 0.8 }
            val cut = best?.first ?: expected.toInt()
            cuts += cut
            prev = cut
        }
        val bounds = listOf(0) + cuts.map { it * frame } + listOf(s.size)
        return (0 until sentences.size).map { i -> Pcm(s.copyOfRange(bounds[i], maxOf(bounds[i], bounds[i + 1])), pcm.rate) }
    }

    private fun evenSplit(pcm: Pcm, sentences: List<String>): List<Pcm> {
        val total = sentences.sumOf { it.length }.coerceAtLeast(1)
        var start = 0
        var acc = 0
        return sentences.mapIndexed { i, t ->
            acc += t.length
            val end = if (i == sentences.lastIndex) pcm.samples.size else (pcm.samples.size.toLong() * acc / total).toInt()
            Pcm(pcm.samples.copyOfRange(start, end), pcm.rate).also { start = end }
        }
    }

    companion object {
        @Volatile var lastError: String? = null
        private val GEMINI_MODELS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts")
        private val OPENAI_MODELS = listOf("gpt-4o-mini-tts", "tts-1-hd", "tts-1")
    }
}

class NeuralTtsProvider(
    private val app: App,
    private val engineId: String,
) : TtsEngineProvider<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsPreferencesEditor, AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {
    @Volatile var engine: NeuralTts? = null
        private set

    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences,
    ): Try<TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice>, org.readium.r2.shared.util.Error> {
        val e = NeuralTts(app, engineId, publication.metadata, initialPreferences)
        engine = e
        return Try.success(e)
    }

    override fun createPreferencesEditor(publication: Publication, initialPreferences: AndroidTtsPreferences): AndroidTtsPreferencesEditor =
        AndroidTtsPreferencesEditor(initialPreferences, publication.metadata, org.readium.navigator.media.tts.android.AndroidTtsDefaults())

    override fun createEmptyPreferences(): AndroidTtsPreferences = AndroidTtsPreferences()

    override fun getPlaybackParameters(settings: AndroidTtsSettings): androidx.media3.common.PlaybackParameters =
        androidx.media3.common.PlaybackParameters(settings.speed.toFloat(), settings.pitch.toFloat())

    override fun updatePlaybackParameters(previousPreferences: AndroidTtsPreferences, playbackParameters: androidx.media3.common.PlaybackParameters): AndroidTtsPreferences =
        previousPreferences.copy(speed = playbackParameters.speed.toDouble(), pitch = playbackParameters.pitch.toDouble())

    override fun mapEngineError(error: AndroidTtsEngine.Error): androidx.media3.common.PlaybackException =
        androidx.media3.common.PlaybackException("Neural speech error", null, androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED)
}
