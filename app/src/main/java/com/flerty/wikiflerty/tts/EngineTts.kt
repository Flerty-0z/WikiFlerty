package com.flerty.wikiflerty.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import org.readium.r2.shared.util.DebugError

class EngineTts private constructor(
    private val context: Context,
    private val enginePackage: String?,
    engine: TextToSpeech,
    private val metadata: Metadata,
    override val voices: Set<AndroidTtsEngine.Voice>,
    initialPreferences: AndroidTtsPreferences,
) : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {
    companion object {
        suspend fun create(
            context: Context,
            enginePackage: String?,
            metadata: Metadata,
            initialPreferences: AndroidTtsPreferences,
        ): EngineTts? {
            val tts = initialize(context, enginePackage) ?: return null
            val voices = runCatching { tts.voices }.getOrNull().orEmpty()
                .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in (it.features ?: emptySet()) }
                .map { it.toVoice() }
                .toSet()
            return EngineTts(context, enginePackage, tts, metadata, voices, initialPreferences)
        }

        suspend fun initialize(context: Context, enginePackage: String?): TextToSpeech? {
            val ready = CompletableDeferred<Boolean>()
            val listener = TextToSpeech.OnInitListener { status -> ready.complete(status == TextToSpeech.SUCCESS) }
            val tts = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
            return if (ready.await()) tts else null.also { tts.shutdown() }
        }

        fun android.speech.tts.Voice.toVoice() = AndroidTtsEngine.Voice(
            id = AndroidTtsEngine.Voice.Id(name),
            language = Language(locale),
            quality = when {
                quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> AndroidTtsEngine.Voice.Quality.Highest
                quality >= android.speech.tts.Voice.QUALITY_HIGH -> AndroidTtsEngine.Voice.Quality.High
                quality >= android.speech.tts.Voice.QUALITY_NORMAL -> AndroidTtsEngine.Voice.Quality.Normal
                quality >= android.speech.tts.Voice.QUALITY_LOW -> AndroidTtsEngine.Voice.Quality.Low
                else -> AndroidTtsEngine.Voice.Quality.Lowest
            },
            requiresNetwork = isNetworkConnectionRequired,
        )

        fun errorOf(code: Int): AndroidTtsEngine.Error = when (code) {
            TextToSpeech.ERROR_INVALID_REQUEST -> AndroidTtsEngine.Error.InvalidRequest
            TextToSpeech.ERROR_NETWORK -> AndroidTtsEngine.Error.Network
            TextToSpeech.ERROR_NETWORK_TIMEOUT -> AndroidTtsEngine.Error.NetworkTimeout
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> AndroidTtsEngine.Error.NotInstalledYet
            TextToSpeech.ERROR_OUTPUT -> AndroidTtsEngine.Error.Output
            TextToSpeech.ERROR_SERVICE -> AndroidTtsEngine.Error.Service
            TextToSpeech.ERROR_SYNTHESIS -> AndroidTtsEngine.Error.Synthesis
            else -> AndroidTtsEngine.Error.Unknown
        }
    }

    private data class Request(val id: TtsEngine.RequestId, val text: String, val language: Language?)

    private sealed class State {
        data class Available(val engine: TextToSpeech) : State()
        data class Waiting(val pending: MutableList<Request> = mutableListOf()) : State()
        data class Failure(val error: AndroidTtsEngine.Error) : State()
    }

    private val scope: CoroutineScope = MainScope()
    private var listener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null
    private var state: State = State.Available(engine)
    private var closed = false

    private val _settings = MutableStateFlow(resolve(initialPreferences))
    override val settings: StateFlow<AndroidTtsSettings> = _settings.asStateFlow()

    init {
        engine.applyRateAndPitch(_settings.value)
    }

    private fun resolve(preferences: AndroidTtsPreferences) = AndroidTtsSettings(
        language = preferences.language ?: metadata.language ?: Language(Locale.getDefault()),
        overrideContentLanguage = preferences.language != null,
        pitch = preferences.pitch ?: 1.0,
        speed = preferences.speed ?: 1.0,
        voices = preferences.voices.orEmpty(),
    )

    override fun submitPreferences(preferences: AndroidTtsPreferences) {
        _settings.value = resolve(preferences)
        (state as? State.Available)?.engine?.applyRateAndPitch(_settings.value)
    }

    override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
        this.listener = listener
        (state as? State.Available)?.let { attachListener(it.engine) }
    }

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        check(!closed) { "Engine is closed." }
        val request = Request(requestId, text, language)
        when (val now = state) {
            is State.Waiting -> now.pending.add(request)
            is State.Failure -> reconnect(request)
            is State.Available -> if (!doSpeak(now.engine, request)) {
                clean(now.engine)
                reconnect(request)
            }
        }
    }

    override fun stop() {
        when (val now = state) {
            is State.Available -> now.engine.stop()
            is State.Failure -> Unit
            is State.Waiting -> {
                now.pending.forEach { listener?.onFlushed(it.id) }
                now.pending.clear()
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        (state as? State.Available)?.let { clean(it.engine) }
    }

    private fun doSpeak(engine: TextToSpeech, request: Request): Boolean =
        engine.chooseVoice(request) && engine.speak(request.text, TextToSpeech.QUEUE_ADD, null, request.id.value) == TextToSpeech.SUCCESS

    private fun attachListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(listener?.let { Progress(it) })
    }

    private fun reconnect(request: Request) {
        state = State.Waiting(mutableListOf(request))
        scope.launch {
            val engine = initialize(context, enginePackage)
            val waiting = state as? State.Waiting ?: return@launch
            if (engine == null) {
                val error = AndroidTtsEngine.Error.Service
                state = State.Failure(error)
                waiting.pending.forEach { listener?.onError(it.id, error) }
                return@launch
            }
            attachListener(engine)
            engine.applyRateAndPitch(_settings.value)
            state = State.Available(engine)
            if (closed) engine.shutdown() else waiting.pending.forEach { doSpeak(engine, it) }
        }
    }

    private fun clean(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(null)
        engine.shutdown()
    }

    private fun TextToSpeech.applyRateAndPitch(s: AndroidTtsSettings) {
        setSpeechRate(s.speed.toFloat())
        setPitch(s.pitch.toFloat())
    }

    private fun TextToSpeech.chooseVoice(request: Request): Boolean {
        val s = _settings.value
        val language = request.language
            .takeUnless { s.overrideContentLanguage }
            ?.takeIf { isLanguageAvailable(it.locale) != TextToSpeech.LANG_NOT_SUPPORTED }
            ?: s.language.takeIf { isLanguageAvailable(it.locale) != TextToSpeech.LANG_NOT_SUPPORTED }
            ?: defaultVoice?.locale?.let { Language(it) }
        if (language == null) {
            listener?.onError(request.id, AndroidTtsEngine.Error.Unknown)
            return false
        }
        if (isLanguageAvailable(language.locale) < TextToSpeech.LANG_AVAILABLE) {
            listener?.onError(request.id, AndroidTtsEngine.Error.LanguageMissingData(language))
            return false
        }
        val chosen = (s.voices[language] ?: s.voices[language.removeRegion()])
            ?.let { id -> runCatching { voices }.getOrNull()?.firstOrNull { it.name == id.value } }
        if (chosen != null) voice = chosen else this.language = language.locale
        return true
    }

    private class Progress(private val listener: TtsEngine.Listener<AndroidTtsEngine.Error>) : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = listener.onStart(TtsEngine.RequestId(utteranceId))

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            val id = TtsEngine.RequestId(utteranceId)
            if (interrupted) listener.onInterrupted(id) else listener.onFlushed(id)
        }

        override fun onDone(utteranceId: String) = listener.onDone(TtsEngine.RequestId(utteranceId))

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = onError(utteranceId, -1)

        override fun onError(utteranceId: String, errorCode: Int) =
            listener.onError(TtsEngine.RequestId(utteranceId), errorOf(errorCode))

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) =
            listener.onRange(TtsEngine.RequestId(utteranceId), start until end)
    }
}

