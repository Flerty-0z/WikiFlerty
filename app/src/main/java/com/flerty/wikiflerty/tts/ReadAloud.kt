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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language

class ReadAloudSession(val bookId: String, val navigator: AndroidTtsNavigator)

data class TtsEngineInfo(val packageName: String, val label: String)

data class TtsVoiceInfo(val name: String, val language: Language, val quality: Int, val needsNetwork: Boolean)

sealed class SleepTimer {
    data class Minutes(val minutes: Int) : SleepTimer()
    data object EndOfChapter : SleepTimer()
}

class ReadAloud(private val app: App) : TtsNavigator.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _session = MutableStateFlow<ReadAloudSession?>(null)
    val session: StateFlow<ReadAloudSession?> = _session.asStateFlow()

    data class Problem(val message: String, val canInstallVoice: Boolean = false, val openVoices: Boolean = false)

    private val _problems = MutableSharedFlow<Problem>(extraBufferCapacity = 4)
    val problems = _problems.asSharedFlow()

    private val _listening = MutableStateFlow<String?>(null)
    val listening: StateFlow<String?> = _listening.asStateFlow()

    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    val sleep: StateFlow<SleepTimer?> = _sleep.asStateFlow()
    private var sleepJob: Job? = null
    private var watchJob: Job? = null
    private var starting = false

    fun start(publication: Publication, bookId: String, start: Locator?, settings: ReaderSettings) {
        if (starting) return
        starting = true
        stop()
        scope.launch {
            try {
                val navigator = open(publication, start, settings) ?: return@launch
                _session.value = ReadAloudSession(bookId, navigator)
                app.startService(Intent(app, ReadAloudService::class.java))
                watch(navigator, bookId)
                navigator.play()
            } finally {
                starting = false
            }
        }
    }

    private suspend fun open(publication: Publication, start: Locator?, settings: ReaderSettings): AndroidTtsNavigator? {
        val neuralId = settings.ttsEngine.takeIf { NeuralVoices.isNeural(it) }
        if (neuralId == NeuralVoices.PIPER) {
            if (!PiperVoices.supported()) {
                _problems.emit(Problem("Офлайн-голоса не работают на этом телефоне. Выбери голос телефона или онлайн-голос", openVoices = true))
                return null
            }
            val voice = NeuralVoices.voiceOrDefault(NeuralVoices.PIPER, settings.ttsVoice)
            if (!PiperVoices.isInstalled(app, voice)) {
                _problems.emit(Problem("Этот голос ещё не скачан. Скачай его в настройках чтения вслух", openVoices = true))
                return null
            }
        }
        val engines: List<String?> = if (neuralId != null) listOf(neuralId) else systemEngines(settings.ttsEngine.ifBlank { null })
        val places = startPlaces(publication, start)
        var engineFailed = false
        for (engine in engines) {
            val factory = factory(publication, engine) ?: run {
                _problems.emit(Problem("Эту книгу нельзя прочитать вслух"))
                return null
            }
            engineFailed = false
            for (place in places) {
                val result = runCatching { factory.createNavigator(this@ReadAloud, place, preferences(settings)) }.getOrNull() ?: continue
                result.getOrNull()?.let { return it }
                if (result.failureOrNull() is TtsNavigatorFactory.Error.EngineInitialization) {
                    engineFailed = true
                    break
                }
            }
            if (!engineFailed) break
        }
        _problems.emit(
            if (engineFailed) {
                Problem("Синтезатор речи телефона не запустился. Попробуй офлайн-нейроголос — он работает без него", openVoices = true)
            } else {
                Problem("Не нашёл текст для чтения на этой странице. Перелистни дальше и попробуй ещё раз")
            },
        )
        return null
    }

    private fun factory(publication: Publication, engine: String?) =
        if (engine != null && NeuralVoices.isNeural(engine)) {
            val neural = NeuralTtsProvider(app, engine)
            TtsNavigatorFactory(app, publication, neural, tokenizerFactory = { language ->
                val base = org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer(org.readium.r2.shared.util.tokenizer.TextUnit.Sentence, language)
                org.readium.r2.shared.util.tokenizer.Tokenizer<String, IntRange> { text ->
                    base.tokenize(text).also { ranges -> runCatching { neural.engine?.prefetch(ranges.map { text.substring(it) }) } }
                }
            })
        } else {
            TtsNavigatorFactory(app, publication, EngineTtsProvider(app, engine))
        }

    private fun systemEngines(chosen: String?): List<String?> {
        val installed = runCatching {
            app.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .mapNotNull { it.serviceInfo?.packageName }
        }.getOrDefault(emptyList())
        val preferred = listOf("com.google.android.tts")
        return (listOf(chosen, null) + preferred.filter { it in installed } + installed).distinct()
    }

    private fun startPlaces(publication: Publication, start: Locator?): List<Locator?> {
        if (start == null) return listOf(null)
        val out = mutableListOf<Locator?>(start)
        val order = publication.readingOrder
        fun name(s: String) = android.net.Uri.decode(s.substringBefore('#').substringBefore('?')).substringAfterLast('/').lowercase()
        val target = name(start.href.toString())
        val index = order.indexOfFirst { name(it.url().toString()) == target }
        if (index >= 0) {
            val base = runCatching { publication.locatorFromLink(order[index]) }.getOrNull()
            if (base != null) {
                start.locations.progression?.let { p -> out += base.copyWithLocations(progression = p) }
                out += base
            }
            for (i in index + 1 until minOf(index + 4, order.size)) {
                runCatching { publication.locatorFromLink(order[i]) }.getOrNull()?.let { out += it }
            }
        }
        return out
    }

    private fun watch(navigator: AndroidTtsNavigator, bookId: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
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
            launch {
                var since = 0L
                var unsaved = 0L
                fun flush() {
                    if (since <= 0L) return
                    val now = android.os.SystemClock.elapsedRealtime()
                    val ms = (now - since).coerceIn(0L, 10 * 60_000L)
                    since = now
                    if (ms <= 0L) return
                    val key = app.library.books.value.firstOrNull { it.id == bookId }?.statsKey ?: bookId
                    app.stats.add(key, ms, 0)
                    unsaved += ms
                    if (unsaved >= 60_000L) {
                        unsaved = 0L
                        app.appScope.launch(Dispatchers.IO) { runCatching { app.stats.save() } }
                    }
                }
                navigator.playback
                    .map { it.playWhenReady && it.state !is TtsNavigator.State.Ended && it.state !is TtsNavigator.State.Failure }
                    .distinctUntilChanged()
                    .collectLatest { playing ->
                        _listening.value = if (playing) bookId else null
                        if (!playing) return@collectLatest
                        since = android.os.SystemClock.elapsedRealtime()
                        try {
                            while (true) {
                                delay(15_000)
                                flush()
                            }
                        } finally {
                            flush()
                            since = 0L
                            app.appScope.launch(Dispatchers.IO) { runCatching { app.stats.save() } }
                        }
                    }
            }
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
        NeuralTts.lastError?.let { message ->
            NeuralTts.lastError = null
            return Problem(message)
        }
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

    fun go(locator: Locator) = _session.value?.navigator?.go(locator)

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
        _listening.value = null
        val session = _session.value ?: return
        _session.value = null
        ReadAloudService.release()
        runCatching { session.navigator.close() }
        app.stopService(Intent(app, ReadAloudService::class.java))
    }

    companion object {
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

        suspend fun engines(context: Context): List<TtsEngineInfo> {
            val tts = EngineTts.initialize(context, null) ?: return emptyList()
            return try {
                val default = tts.defaultEngine
                tts.engines.map { TtsEngineInfo(it.name, it.label) }.sortedByDescending { it.packageName == default }
            } finally {
                tts.shutdown()
            }
        }

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

        fun installVoices(context: Context) = AndroidTtsEngine.requestInstallVoice(context)
    }
}
