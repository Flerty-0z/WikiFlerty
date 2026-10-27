package com.flerty.wikiflerty.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.flerty.wikiflerty.reader.FontCatalog
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A set of base colors for the whole interface. */
enum class UiBase(
    val id: String,
    val title: String,
    val background: Long,
    val panel: Long,
    val panelTop: Long,
    val panelBottom: Long,
    val border: Long,
    val edgeTop: Long,
    val field: Long,
    val line: Long,
) {
    Dark("dark", "Тёмная", 0xFF101113, 0xFF1F2125, 0xFF25282D, 0xFF1C1E22, 0xFF2C2F34, 0xFF454952, 0xFF16171A, 0xFF33363C),
    Graphite("graphite", "Графит", 0xFF141414, 0xFF222222, 0xFF292929, 0xFF1F1F1F, 0xFF323232, 0xFF4A4A4A, 0xFF191919, 0xFF3A3A3A),
    Ocean("ocean", "Океан", 0xFF0D1117, 0xFF161D27, 0xFF1B2431, 0xFF141B25, 0xFF243042, 0xFF3A4A63, 0xFF0F151E, 0xFF2A3546),
    Oled("oled", "OLED", 0xFF000000, 0xFF121214, 0xFF18181B, 0xFF0F0F11, 0xFF26262B, 0xFF3A3A42, 0xFF0A0A0C, 0xFF2A2A30),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Dark
    }
}

enum class ButtonLook(val id: String, val title: String) {
    Soft("soft", "Мягкие"),
    Outline("outline", "Контур"),
    Filled("filled", "Заливка"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Soft
    }
}

enum class BackgroundKind(val id: String, val title: String) {
    None("none", "Нет"),
    Image("image", "Картинка"),
    Video("video", "Видео"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: None
    }
}

/** Everything the user can customize in the look of the app. Saved to ui_style.json. */
data class UiStyle(
    val base: String = UiBase.Dark.id,
    val accent: Long = 0xFF8FB3FF,
    /** Corner rounding: 0 = square, 1 = normal, 1.6 = very round. */
    val corners: Float = 1f,
    val outline: Boolean = true,
    val buttons: String = ButtonLook.Soft.id,
    /** Font of the interface: an id from the font catalog, or "system". */
    val font: String = "system",
    val textScale: Float = 1f,
    val pressEffect: Boolean = true,
    val background: String = BackgroundKind.None.id,
    /** Darkening over the background picture/video, 0..0.9. */
    val backgroundDim: Float = 0.45f,
    /** Blur of the background picture, in dp (Android 12+). */
    val backgroundBlur: Float = 0f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("base", base)
        put("accent", String.format("#%08X", accent))
        put("corners", corners.toDouble())
        put("outline", outline)
        put("buttons", buttons)
        put("font", font)
        put("textScale", textScale.toDouble())
        put("pressEffect", pressEffect)
        put("background", background)
        put("backgroundDim", backgroundDim.toDouble())
        put("backgroundBlur", backgroundBlur.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): UiStyle {
            val d = UiStyle()
            return UiStyle(
                base = json.optString("base", d.base),
                accent = runCatching { json.getString("accent").removePrefix("#").toLong(16) }.getOrDefault(d.accent),
                corners = json.optDouble("corners", d.corners.toDouble()).toFloat().coerceIn(0f, 1.6f),
                outline = json.optBoolean("outline", d.outline),
                buttons = json.optString("buttons", d.buttons),
                font = json.optString("font", d.font),
                textScale = json.optDouble("textScale", d.textScale.toDouble()).toFloat().coerceIn(0.8f, 1.3f),
                pressEffect = json.optBoolean("pressEffect", d.pressEffect),
                background = json.optString("background", d.background),
                backgroundDim = json.optDouble("backgroundDim", d.backgroundDim.toDouble()).toFloat().coerceIn(0f, 0.9f),
                backgroundBlur = json.optDouble("backgroundBlur", d.backgroundBlur.toDouble()).toFloat().coerceIn(0f, 30f),
            )
        }
    }
}

/** Ready-made looks. */
data class StylePreset(val title: String, val base: UiBase, val accent: Long)

val StylePresets = listOf(
    StylePreset("Полночь", UiBase.Dark, 0xFF8FB3FF),
    StylePreset("Графит", UiBase.Graphite, 0xFFE8A33D),
    StylePreset("Изумруд", UiBase.Dark, 0xFF6FD3A6),
    StylePreset("Закат", UiBase.Graphite, 0xFFF28B6B),
    StylePreset("Аметист", UiBase.Ocean, 0xFFB79CFF),
    StylePreset("Бирюза", UiBase.Oled, 0xFF5FD4C0),
    StylePreset("Роза", UiBase.Dark, 0xFFF08FA8),
    StylePreset("Лёд", UiBase.Ocean, 0xFF7FD1F5),
)

