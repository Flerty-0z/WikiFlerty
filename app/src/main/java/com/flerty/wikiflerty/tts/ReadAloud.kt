package com.flerty.wikiflerty.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.reader.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language

/** One "read aloud" session for one book. */
class ReadAloudSession(val bookId: String, val navigator: AndroidTtsNavigator)

/** An installed text-to-speech engine. */
data class TtsEngineInfo(val packageName: String, val label: String)

/** A voice of an engine. */
data class TtsVoiceInfo(val name: String, val language: Language, val quality: Int, val needsNetwork: Boolean)

/** Sleep timer: stop after some minutes, or at the end of the chapter. */
sealed class SleepTimer {
    data class Minutes(val minutes: Int) : SleepTimer()
    data object EndOfChapter : SleepTimer()
}

/**
 * Reads the open book aloud with the phone's text-to-speech, sentence by sentence.
 * Keeps going with the screen off through [ReadAloudService], which shows the controls in a notification.
 */
class ReadAloud(private val app: App) : TtsNavigator.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _session = MutableStateFlow<ReadAloudSession?>(null)
    val session: StateFlow<ReadAloudSession?> = _session.asStateFlow()

    /** Problems to show to the user (and whether a voice can be installed for them). */
    data class Problem(val message: String, val canInstallVoice: Boolean = false)

    private val _problems = MutableSharedFlow<Problem>(extraBufferCapacity = 4)
    val problems = _problems.asSharedFlow()

    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    val sleep: StateFlow<SleepTimer?> = _sleep.asStateFlow()
    private var sleepJob: Job? = null
    private var watchJob: Job? = null
    private var starting = false

    /** Starts reading [publication] at [start]. Stops any other book first. */
    fun start(publication: Publication, bookId: String, start: Locator?, settings: ReaderSettings) {
        if (starting) return
        starting = true
        stop()
        scope.launch {
            try {
                val factory = TtsNavigatorFactory(app, publication, EngineTtsProvider(app, settings.ttsEngine.ifBlank { null }))
                if (factory == null) {
                    _problems.emit(Problem("Эту книгу нельзя прочитать вслух"))
                    return@launch
                }
                val navigator = factory.createNavigator(this@ReadAloud, start, preferences(settings)).getOrNull()
                if (navigator == null) {
                    _problems.emit(Problem("Синтезатор речи не запустился. Проверь, что он установлен"))
                    return@launch
                }
                _session.value = ReadAloudSession(bookId, navigator)
                app.startService(Intent(app, ReadAloudService::class.java))
                watch(navigator, bookId)
                navigator.play()
            } finally {
                starting = false
            }
        }
    }

    private fun watch(navigator: AndroidTtsNavigator, bookId: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            // Errors and the end of the book.
            launch {
                navigator.playback.collect { playback ->
                    when (val state = playback.state as? TtsNavigator.State) {
                        is TtsNavigator.State.Ended -> stop()
                        is TtsNavigator.State.Failure -> {
                            _problems.emit(problemOf(state.error))
                            stop()
                        }
                        else -> Unit
                    }
                }
            }
            // Keep the reading position saved even while the reader screen is closed.
            launch {
                var last = 0L
                navigator.currentLocator.collect { locator ->
                    val now = System.currentTimeMillis()
                    if (now - last > 3_000) {
                        last = now
                        app.library.saveProgress(bookId, locator)
                    }
                }
            }
        }
    }

    private fun problemOf(error: TtsNavigator.Error): Problem {
        val engineError = (error as? TtsNavigator.Error.EngineError<*>)?.cause as? AndroidTtsEngine.Error
        return when (engineError) {
            is AndroidTtsEngine.Error.LanguageMissingData -> Problem(
                "Нет голоса для языка «${engineError.language.locale.displayLanguage}». Установи его в синтезаторе речи",
                canInstallVoice = true,
            )
            AndroidTtsEngine.Error.Network, AndroidTtsEngine.Error.NetworkTimeout ->
                Problem("Этому голосу нужен интернет. Выбери голос без интернета или подключись к сети")
            AndroidTtsEngine.Error.NotInstalledYet -> Problem("Голос ещё скачивается. Попробуй через минуту", canInstallVoice = true)
            null -> Problem("Не удалось прочитать текст книги")
            else -> Problem("Синтезатор речи выдал ошибку. Попробуй другой голос или движок")
        }
    }

    fun play() = _session.value?.navigator?.play()
    fun pause() = _session.value?.navigator?.pause()
    fun toggle() {
        val navigator = _session.value?.navigator ?: return
        if (navigator.playback.value.playWhenReady) navigator.pause() else navigator.play()
    }
    fun next() = _session.value?.navigator?.skipToNextUtterance()
    fun previous() = _session.value?.navigator?.skipToPreviousUtterance()

    /** Starts reading from [locator] (for example after the user turned pages by hand). */
    fun go(locator: Locator) = _session.value?.navigator?.go(locator)

    /** Applies new speed, pitch or voice right away. */
    fun apply(settings: ReaderSettings) {
        _session.value?.navigator?.submitPreferences(preferences(settings))
    }

    fun setSleep(timer: SleepTimer?) {
        sleepJob?.cancel()
        _sleep.value = timer
        val navigator = _session.value?.navigator ?: return
        sleepJob = when (timer) {
            null -> null
            is SleepTimer.Minutes -> scope.launch {
                delay(timer.minutes * 60_000L)
                navigator.pause()
                _sleep.value = null
            }
            SleepTimer.EndOfChapter -> scope.launch {
                // The chapter ends when the voice moves to the next file of the book.
                navigator.location.map { it.href.toString() }.distinctUntilChanged().drop(1).first()
                navigator.pause()
                _sleep.value = null
            }
        }
    }

    override fun onStopRequested() = stop()

    fun stop() {
        sleepJob?.cancel()
        _sleep.value = null
        watchJob?.cancel()
        val session = _session.value ?: return
        _session.value = null
        ReadAloudService.release()
        runCatching { session.navigator.close() }
        app.stopService(Intent(app, ReadAloudService::class.java))
    }

    companion object {
        /** Our settings → Readium's text-to-speech preferences. */
        fun preferences(s: ReaderSettings): AndroidTtsPreferences {
            val voiceLanguage = s.ttsVoiceLanguage.takeIf { s.ttsVoice.isNotBlank() && it.isNotBlank() }?.let { Language(it) }
            val voiceId = AndroidTtsEngine.Voice.Id(s.ttsVoice)
            return AndroidTtsPreferences(
                language = voiceLanguage?.removeRegion(),
                pitch = s.ttsPitch,
                speed = s.ttsSpeed,
                voices = voiceLanguage?.let { mapOf(it to voiceId, it.removeRegion() to voiceId) },
            )
        }

        /** Engines installed on the phone, the default one first. */
        suspend fun engines(context: Context): List<TtsEngineInfo> {
            val tts = EngineTts.initialize(context, null) ?: return emptyList()
            return try {
                val default = tts.defaultEngine
                tts.engines.map { TtsEngineInfo(it.name, it.label) }.sortedByDescending { it.packageName == default }
            } finally {
                tts.shutdown()
            }
        }

        /** Voices of an engine (installed ones only). */
        suspend fun voices(context: Context, enginePackage: String?): List<TtsVoiceInfo> {
            val tts = EngineTts.initialize(context, enginePackage) ?: return emptyList()
            return try {
                runCatching { tts.voices }.getOrNull().orEmpty()
                    .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in (it.features ?: emptySet()) }
                    .map { TtsVoiceInfo(it.name, Language(it.locale), it.quality, it.isNetworkConnectionRequired) }
            } finally {
                tts.shutdown()
            }
        }

        /** Opens the engine's screen for downloading voices. */
        fun installVoices(context: Context) = AndroidTtsEngine.requestInstallVoice(context)
    }
}
