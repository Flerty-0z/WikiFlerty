package com.flerty.wikiflerty.reader

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme

enum class PageTheme(
    val id: String,
    val title: String,
    val background: Long,
    val text: Long,
    val readiumBase: ReadiumTheme,
) {
    Dark("dark", "Тёмная", 0xFF101113, 0xFFD6D4CE, ReadiumTheme.DARK),
    Black("black", "Чёрная", 0xFF000000, 0xFFC2C2CA, ReadiumTheme.DARK),
    Night("night", "Ночь", 0xFF0E1420, 0xFFC9D1E0, ReadiumTheme.DARK),
    Sepia("sepia", "Сепия", 0xFF231D16, 0xFFE0D3BC, ReadiumTheme.DARK),
    Paper("paper", "Бумага", 0xFFEFE6D2, 0xFF3B3328, ReadiumTheme.LIGHT),
    Light("light", "Светлая", 0xFFF6F3EC, 0xFF2A2724, ReadiumTheme.LIGHT),
    ;

    val isLight: Boolean get() = readiumBase == ReadiumTheme.LIGHT

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Dark
    }
}

enum class PageAnimation(val id: String, val title: String) {
    Slide("slide", "Сдвиг"),
    Fade("fade", "Плавно"),
    Cover("cover", "Накрытие"),
    None("none", "Нет"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Slide
    }
}

enum class ScreenOrientation(val id: String, val title: String) {
    Auto("auto", "Авто"),
    Portrait("portrait", "Книжный"),
    Landscape("landscape", "Альбомный"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Auto
    }
}

enum class QuickButton(val id: String, val title: String) {
    Toc("toc", "Главы"),
    Font("font", "Шрифт"),
    Theme("theme", "Тема"),
    Scroll("scroll", "Листание"),
    Screen("screen", "Экран"),
    Notes("notes", "Заметки"),
    Bookmarks("bookmarks", "Закладки"),
    Listen("listen", "Слушать"),
    Search("search", "Поиск"),
    Auto("auto", "Авто"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
        val defaults = listOf(Toc, Font, Theme, Notes)
        const val MAX = 5
    }
}

