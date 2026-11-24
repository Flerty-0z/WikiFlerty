package com.flerty.wikiflerty.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.flerty.wikiflerty.App
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioState(
    val bookId: String? = null,
    val track: Int = 0,
    val positionMs: Long = 0,
    val playing: Boolean = false,
    val ready: Boolean = false,
    val speed: Float = 1f,
    val sleepAt: Long = 0,
    val sleepChapter: Boolean = false,
    val error: String? = null,
)

class AudioPlayer(private val app: App) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var completed = false
    private var loadedTrack = -1
    private var ticker: Job? = null
    private var fading: Job? = null
    private var lastSave = 0L
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focus: AudioFocusRequest? = null
    private var noisyOn = false
    var session: SessionPlayer? = null
        private set

    val book: Audiobook? get() = app.audiobooks.find(_state.value.bookId)

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 0.3f
            AudioManager.AUDIOFOCUS_GAIN -> player?.volume = 1f
        }
    }

    fun open(id: String, play: Boolean = true) {
        val b = app.audiobooks.find(id) ?: return
        if (_state.value.bookId != id) {
            saveNow()
            release()
            _state.value = AudioState(bookId = id, track = b.track, positionMs = b.positionMs, speed = b.speed)
        }
        if (play) play()
        invalidate()
    }

    fun positionNow(): Long = runCatching { player?.takeIf { loadedTrack == _state.value.track && _state.value.ready }?.currentPosition }.getOrNull() ?: _state.value.positionMs

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun create(): ExoPlayer {
        val extractors = DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING).setConstantBitrateSeekingEnabled(true)
        val attrs = androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH).build()
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(app, extractors))
            .setAudioAttributes(attrs, false)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(false)
            .build()
    }

    private fun load(track: Int, at: Long, autoplay: Boolean) {
        val b = book ?: return
        val t = b.tracks.getOrNull(track) ?: return
        releasePlayer()
        loadedTrack = track
        completed = false
        _state.value = _state.value.copy(track = track, positionMs = at, ready = false, error = null)
        val mp = try {
            create()
        } catch (e: Throwable) {
            _state.value = _state.value.copy(playing = false, error = "Не получилось открыть файл")
            invalidate()
            return
        }
        player = mp
        mp.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (player !== mp) return
                when (state) {
                    Player.STATE_READY -> if (!_state.value.ready) {
                        mp.setPlaybackSpeed(_state.value.speed)
                        _state.value = _state.value.copy(ready = true)
                        if (autoplay || _state.value.playing) startNow()
                        invalidate()
                    }
                    Player.STATE_ENDED -> if (!completed) {
                        completed = true
                        finished(track, t.durationMs)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (player !== mp) return
                _state.value = _state.value.copy(playing = false, ready = false, error = "Не получилось открыть файл. Возможно, он перемещён или удалён")
                stopTicker()
                invalidate()
            }
        })
        try {
            mp.setMediaItem(MediaItem.fromUri(Uri.parse(t.uri)), at.coerceAtLeast(0))
            mp.prepare()
        } catch (e: Exception) {
            _state.value = _state.value.copy(playing = false, error = "Нет доступа к файлу. Добавь аудиокнигу заново")
            invalidate()
        }
    }

    private fun finished(track: Int, durationMs: Long) {
        val bk = book ?: return
        if (_state.value.sleepChapter && bk.chapters.count { it.track == track } <= 1) {
            stopForSleep()
            return
        }
        if (track + 1 < bk.tracks.size) {
            load(track + 1, 0, true)
        } else {
            player?.playWhenReady = false
            _state.value = _state.value.copy(playing = false, positionMs = durationMs)
            saveNow()
            stopTicker()
            invalidate()
        }
    }

    fun play() {
        val s = _state.value
        if (s.bookId == null) return
        runCatching { app.readAloud.stop() }
        if (player == null || loadedTrack != s.track) {
            _state.value = s.copy(playing = true)
            load(s.track, s.positionMs, true)
            return
        }
        if (s.ready) startNow() else _state.value = s.copy(playing = true)
    }

    private fun startNow() {
        val mp = player ?: return
        if (!requestFocus()) return
        if (!noisyOn) {
            runCatching { app.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) }
            noisyOn = true
        }
        fading?.cancel()
        mp.volume = 1f
        mp.setPlaybackSpeed(_state.value.speed)
        if (completed) {
            completed = false
            mp.seekTo(0)
        }
        mp.playWhenReady = true
        _state.value = _state.value.copy(playing = true)
        app.audiobooks.update(_state.value.bookId ?: return) { it.copy(lastPlayedAt = System.currentTimeMillis()) }
        startService()
        startTicker()
        invalidate()
    }

    fun pause() {
        runCatching { player?.playWhenReady = false }
        _state.value = _state.value.copy(playing = false, positionMs = positionNow())
        fading?.cancel()
        abandonFocus()
        saveNow()
        stopTicker()
        invalidate()
    }

    fun toggle() = if (_state.value.playing) pause() else play()

    fun seekTo(track: Int, ms: Long) {
        val b = book ?: return
        val t = track.coerceIn(0, b.tracks.lastIndex)
        val at = ms.coerceIn(0, b.tracks[t].durationMs)
        if (t != loadedTrack || player == null) {
            load(t, at, _state.value.playing)
        } else {
            completed = false
            runCatching { player?.seekTo(at) }
            _state.value = _state.value.copy(positionMs = at)
        }
        saveNow()
        invalidate()
    }

    fun seekGlobal(ms: Long) {
        val b = book ?: return
        val (t, at) = b.locate(ms)
        seekTo(t, at)
    }

    fun skip(deltaMs: Long) {
        val b = book ?: return
        seekGlobal(b.offsetOf(_state.value.track) + positionNow() + deltaMs)
    }

    fun chapter(index: Int) {
        val b = book ?: return
        val c = b.chapters.getOrNull(index) ?: return
        seekTo(c.track, c.startMs)
    }

    fun nextChapter() {
        val b = book ?: return
        val i = b.chapterAt(_state.value.track, positionNow())
        if (i + 1 < b.chapters.size) chapter(i + 1)
    }

    fun prevChapter() {
        val b = book ?: return
        val i = b.chapterAt(_state.value.track, positionNow())
        val into = b.offsetOf(_state.value.track) + positionNow() - b.chapterStart(i)
        chapter(if (into > 4_000 || i == 0) i else i - 1)
    }

    fun setSpeed(speed: Float) {
        val v = speed.coerceIn(0.5f, 3f)
        _state.value = _state.value.copy(speed = v)
        player?.setPlaybackSpeed(v)
        _state.value.bookId?.let { id -> app.audiobooks.update(id) { it.copy(speed = v) } }
        invalidate()
    }

    fun sleep(minutes: Int) {
        _state.value = _state.value.copy(
            sleepAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0,
            sleepChapter = minutes < 0,
        )
        if (_state.value.playing) startTicker()
    }

    fun markNear(book: Audiobook, track: Int, ms: Long): AudioMark? =
        book.marks.firstOrNull { it.track == track && kotlin.math.abs(it.ms - ms) <= 4_000 }

    fun toggleMark(): Boolean {
        val s = _state.value
        val id = s.bookId ?: return false
        val b = book ?: return false
        val at = positionNow()
        val near = markNear(b, s.track, at)
        app.audiobooks.update(id) { bk ->
            val list = if (near != null) bk.marks - near else bk.marks + AudioMark(s.track, at, System.currentTimeMillis())
            bk.copy(marks = list.sortedWith(compareBy({ it.track }, { it.ms })))
        }
        return near == null
    }

    fun close() {
        pause()
        release()
        _state.value = AudioState()
        invalidate()
        AudioService.release()
    }

    private fun stopForSleep() {
        fading?.cancel()
        _state.value = _state.value.copy(sleepAt = 0, sleepChapter = false)
        pause()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var lastChapter = -1
            while (isActive) {
                delay(500)
                val s = _state.value
                if (!s.playing) continue
                val pos = positionNow()
                _state.value = s.copy(positionMs = pos)
                val b = book
                if (b != null) {
                    val ch = b.chapterAt(s.track, pos)
                    if (s.sleepChapter && lastChapter >= 0 && ch != lastChapter) {
                        stopForSleep()
                        continue
                    }
                    if (ch != lastChapter) {
                        lastChapter = ch
                        invalidate()
                    }
                }
                if (s.sleepAt > 0) {
                    val left = s.sleepAt - System.currentTimeMillis()
                    if (left <= 0) {
                        stopForSleep()
                        continue
                    }
                    if (left < 10_000) player?.let { it.volume = (left / 10_000f).coerceIn(0.05f, 1f) }
                }
                if (System.currentTimeMillis() - lastSave > 5_000) saveNow()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun saveNow() {
        val s = _state.value
        val id = s.bookId ?: return
        val pos = positionNow()
        lastSave = System.currentTimeMillis()
        app.audiobooks.update(id) { it.copy(track = s.track, positionMs = pos) }
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusListener).build().also { focus = it }
            audio.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus?.let { audio.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusListener)
        }
        if (noisyOn) {
            runCatching { app.unregisterReceiver(noisy) }
            noisyOn = false
        }
    }

    private fun releasePlayer() {
        player?.let { mp -> runCatching { mp.release() } }
        player = null
        loadedTrack = -1
    }

    private fun release() {
        stopTicker()
        releasePlayer()
    }

    private fun startService() {
        if (session == null) session = SessionPlayer(this)
        runCatching { app.startService(Intent(app, AudioService::class.java)) }
    }

    private fun invalidate() {
        session?.refresh()
    }

    fun coverBytes(id: String): ByteArray? = runCatching { app.audiobooks.cover(id).takeIf { it.exists() }?.readBytes() }.getOrNull()
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class SessionPlayer(private val engine: AudioPlayer) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var art: Pair<String, ByteArray?>? = null

    fun refresh() = invalidateState()

    override fun getState(): State {
        val s = engine.state.value
        val b = engine.book
        if (b == null) {
            return State.Builder()
                .setAvailableCommands(Player.Commands.EMPTY)
                .setPlaybackState(Player.STATE_IDLE)
                .build()
        }
        val cover = art?.takeIf { it.first == b.id }?.second ?: engine.coverBytes(b.id).also { art = b.id to it }
        val chapter = b.chapters.getOrNull(b.chapterAt(s.track, s.positionMs))
        val items = b.tracks.mapIndexed { i, t ->
            val meta = MediaMetadata.Builder()
                .setTitle(if (i == s.track && chapter != null) chapter.title else t.title)
                .setArtist(b.title)
                .setAlbumTitle(b.title)
                .setAlbumArtist(b.author)
                .apply { if (cover != null) setArtworkData(cover, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                .build()
            MediaItemData.Builder("t$i")
                .setMediaItem(MediaItem.Builder().setMediaId("t$i").setMediaMetadata(meta).build())
                .setDurationUs(t.durationMs * 1000)
                .setIsSeekable(true)
                .build()
        }
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                ).build(),
            )
            .setPlaylist(items)
            .setCurrentMediaItemIndex(s.track.coerceIn(0, items.lastIndex))
            .setContentPositionMs { engine.positionNow() }
            .setPlayWhenReady(s.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (s.ready || !s.playing) Player.STATE_READY else Player.STATE_BUFFERING)
            .setPlaybackParameters(PlaybackParameters(s.speed))
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_BACK -> engine.skip(-15_000)
            Player.COMMAND_SEEK_FORWARD -> engine.skip(30_000)
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> engine.nextChapter()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> engine.prevChapter()
            else -> engine.seekTo(mediaItemIndex, positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        engine.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
