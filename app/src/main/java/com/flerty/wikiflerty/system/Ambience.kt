package com.flerty.wikiflerty.system

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.flerty.wikiflerty.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AmbientSound(val id: String, val title: String, val emoji: String, val res: Int) {
    Rain("rain", "Дождь", "🌧️", R.raw.amb_rain),
    Storm("storm", "Гроза", "⛈️", R.raw.amb_storm),
    Fire("fire", "Камин", "🔥", R.raw.amb_fire),
    Cafe("cafe", "Кофейня", "☕", R.raw.amb_cafe),
    Waves("waves", "Волны", "🌊", R.raw.amb_waves),
    Forest("forest", "Лес", "🌲", R.raw.amb_forest),
    Wind("wind", "Ветер", "💨", R.raw.amb_wind),
    Stream("stream", "Ручей", "🏞️", R.raw.amb_stream),
    Train("train", "Поезд", "🚂", R.raw.amb_train),
    Clock("clock", "Часы", "🕰️", R.raw.amb_clock),
    City("city", "Ночной город", "🌃", R.raw.amb_city),
    Noise("noise", "Белый шум", "🤍", R.raw.amb_noise);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

data class AmbientPreset(val title: String, val emoji: String, val mix: Map<AmbientSound, Float>)

val AmbientPresets = listOf(
    AmbientPreset("Уютный вечер", "🛋️", mapOf(AmbientSound.Fire to 0.8f, AmbientSound.Rain to 0.35f, AmbientSound.Clock to 0.3f)),
    AmbientPreset("Библиотека", "📚", mapOf(AmbientSound.Clock to 0.45f, AmbientSound.Rain to 0.25f, AmbientSound.Noise to 0.15f)),
    AmbientPreset("Шторм", "⛈️", mapOf(AmbientSound.Storm to 0.85f, AmbientSound.Wind to 0.45f)),
    AmbientPreset("Кофейня", "☕", mapOf(AmbientSound.Cafe to 0.7f, AmbientSound.Rain to 0.3f)),
    AmbientPreset("Лес у ручья", "🌿", mapOf(AmbientSound.Forest to 0.7f, AmbientSound.Stream to 0.5f)),
    AmbientPreset("Ночной поезд", "🌙", mapOf(AmbientSound.Train to 0.7f, AmbientSound.Rain to 0.3f)),
)

class AmbientPlayer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("ambience", Context.MODE_PRIVATE)
    private val players = mutableMapOf<AmbientSound, MediaPlayer>()
    private val fades = mutableMapOf<AmbientSound, Job>()

    private val _mix = MutableStateFlow<Map<AmbientSound, Float>>(emptyMap())
    val mix: StateFlow<Map<AmbientSound, Float>> = _mix.asStateFlow()

    private var master = 1f

    val saved: Map<AmbientSound, Float>
        get() = runCatching {
            val j = JSONObject(prefs.getString("mix", "{}") ?: "{}")
            j.keys().asSequence().mapNotNull { k -> AmbientSound.of(k)?.let { it to j.getDouble(k).toFloat() } }.toMap()
        }.getOrElse { emptyMap() }

    fun toggle(sound: AmbientSound) {
        if (sound in _mix.value) stop(sound) else start(sound, 0.6f)
    }

    fun setVolume(sound: AmbientSound, volume: Float) {
        if (sound !in _mix.value) return
        val v = volume.coerceIn(0f, 1f)
        _mix.value = _mix.value + (sound to v)
        players[sound]?.setVolume(v * master, v * master)
        persist()
    }

    fun preset(preset: AmbientPreset) = applyMix(preset.mix)

    fun applyMix(mix: Map<AmbientSound, Float>) {
        (_mix.value.keys - mix.keys).forEach { stop(it) }
        mix.forEach { (s, v) -> if (s in _mix.value) setVolume(s, v) else start(s, v) }
    }

    private fun start(sound: AmbientSound, volume: Float) {
        val player = runCatching {
            MediaPlayer.create(
                context,
                sound.res,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }.getOrNull() ?: return
        player.isLooping = true
        player.setVolume(0f, 0f)
        player.start()
        players[sound] = player
        _mix.value = _mix.value + (sound to volume)
        fade(sound, player, 0f, volume * master, 900) {}
        persist()
    }

    private fun stop(sound: AmbientSound, persist: Boolean = true) {
        val player = players.remove(sound) ?: return
        val from = (_mix.value[sound] ?: 0f) * master
        _mix.value = _mix.value - sound
        if (persist) persist()
        fade(sound, player, from, 0f, 600) { runCatching { player.stop(); player.release() } }
    }

    private fun fade(sound: AmbientSound, player: MediaPlayer, from: Float, to: Float, ms: Long, end: () -> Unit) {
        fades[sound]?.cancel()
        fades[sound] = scope.launch {
            val steps = 20
            for (i in 1..steps) {
                val v = from + (to - from) * i / steps
                runCatching { player.setVolume(v, v) }
                delay(ms / steps)
            }
            end()
        }
    }

    fun stopAll() {
        _mix.value.keys.toList().forEach { stop(it, persist = false) }
    }

    private fun persist() {
        val j = JSONObject()
        _mix.value.forEach { (s, v) -> j.put(s.id, v.toDouble()) }
        prefs.edit().putString("mix", j.toString()).apply()
    }
}
