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
import org.readium.r2.navigator.preferences.FontFamily
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
    Sepia("sepia", "Сепия", 0xFF231D16, 0xFFE0D3BC, ReadiumTheme.DARK),
    Light("light", "Светлая", 0xFFF6F3EC, 0xFF2A2724, ReadiumTheme.LIGHT),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Dark
    }
}

/** A font the reader can use. Bundled fonts are shown only when their files are in the app. */
enum class ReaderFont(val id: String, val title: String, val family: FontFamily?, val assetFile: String?) {
    Literata("literata", "Literata", FontFamily("Literata"), "Literata.ttf"),
    PtSerif("ptserif", "PT Serif", FontFamily("PT Serif"), "PTSerif-Regular.ttf"),
    Serif("serif", "С засечками", FontFamily.SERIF, null),
    Sans("sans", "Без засечек", FontFamily.SANS_SERIF, null),
    Book("book", "Как в книге", null, null),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Serif

        /** Fonts available on this build: system ones always, bundled ones if their files exist. */
        fun available(context: Context): List<ReaderFont> {
            val bundled = runCatching { context.assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
            return entries.filter { it.assetFile == null || it.assetFile in bundled }
        }
    }
}

/** Buttons that can be shown on the bottom reading panel. "Опции" is always there. */
enum class QuickButton(val id: String, val title: String) {
    Toc("toc", "Главы"),
    Font("font", "Шрифт"),
    Theme("theme", "Тема"),
    Scroll("scroll", "Листание"),
    Notes("notes", "Заметки"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
        val defaults = listOf(Toc, Font, Theme, Notes)
    }
}

/** All reading options. Saved to reader_settings.json; later also part of sync and export. */
data class ReaderSettings(
    val font: String = ReaderFont.Literata.id,
    /** Text size as a factor: 1.0 = 100%. */
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.5,
    /** Side margins as a factor: 1.0 = normal. */
    val margins: Double = 1.0,
    val bold: Boolean = false,
    val justify: Boolean = true,
    val hyphens: Boolean = true,
    val theme: String = PageTheme.Dark.id,
    /** Continuous scroll ("Лента") instead of pages. */
    val scroll: Boolean = false,
    val animatedTurns: Boolean = true,
    val edgeTaps: Boolean = true,
    val volumeKeys: Boolean = false,
    /** Show progress as pages instead of percent. */
    val progressInPages: Boolean = false,
    val quickButtons: List<String> = QuickButton.defaults.map { it.id },
) {
    val pageTheme: PageTheme get() = PageTheme.of(theme)

    fun toEpubPreferences(availableFonts: List<ReaderFont>): EpubPreferences {
        val chosen = ReaderFont.of(font).takeIf { it in availableFonts } ?: ReaderFont.Serif
        val page = pageTheme
        return EpubPreferences(
            fontFamily = chosen.family,
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
        put("version", 1)
        put("font", font)
        put("fontSize", fontSize)
        put("lineHeight", lineHeight)
        put("margins", margins)
        put("bold", bold)
        put("justify", justify)
        put("hyphens", hyphens)
        put("theme", theme)
        put("scroll", scroll)
        put("animatedTurns", animatedTurns)
        put("edgeTaps", edgeTaps)
        put("volumeKeys", volumeKeys)
        put("progressInPages", progressInPages)
        put("quickButtons", JSONArray(quickButtons))
    }

    companion object {
        fun fromJson(json: JSONObject): ReaderSettings {
            val d = ReaderSettings()
            val quick = json.optJSONArray("quickButtons")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { QuickButton.of(it) != null } }
            } ?: d.quickButtons
            return ReaderSettings(
                font = json.optString("font", d.font),
                fontSize = json.optDouble("fontSize", d.fontSize).coerceIn(0.5, 2.5),
                lineHeight = json.optDouble("lineHeight", d.lineHeight).coerceIn(1.0, 2.0),
                margins = json.optDouble("margins", d.margins).coerceIn(0.0, 4.0),
                bold = json.optBoolean("bold", d.bold),
                justify = json.optBoolean("justify", d.justify),
                hyphens = json.optBoolean("hyphens", d.hyphens),
                theme = json.optString("theme", d.theme),
                scroll = json.optBoolean("scroll", d.scroll),
                animatedTurns = json.optBoolean("animatedTurns", d.animatedTurns),
                edgeTaps = json.optBoolean("edgeTaps", d.edgeTaps),
                volumeKeys = json.optBoolean("volumeKeys", d.volumeKeys),
                progressInPages = json.optBoolean("progressInPages", d.progressInPages),
                quickButtons = quick,
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