class EngineTtsProvider(
    private val context: Context,
    private val enginePackage: String?,
) : TtsEngineProvider<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsPreferencesEditor, AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {
    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences,
    ): Try<TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice>, org.readium.r2.shared.util.Error> {
        val engine = EngineTts.create(context, enginePackage, publication.metadata, initialPreferences)
            ?: return Try.failure(DebugError("Text-to-speech engine did not start."))
        return Try.success(engine)
    }

    override fun createPreferencesEditor(publication: Publication, initialPreferences: AndroidTtsPreferences): AndroidTtsPreferencesEditor =
        AndroidTtsPreferencesEditor(initialPreferences, publication.metadata, org.readium.navigator.media.tts.android.AndroidTtsDefaults())

    override fun createEmptyPreferences(): AndroidTtsPreferences = AndroidTtsPreferences()

    override fun getPlaybackParameters(settings: AndroidTtsSettings): PlaybackParameters =
        PlaybackParameters(settings.speed.toFloat(), settings.pitch.toFloat())

    override fun updatePlaybackParameters(previousPreferences: AndroidTtsPreferences, playbackParameters: PlaybackParameters): AndroidTtsPreferences =
        previousPreferences.copy(speed = playbackParameters.speed.toDouble(), pitch = playbackParameters.pitch.toDouble())

    override fun mapEngineError(error: AndroidTtsEngine.Error): PlaybackException =
        PlaybackException("Text-to-speech error: ${error.javaClass.simpleName}", null, PlaybackException.ERROR_CODE_UNSPECIFIED)
}