/**
 * The current look, readable from anywhere in the UI.
 * It is Compose state, so every screen redraws itself the moment the user changes something.
 */
object AppStyle {
    var current by mutableStateOf(UiStyle())
        internal set

    val base: UiBase get() = UiBase.of(current.base)
    val buttons: ButtonLook get() = ButtonLook.of(current.buttons)
}

class StyleStore(context: Context) {
    private val file = File(context.filesDir, "ui_style.json")
    private val _style = MutableStateFlow(load())
    val style: StateFlow<UiStyle> = _style.asStateFlow()

    /** Background picture or video chosen by the user. */
    val backgroundImage = File(context.filesDir, "ui_background.jpg")
    val backgroundVideo = File(context.filesDir, "ui_background.mp4")

    init {
        AppStyle.current = _style.value
    }

    var onChanged: (() -> Unit)? = null

    fun update(transform: (UiStyle) -> UiStyle) {
        if (write(transform(_style.value))) onChanged?.invoke()
    }

    /** Sets the look received from another phone. */
    fun replace(style: UiStyle) {
        write(style)
    }

    private fun write(updated: UiStyle): Boolean {
        if (updated == _style.value) return false
        _style.value = updated
        AppStyle.current = updated
        runCatching { file.writeText(updated.toJson().toString()) }
        return true
    }

    private fun load(): UiStyle = runCatching {
        if (file.exists()) UiStyle.fromJson(JSONObject(file.readText())) else UiStyle()
    }.getOrElse { UiStyle() }
}

/** App colors, taken from the current [AppStyle]. */
object AppColors {
    private val base get() = AppStyle.base
    val Background: Color get() = Color(base.background)
    val Panel: Color get() = Color(base.panel)
    val PanelTop: Color get() = Color(base.panelTop)
    val PanelBottom: Color get() = Color(base.panelBottom)
    val PanelBorder: Color get() = Color(base.border)
    val EdgeTop: Color get() = Color(base.edgeTop)
    val Field: Color get() = Color(base.field)
    val Line: Color get() = Color(base.line)
    val Text = Color(0xFFECEDEF)
    val Muted = Color(0xFFA3A7AF)
    val Faint = Color(0xFF8A8E96)

    val Accent: Color get() = Color(AppStyle.current.accent)
    /** Text and icons on top of the accent color. */
    val OnAccent: Color get() = if (Accent.luminance() > 0.4f) Color(0xFF0D1424) else Color.White
    val AccentSoft: Color get() = Accent.copy(alpha = 0.18f)
    val AccentLight: Color get() = Color.White.copy(alpha = 0.22f).compositeOver(Accent)
    val AccentDark: Color get() = Color.Black.copy(alpha = 0.2f).compositeOver(Accent)
    /** A field tinted with the accent, for selected items. */
    val AccentTint: Color get() = Accent.copy(alpha = 0.12f).compositeOver(Field)
    val AccentTintStrong: Color get() = Accent.copy(alpha = 0.2f).compositeOver(Panel)
}

/** Rounded corners scaled by the user's "corner rounding" setting. */
fun appCorner(radius: Int): RoundedCornerShape = RoundedCornerShape((radius * AppStyle.current.corners).dp)

@Composable
fun WikiFlertyTheme(content: @Composable () -> Unit) {
    val style = AppStyle.current
    val context = LocalContext.current
    val colors = darkColorScheme(
        primary = AppColors.Accent,
        onPrimary = AppColors.OnAccent,
        primaryContainer = AppColors.AccentTintStrong,
        onPrimaryContainer = AppColors.Text,
        secondary = AppColors.Accent,
        secondaryContainer = AppColors.AccentTintStrong,
        onSecondaryContainer = AppColors.Text,
        background = AppColors.Background,
        onBackground = AppColors.Text,
        surface = AppColors.Background,
        onSurface = AppColors.Text,
        surfaceVariant = AppColors.Panel,
        onSurfaceVariant = AppColors.Muted,
        surfaceContainerLowest = AppColors.Background,
        surfaceContainerLow = AppColors.Field,
        surfaceContainer = AppColors.Panel,
        surfaceContainerHigh = AppColors.PanelTop,
        surfaceContainerHighest = AppColors.PanelBorder,
        outline = AppColors.Line,
        outlineVariant = AppColors.PanelBorder,
    )
    val typography = remember(style.font) {
        val family = if (style.font == "system") {
            null
        } else {
            FontCatalog.all.firstOrNull { it.id == style.font }?.let { FontCatalog.preview(context, it) }
        }
        if (family == null) Typography() else Typography().withFont(family)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * style.textScale)) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

private fun Typography.withFont(family: androidx.compose.ui.text.font.FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