data class ReaderSettings(
    val font: String = "literata",
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.5,
    val margins: Double = 1.0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val justify: Boolean = true,
    val hyphens: Boolean = true,
    val columns: String = "auto",
    val theme: String = PageTheme.Dark.id,
    val scroll: Boolean = false,
    val pageAnimation: String = PageAnimation.Slide.id,
    val volumeKeys: Boolean = false,
    val edgeTaps: Boolean = true,
    val edgeZone: Double = 0.3,
    val leftTapForward: Boolean = false,
    val brightnessSwipe: Boolean = true,
    val brightness: Double = -1.0,
    val keepScreenOn: Boolean = true,
    val orientation: String = ScreenOrientation.Auto.id,
    val fullscreen: Boolean = true,
    val showFlag: Boolean = true,
    val progressInPages: Boolean = false,
    val quickButtons: List<String> = QuickButton.defaults.map { it.id },
    val sounds: Boolean = true,
    val soundVolume: Double = 0.35,
    val haptics: Boolean = true,
    val markerColor: Long = 0xFFE9C84A,
    val noteColors: List<Long> = com.flerty.wikiflerty.data.NoteColors.presets.map { it.second },
    val ttsEngine: String = "",
    val ttsVoice: String = "",
    val ttsVoiceLanguage: String = "",
    val ttsSpeed: Double = 1.0,
    val ttsPitch: Double = 1.0,
    val ttsColor: Long = 0xFFF2C46B,
    val ttsFollow: Boolean = true,
    val autoNight: Boolean = false,
    val nightTheme: String = PageTheme.Black.id,
    val nightFrom: Int = 22 * 60,
    val nightTo: Int = 7 * 60,
    val autoSpeed: Int = 4,
) {
    val dayTheme: PageTheme get() = PageTheme.of(theme)
    val isNightNow: Boolean
        get() {
            if (!autoNight || nightFrom == nightTo) return false
            val now = java.util.Calendar.getInstance()
            val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            return if (nightFrom < nightTo) minutes in nightFrom until nightTo else minutes >= nightFrom || minutes < nightTo
        }
    val pageTheme: PageTheme get() = if (isNightNow) PageTheme.of(nightTheme) else PageTheme.of(theme)
    val animation: PageAnimation get() = PageAnimation.of(pageAnimation)

    fun toEpubPreferences(fonts: List<FontEntry>): EpubPreferences {
        val entry = FontCatalog.find(fonts, font)
        val page = pageTheme
        return EpubPreferences(
            fontFamily = entry.readiumFamily(italic),
            fontSize = fontSize,
            fontWeight = if (bold) 1.5 else null,
            lineHeight = lineHeight,
            pageMargins = margins,
            textAlign = if (justify) TextAlign.JUSTIFY else TextAlign.START,
            hyphens = hyphens,
            publisherStyles = false,
            theme = page.readiumBase,
            backgroundColor = Color(page.background.toInt()),
            textColor = Color(page.text.toInt()),
            scroll = scroll,
            columnCount = when (columns) {
                "1" -> ColumnCount.ONE
                "2" -> ColumnCount.TWO
                else -> ColumnCount.AUTO
            },
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 2)
        put("font", font)
        put("fontSize", fontSize)
        put("lineHeight", lineHeight)
        put("margins", margins)
        put("bold", bold)
        put("italic", italic)
        put("justify", justify)
        put("hyphens", hyphens)
        put("columns", columns)
        put("theme", theme)
        put("scroll", scroll)
        put("pageAnimation", pageAnimation)
        put("volumeKeys", volumeKeys)
        put("edgeTaps", edgeTaps)
        put("edgeZone", edgeZone)
        put("leftTapForward", leftTapForward)
        put("brightnessSwipe", brightnessSwipe)
        put("brightness", brightness)
        put("keepScreenOn", keepScreenOn)
        put("orientation", orientation)
        put("fullscreen", fullscreen)
        put("showFlag", showFlag)
        put("progressInPages", progressInPages)
        put("quickButtons", JSONArray(quickButtons))
        put("sounds", sounds)
        put("soundVolume", soundVolume)
        put("haptics", haptics)
        put("markerColor", String.format("#%08X", markerColor))
        put("noteColors", JSONArray(noteColors.map { String.format("#%08X", it) }))
        put("ttsEngine", ttsEngine)
        put("ttsVoice", ttsVoice)
        put("ttsVoiceLanguage", ttsVoiceLanguage)
        put("ttsSpeed", ttsSpeed)
        put("ttsPitch", ttsPitch)
        put("ttsColor", String.format("#%08X", ttsColor))
        put("ttsFollow", ttsFollow)
        put("autoNight", autoNight)
        put("nightTheme", nightTheme)
        put("nightFrom", nightFrom)
        put("nightTo", nightTo)
        put("autoSpeed", autoSpeed)
    }

    companion object {
        fun fromJson(json: JSONObject): ReaderSettings {
            val d = ReaderSettings()
            val quick = json.optJSONArray("quickButtons")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { QuickButton.of(it) != null } }
            } ?: d.quickButtons
            val animation = when {
                json.has("pageAnimation") -> json.optString("pageAnimation")
                json.has("animatedTurns") && !json.optBoolean("animatedTurns") -> PageAnimation.None.id
                else -> d.pageAnimation
            }
            return ReaderSettings(
                font = json.optString("font", d.font),
                fontSize = json.optDouble("fontSize", d.fontSize).coerceIn(0.5, 2.5),
                lineHeight = json.optDouble("lineHeight", d.lineHeight).coerceIn(1.0, 2.0),
                margins = json.optDouble("margins", d.margins).coerceIn(0.0, 4.0),
                bold = json.optBoolean("bold", d.bold),
                italic = json.optBoolean("italic", d.italic),
                justify = json.optBoolean("justify", d.justify),
                hyphens = json.optBoolean("hyphens", d.hyphens),
                columns = json.optString("columns", d.columns).takeIf { it in listOf("auto", "1", "2") } ?: d.columns,
                theme = json.optString("theme", d.theme),
                scroll = json.optBoolean("scroll", d.scroll),
                pageAnimation = animation,
                volumeKeys = json.optBoolean("volumeKeys", d.volumeKeys),
                edgeTaps = json.optBoolean("edgeTaps", d.edgeTaps),
                edgeZone = json.optDouble("edgeZone", d.edgeZone).coerceIn(0.1, 0.45),
                leftTapForward = json.optBoolean("leftTapForward", d.leftTapForward),
                brightnessSwipe = json.optBoolean("brightnessSwipe", d.brightnessSwipe),
                brightness = json.optDouble("brightness", d.brightness),
                keepScreenOn = json.optBoolean("keepScreenOn", d.keepScreenOn),
                orientation = json.optString("orientation", d.orientation),
                fullscreen = json.optBoolean("fullscreen", d.fullscreen),
                showFlag = json.optBoolean("showFlag", d.showFlag),
                progressInPages = json.optBoolean("progressInPages", d.progressInPages),
                quickButtons = quick,
                sounds = json.optBoolean("sounds", d.sounds),
                soundVolume = json.optDouble("soundVolume", d.soundVolume).coerceIn(0.05, 1.0),
                haptics = json.optBoolean("haptics", d.haptics),
                markerColor = runCatching { json.getString("markerColor").removePrefix("#").toLong(16) }
                    .getOrDefault(d.markerColor),
                noteColors = json.optJSONArray("noteColors")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        runCatching { arr.getString(i).removePrefix("#").toLong(16) }.getOrNull()
                    }
                }?.takeIf { it.isNotEmpty() } ?: d.noteColors,
                ttsEngine = json.optString("ttsEngine", d.ttsEngine),
                ttsVoice = json.optString("ttsVoice", d.ttsVoice),
                ttsVoiceLanguage = json.optString("ttsVoiceLanguage", d.ttsVoiceLanguage),
                ttsSpeed = json.optDouble("ttsSpeed", d.ttsSpeed).coerceIn(0.3, 4.0),
                ttsPitch = json.optDouble("ttsPitch", d.ttsPitch).coerceIn(0.3, 3.0),
                ttsColor = runCatching { json.getString("ttsColor").removePrefix("#").toLong(16) }.getOrDefault(d.ttsColor),
                ttsFollow = json.optBoolean("ttsFollow", d.ttsFollow),
                autoNight = json.optBoolean("autoNight", d.autoNight),
                nightTheme = json.optString("nightTheme", d.nightTheme),
                nightFrom = json.optInt("nightFrom", d.nightFrom).coerceIn(0, 24 * 60 - 1),
                nightTo = json.optInt("nightTo", d.nightTo).coerceIn(0, 24 * 60 - 1),
                autoSpeed = json.optInt("autoSpeed", d.autoSpeed).coerceIn(1, 10),
            )
        }
    }
}

