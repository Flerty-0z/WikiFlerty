package com.flerty.wikiflerty.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.reader.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    fun start(publication: Publication, bookId: String, start: Locator?, settings: ReaderSettings, snippet: String? = null) {
        if (starting) return
        starting = true
        stop()
        scope.launch {
            try {
                val navigator = open(publication, start, settings) ?: return@launch
                order = publication.readingOrder.map { place(it.url().toString()) }
                book = publication
                _seeking.value = true
                try {
                    align(navigator, snippet, start)
                } finally {
                    _seeking.value = false
                }
                _session.value = ReadAloudSession(bookId, navigator)
                app.achievements.event("tts")
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
        var crash: Throwable? = null
        var empty = false
        for (engine in engines) {
            val factory = factory(publication, engine) ?: run {
                _problems.emit(Problem("Эту книгу нельзя прочитать вслух"))
                return null
            }
            engineFailed = false
            for (place in places) {
                val attempt = runCatching { factory.createNavigator(this@ReadAloud, place, preferences(settings)) }
                attempt.exceptionOrNull()?.let { e ->
                    android.util.Log.w("ReadAloud", "createNavigator failed", e)
                    if (crash == null) crash = e
                }
                val result = attempt.getOrNull() ?: continue
                result.getOrNull()?.let {
                    shareAudio(it)
                    return it
                }
                when (result.failureOrNull()) {
                    is TtsNavigatorFactory.Error.EngineInitialization -> {
                        engineFailed = true
                        break
                    }
                    is TtsNavigatorFactory.Error.UnsupportedPublication -> empty = true
                    else -> Unit
                }
            }
            if (!engineFailed) break
        }
        val failure = crash
        lastCrash = crash
        _problems.emit(
            when {
                engineFailed -> Problem("Синтезатор речи телефона не запустился. Попробуй офлайн-нейроголос — он работает без него", openVoices = true)
                failure != null && !empty -> Problem("Чтение вслух не запустилось: ${describe(failure)}. Пришли этот скрин в @F1erty0z")
                hasText(publication, start) -> Problem("Не получилось подготовить текст к чтению. Пришли этот скрин в @F1erty0z" + (failure?.let { " (${describe(it)})" } ?: ""))
                else -> Problem("Не нашёл текст для чтения на этой странице. Перелистни дальше и попробуй ещё раз. Если не помогло — пришли этот скрин в @F1erty0z (${clue(publication, start)})")
            },
        )
        return null
    }

    private var lastCrash: Throwable? = null

    private fun shareAudio(navigator: AndroidTtsNavigator) {
        runCatching {
            navigator.asMedia3Player().setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .build(),
                false,
            )
        }
    }

    private suspend fun clue(publication: Publication, start: Locator?): String = withContext(Dispatchers.IO) {
        runCatching {
            val name = start?.href?.toString()?.substringBefore('#')?.substringAfterLast('/')
            val link = publication.readingOrder.firstOrNull { it.url().toString().substringAfterLast('/') == name } ?: publication.readingOrder.firstOrNull()
            val type = link?.mediaType?.toString() ?: "нет типа"
            val bytes = link?.let { publication.get(it)?.read()?.getOrNull() }
            val text = bytes?.let { String(it, Charsets.UTF_8) }
            val body = text?.substringAfter("<body", "")?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.length ?: -1
            "$type · ${bytes?.size ?: -1} Б · ${body} симв · ${publication.readingOrder.size} гл" + (SafeContent.lastIssue?.let { " · $it" } ?: "") + (lastCrash?.let { " · ${describe(it)}" } ?: "")
        }.getOrElse { it.javaClass.simpleName }
    }

    private fun describe(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        val name = root.javaClass.simpleName.ifBlank { root.javaClass.name.substringAfterLast('.') }
        val msg = root.message?.replace('\n', ' ')?.take(70)
        return if (msg.isNullOrBlank()) name else "$name — $msg"
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    private suspend fun hasText(publication: Publication, start: Locator?): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val content = publication.findService(org.readium.r2.shared.publication.services.content.ContentService::class)?.content(start) ?: return@runCatching false
            val it = content.iterator()
            var n = 0
            while (n < 40 && it.hasNext()) {
                val element = it.next()
                if (element is org.readium.r2.shared.publication.services.content.Content.TextualElement && !element.text.isNullOrBlank()) return@runCatching true
                n++
            }
            false
        }.getOrDefault(false)
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
                        wake(playing)
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

    private var goJob: Job? = null

    private val _seeking = MutableStateFlow(false)
    val seeking: StateFlow<Boolean> = _seeking.asStateFlow()

    private suspend fun jump(navigator: AndroidTtsNavigator, locator: Locator) {
        val before = navigator.location.value.utteranceLocator
        navigator.go(locator)
        withTimeoutOrNull(1500) { navigator.location.first { it.utteranceLocator != before } }
    }

    fun go(locator: Locator, snippet: String? = null, play: Boolean = false, near: Boolean = false, fallback: Locator? = null) {
        val navigator = _session.value?.navigator ?: return
        goJob?.cancel()
        goJob = scope.launch {
            val playing = navigator.playback.value.playWhenReady || play
            _seeking.value = true
            try {
                if (playing) halt(navigator)
                jump(navigator, locator)
                val found = align(navigator, snippet, locator, if (near) 80 else 2000)
                if (!found && fallback != null && !snippet.isNullOrBlank()) {
                    jump(navigator, fallback)
                    align(navigator, snippet, fallback)
                }
            } finally {
                _seeking.value = false
                if (playing) navigator.play()
            }
        }
    }

    private var book: Publication? = null

    fun jumpTo(page: Locator, sentence: String, prog: Double, fallback: Locator?) {
        val navigator = _session.value?.navigator ?: return
        val pub = book ?: return
        goJob?.cancel()
        goJob = scope.launch {
            _seeking.value = true
            try {
                halt(navigator)
                val target = squash(sentence)
                val spot = withContext(Dispatchers.Default) { runCatching { locate(pub, page, target, prog) }.getOrNull() }
                if (spot != null) {
                    navigator.go(spot.locator)
                    waitText(navigator, 2500) { u -> u.isNotEmpty() && (u.contains(spot.first.take(16)) || spot.first.contains(u.take(16))) }
                    repeat(spot.index) {
                        if (!navigator.hasNextUtterance()) return@repeat
                        step(navigator, forward = true)
                    }
                    settle(navigator, target)
                } else if (fallback != null) {
                    jump(navigator, fallback)
                    align(navigator, sentence, fallback)
                }
            } finally {
                _seeking.value = false
                navigator.play()
            }
        }
    }

    private class Spot(val locator: Locator, val index: Int, val first: String)

    private fun same(u: String, target: String): Boolean {
        if (u.isEmpty() || target.isEmpty()) return false
        val key = target.take(20)
        return if (u.length >= key.length) u.contains(key) else target.startsWith(u)
    }

    private suspend fun halt(navigator: AndroidTtsNavigator) {
        navigator.pause()
        withTimeoutOrNull(800) { navigator.playback.first { !it.playWhenReady } }
        delay(60)
    }

    private suspend fun waitText(navigator: AndroidTtsNavigator, ms: Long, ok: (String) -> Boolean): Boolean =
        withTimeoutOrNull(ms) { navigator.location.first { ok(squash(it.utterance)) } } != null

    private suspend fun step(navigator: AndroidTtsNavigator, forward: Boolean): Boolean {
        val now = navigator.location.value.utteranceLocator
        if (forward) navigator.skipToNextUtterance() else navigator.skipToPreviousUtterance()
        return withTimeoutOrNull(900) { navigator.location.first { it.utteranceLocator != now } } != null
    }

    private suspend fun settle(navigator: AndroidTtsNavigator, target: String) {
        if (same(squash(navigator.location.value.utterance), target)) return
        var moved = 0
        while (moved < 12 && navigator.hasNextUtterance()) {
            if (!step(navigator, true)) break
            moved++
            if (same(squash(navigator.location.value.utterance), target)) return
        }
        repeat(moved) { step(navigator, false) }
        var back = 0
        while (back < 12 && navigator.hasPreviousUtterance()) {
            if (!step(navigator, false)) break
            back++
            if (same(squash(navigator.location.value.utterance), target)) return
        }
        repeat(back) { step(navigator, true) }
    }

    private suspend fun locate(pub: Publication, page: Locator, target: String, prog: Double): Spot? {
        if (target.length < 2) return null
        val key = target.take(20)
        val home = place(page.href.toString())
        val link = pub.readingOrder.firstOrNull { place(it.url().toString()) == home } ?: return null
        val base = pub.locatorFromLink(link) ?: return null
        val content = pub.findService(org.readium.r2.shared.publication.services.content.ContentService::class)
            ?.content(base.copyWithLocations(progression = 0.0)) ?: return null
        val iterator = content.iterator()
        val hits = mutableListOf<Pair<Int, org.readium.r2.shared.publication.services.content.Content.TextElement>>()
        var count = 0
        while (count < 20_000 && iterator.hasNext()) {
            val element = iterator.next()
            if (place(element.locator.href.toString()) != home) break
            if (element is org.readium.r2.shared.publication.services.content.Content.TextElement) {
                val text = squash(element.segments.joinToString("") { it.text })
                if (text.contains(key) || (key.length < 20 && text.endsWith(key))) hits += count to element
            }
            count++
        }
        val total = count.coerceAtLeast(1)
        val pick = hits.minByOrNull { kotlin.math.abs(it.first.toDouble() / total - prog) }?.second ?: return null
        val tokenizer = org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer(org.readium.r2.shared.util.tokenizer.TextUnit.Sentence, pub.metadata.language)
        var index = 0
        var first: String? = null
        for (segment in pick.segments) {
            for (range in tokenizer.tokenize(segment.text)) {
                val part = squash(segment.text.substring(range))
                if (part.isEmpty()) continue
                if (first == null) first = part
                if (same(part, target)) return Spot(pick.locator, index, first)
                index++
            }
        }
        return Spot(pick.locator, 0, first ?: key)
    }

    private fun squash(t: String): String = buildString(t.length) {
        t.forEach { c ->
            if (!c.isWhitespace() && c != '\u00AD') append(if (c == 'ё' || c == 'Ё') 'е' else c.lowercaseChar())
        }
    }

    private fun fits(utterance: String, page: String): Boolean {
        val u = squash(utterance)
        if (u.isEmpty()) return false
        if (page.length >= 20 && u.contains(page.take(20))) return true
        val top = minOf(u.length, page.length)
        for (k in top downTo 8) if (u.endsWith(page.substring(0, k))) return true
        return u.length < 8 && page.startsWith(u)
    }

    private var order: List<String> = emptyList()

    private fun place(href: String): String =
        android.net.Uri.decode(href.substringBefore('#').substringBefore('?')).trimStart('/')

    private suspend fun align(navigator: AndroidTtsNavigator, snippet: String?, start: Locator?, limit: Int = 2000): Boolean {
        val target = start ?: return false
        val home = place(target.href.toString())
        val page = snippet?.let { squash(it).take(48) }?.takeIf { it.length >= 8 }
        if (enter(navigator, home, page, limit)) return true
        if (page == null) return false
        jump(navigator, target)
        enter(navigator, home, null, limit)
        return false
    }

    private suspend fun enter(navigator: AndroidTtsNavigator, home: String, page: String?, limit: Int = 2000): Boolean {
        val homeIndex = order.indexOf(home)
        val deadline = android.os.SystemClock.uptimeMillis() + 4000
        var steps = 0
        var inside = false
        while (steps < limit && android.os.SystemClock.uptimeMillis() < deadline) {
            val now = navigator.location.value
            val here = place(now.utteranceLocator.href.toString())
            if (here == home) {
                inside = true
                if (page == null || fits(now.utterance, page)) return true
            } else {
                if (inside) return false
                val index = order.indexOf(here)
                if (homeIndex < 0 || index < 0 || index > homeIndex) return false
            }
            if (!navigator.hasNextUtterance()) return false
            navigator.skipToNextUtterance()
            withTimeoutOrNull(700) { navigator.location.first { it.utteranceLocator != now.utteranceLocator } } ?: return false
            steps++
        }
        return false
    }

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

    private val wakeLock by lazy {
        (app.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "WikiFlerty:ReadAloud")
            .apply { setReferenceCounted(false) }
    }

    private fun wake(on: Boolean) {
        runCatching {
            if (on) wakeLock.acquire(6 * 60 * 60 * 1000L) else if (wakeLock.isHeld) wakeLock.release()
        }
    }

    fun stop() {
        wake(false)
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
