package com.flerty.wikiflerty.reader

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import org.readium.r2.navigator.preferences.FontFamily

enum class FontCategory(val title: String) {
    Serif("С засечками"),
    Sans("Без засечек"),
    Mono("Моно"),
    Other("Другое"),
}

/**
 * A reading font.
 *
 * [css] is the family name the book page uses. Bundled fonts have their files in
 * assets/fonts (downloaded during the build, see app/fonts.txt).
 * A variable font has one file covering every weight.
 */
data class FontEntry(
    val id: String,
    val title: String,
    val category: FontCategory,
    val css: String?,
    val regular: String? = null,
    val italic: String? = null,
    val bold: String? = null,
    val variable: Boolean = false,
) {
    val bundled: Boolean get() = regular != null
    val supportsItalic: Boolean get() = italic != null

    /** Font family for the book page. Italic mode uses a separate family whose normal style is the italic file. */
    fun readiumFamily(italicMode: Boolean): FontFamily? = when {
        css == null -> null
        italicMode && supportsItalic -> FontFamily(italicFamilyName)
        else -> FontFamily(css)
    }

    val italicFamilyName: String get() = "$css Italic Mode"
}

object FontCatalog {
    private fun v(id: String, title: String, cat: FontCategory, file: String, italic: String? = null) =
        FontEntry(id, title, cat, css = title, regular = file, italic = italic, variable = true)

    private fun s(id: String, title: String, cat: FontCategory, base: String, italic: Boolean = true, bold: Boolean = true) =
        FontEntry(
            id, title, cat, css = title,
            regular = "$base-Regular.ttf",
            italic = if (italic) "$base-Italic.ttf" else null,
            bold = if (bold) "$base-Bold.ttf" else null,
        )

    val all: List<FontEntry> = listOf(
        // Serif
        v("literata", "Literata", FontCategory.Serif, "Literata.ttf", "Literata-Italic.ttf"),
        s("ptserif", "PT Serif", FontCategory.Serif, "PTSerif"),
        v("lora", "Lora", FontCategory.Serif, "Lora.ttf", "Lora-Italic.ttf"),
        v("ebgaramond", "EB Garamond", FontCategory.Serif, "EBGaramond.ttf", "EBGaramond-Italic.ttf"),
        v("sourceserif", "Source Serif 4", FontCategory.Serif, "SourceSerif4.ttf", "SourceSerif4-Italic.ttf"),
        v("alegreya", "Alegreya", FontCategory.Serif, "Alegreya.ttf", "Alegreya-Italic.ttf"),
        s("spectral", "Spectral", FontCategory.Serif, "Spectral"),
        s("oldstandard", "Old Standard TT", FontCategory.Serif, "OldStandard"),
        v("crimsonpro", "Crimson Pro", FontCategory.Serif, "CrimsonPro.ttf", "CrimsonPro-Italic.ttf"),
        s("ibmplexserif", "IBM Plex Serif", FontCategory.Serif, "IBMPlexSerif"),
        v("vollkorn", "Vollkorn", FontCategory.Serif, "Vollkorn.ttf", "Vollkorn-Italic.ttf"),
        s("tinos", "Tinos", FontCategory.Serif, "Tinos", bold = false),
        FontEntry("serif", "Системный с засечками", FontCategory.Serif, css = "serif"),
        // Sans
        v("onest", "Onest", FontCategory.Sans, "Onest.ttf"),
        v("opensans", "Open Sans", FontCategory.Sans, "OpenSans.ttf", "OpenSans-Italic.ttf"),
        v("rubik", "Rubik", FontCategory.Sans, "Rubik.ttf", "Rubik-Italic.ttf"),
        v("nunito", "Nunito", FontCategory.Sans, "Nunito.ttf", "Nunito-Italic.ttf"),
        v("manrope", "Manrope", FontCategory.Sans, "Manrope.ttf"),
        s("firasans", "Fira Sans", FontCategory.Sans, "FiraSans"),
        s("ptsans", "PT Sans", FontCategory.Sans, "PTSans"),
        FontEntry("sans", "Системный без засечек", FontCategory.Sans, css = "sans-serif"),
        // Mono
        v("jetbrainsmono", "JetBrains Mono", FontCategory.Mono, "JetBrainsMono.ttf", "JetBrainsMono-Italic.ttf"),
        FontEntry("ptmono", "PT Mono", FontCategory.Mono, css = "PT Mono", regular = "PTMono-Regular.ttf"),
        // Other
        FontEntry("book", "Как в книге", FontCategory.Other, css = null),
    )

    private const val DEFAULT_ID = "literata"

    /** Fonts that can be used on this build: system ones always, bundled ones if their files are there. */
    fun available(context: Context): List<FontEntry> {
        val files = runCatching { context.assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
        return all.map { entry ->
            // Drop optional styles whose files are missing, so the page never points at a missing file.
            entry.copy(
                italic = entry.italic?.takeIf { it in files },
                bold = entry.bold?.takeIf { it in files },
            )
        }.filter { it.regular == null || it.regular in files }
    }

    fun find(fonts: List<FontEntry>, id: String): FontEntry =
        fonts.firstOrNull { it.id == id }
            ?: fonts.firstOrNull { it.id == DEFAULT_ID }
            ?: fonts.first { it.id == "serif" }

    private val previewCache = mutableMapOf<String, ComposeFontFamily>()

    /** Compose font used to preview [entry] in the menus. */
    fun preview(context: Context, entry: FontEntry): ComposeFontFamily {
        val file = entry.regular ?: return when (entry.id) {
            "sans" -> ComposeFontFamily.SansSerif
            "book" -> ComposeFontFamily.Default
            else -> ComposeFontFamily.Serif
        }
        return previewCache.getOrPut(file) {
            runCatching {
                ComposeFontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(context.assets, "fonts/$file")))
            }.getOrDefault(ComposeFontFamily.Serif)
        }
    }
}