class ReaderSettingsStore(context: Context) {
    private val file = File(context.filesDir, "reader_settings.json")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    var onChanged: (() -> Unit)? = null

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        if (write(transform(_settings.value))) onChanged?.invoke()
    }

    fun replace(settings: ReaderSettings) {
        write(settings)
    }

    private fun write(updated: ReaderSettings): Boolean {
        if (updated == _settings.value) return false
        _settings.value = updated
        runCatching {
            val tmp = File(file.parentFile, "reader_settings.json.tmp")
            tmp.writeText(updated.toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(updated.toJson().toString())
                tmp.delete()
            }
        }
        return true
    }

    private fun load(): ReaderSettings = runCatching {
        if (file.exists()) ReaderSettings.fromJson(JSONObject(file.readText())) else ReaderSettings()
    }.getOrElse { ReaderSettings() }
}

object AutoScroll {
    private val pageSeconds = listOf(60, 45, 35, 28, 22, 18, 14, 11, 8, 6)
    private val pixels = listOf(12, 18, 25, 33, 42, 52, 64, 78, 95, 115)

    fun pageDelayMs(speed: Int): Long = pageSeconds[(speed - 1).coerceIn(0, 9)] * 1000L
    fun pixelsPerSecond(speed: Int): Int = pixels[(speed - 1).coerceIn(0, 9)]
    fun label(speed: Int, scroll: Boolean): String =
        if (scroll) "Скорость $speed" else "${pageSeconds[(speed - 1).coerceIn(0, 9)]} с/стр"
}
