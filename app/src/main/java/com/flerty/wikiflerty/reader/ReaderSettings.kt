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
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme

/** A page theme: background and text colors of the book. */
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

/** How a page turns on a tap or a volume key. */
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

/** Buttons that can be shown on the bottom reading panel. "Опции" is always there. */
enum class QuickButton(val id: String, val title: String) {
    Toc("toc", "Главы"),
    Font("font", "Шрифт"),
    Theme("theme", "Тема"),
    Scroll("scroll", "Листание"),
    Screen("screen", "Экран"),
    Notes("notes", "Заметки"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
        val defaults = listOf(Toc, Font, Theme, Notes)
        const val MAX = 5
    }
}

/** All reading options. Saved to reader_settings.json; later also part of sync and export. */
data class ReaderSettings(
    // Text
    val font: String = "literata",
    /** Text size as a factor: 1.0 = 100%. */
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.5,
    /** Side margins as a factor: 1.0 = normal. */
    val margins: Double = 1.0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val justify: Boolean = true,
    val hyphens: Boolean = true,
    // Theme
    val theme: String = PageTheme.Dark.id,
    // Page turning
    /** Continuous scroll ("Лента") instead of pages. */
    val scroll: Boolean = false,
    val pageAnimation: String = PageAnimation.Slide.id,
    val volumeKeys: Boolean = false,
    // Gestures
    val edgeTaps: Boolean = true,
    /** Width of each tap zone at the left/right edge, as a share of the screen. */
    val edgeZone: Double = 0.3,
    /** One-handed mode: the left edge also goes forward. */
    val leftTapForward: Boolean = false,
    val brightnessSwipe: Boolean = true,
    // Screen
    /** Screen brightness 0.05..1.0, or -1 to follow the system. */
    val brightness: Double = -1.0,
    val keepScreenOn: Boolean = true,
    val orientation: String = ScreenOrientation.Auto.id,
    val fullscreen: Boolean = true,
    val showFlag: Boolean = true,
    // Panel
    /** Show progress as pages instead of percent. */
    val progressInPages: Boolean = false,
    val quickButtons: List<String> = QuickButton.defaults.map { it.id },
    // Sound & feel
    val sounds: Boolean = true,
    val soundVolume: Double = 0.35,
    val haptics: Boolean = true,
) {
    val pageTheme: PageTheme get() = PageTheme.of(theme)
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
            // Our own layout settings win over the book's styles, otherwise most of them do nothing.
            publisherStyles = false,
            theme = page.readiumBase,
            backgroundColor = Color(page.background.toInt()),
            textColor = Color(page.text.toInt()),
            scroll = scroll,
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
    }

    companion object {
        fun fromJson(json: JSONObject): ReaderSettings {
            val d = ReaderSettings()
            val quick = json.optJSONArray("quickButtons")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { QuickButton.of(it) != null } }
            } ?: d.quickButtons
            // Version 1 had a simple on/off animation switch.
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
            )
        }
    }
}

class ReaderSettingsStore(context: Context) {
    private val file = File(context.filesDir, "reader_settings.json")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        runCatching {
            val tmp = File(file.parentFile, "reader_settings.json.tmp")
            tmp.writeText(updated.toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(updated.toJson().toString())
                tmp.delete()
            }
        }
    }

    private fun load(): ReaderSettings = runCatching {
        if (file.exists()) ReaderSettings.fromJson(JSONObject(file.readText())) else ReaderSettings()
    }.getOrElse { ReaderSettings() }
}
