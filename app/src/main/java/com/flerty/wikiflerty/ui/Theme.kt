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

data class GlowSpot(val color: Long, val alpha: Float, val x: Float, val y: Float, val radius: Float)

data class Ambience(
    val topColor: Long? = null,
    val topAlpha: Float = 1f,
    val topHeight: Float = 0.55f,
    val spots: List<GlowSpot> = emptyList(),
    val dots: Boolean = false,
)

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
    val ambience: Ambience? = null,
) {
    Aurora(
        "aurora", "Сияние", 0xFF0A0D18, 0xFF161B2E, 0xFF1B2240, 0xFF13172A, 0xFF262D48, 0xFF3C4670, 0xFF111528, 0xFF2A3150,
        Ambience(
            spots = listOf(
                GlowSpot(0xFF466EFF, 0.42f, 0.23f, 0.08f, 0.60f),
                GlowSpot(0xFF9262FF, 0.30f, 0.85f, 0.71f, 0.62f),
                GlowSpot(0xFF3CAAFF, 0.18f, 0.38f, 1.01f, 0.55f),
            ),
        ),
    ),
    Lamp(
        "lamp", "Ночная библиотека", 0xFF110D09, 0xFF1E1813, 0xFF261E17, 0xFF1A1510, 0xFF33291F, 0xFF4E3F30, 0xFF17120D, 0xFF3A2F24,
        Ambience(
            spots = listOf(
                GlowSpot(0xFFE8A048, 0.34f, 0.77f, 0.08f, 0.62f),
                GlowSpot(0xFFAA5A28, 0.26f, 0.08f, 0.78f, 0.60f),
            ),
        ),
    ),
    Deep(
        "deep", "Глубина океана", 0xFF06141D, 0xFF0F2430, 0xFF123040, 0xFF0C1E28, 0xFF1C3A48, 0xFF2E5868, 0xFF0A1B24, 0xFF1F3C4A,
        Ambience(
            topColor = 0xFF072A33,
            spots = listOf(
                GlowSpot(0xFF2EC4B6, 0.36f, 0.26f, 0.09f, 0.57f),
                GlowSpot(0xFF2878C8, 0.26f, 0.87f, 0.88f, 0.57f),
            ),
        ),
    ),
    Dusk(
        "dusk", "Сумерки", 0xFF120A1F, 0xFF1E1530, 0xFF26193D, 0xFF1A1229, 0xFF30244A, 0xFF4A3970, 0xFF160F24, 0xFF372A52,
        Ambience(
            topColor = 0xFF1C1033,
            spots = listOf(
                GlowSpot(0xFFF26EAA, 0.34f, 0.77f, 0.08f, 0.57f),
                GlowSpot(0xFF6E50F0, 0.30f, 0.13f, 0.64f, 0.62f),
            ),
        ),
    ),
    Neon(
        "neon", "Графит и неон", 0xFF0B0C0E, 0xFF15171B, 0xFF1A1D22, 0xFF121418, 0xFF262A30, 0xFF3A3F47, 0xFF111316, 0xFF2A2E35,
        Ambience(topColor = 0xFFC6F432, topAlpha = 0.10f, topHeight = 0.31f, dots = true),
    ),
    Forest(
        "forest", "Лес", 0xFF08130E, 0xFF122219, 0xFF16291F, 0xFF0F1D15, 0xFF20382B, 0xFF32543F, 0xFF0C1A12, 0xFF22392C,
        Ambience(
            topColor = 0xFF0D1F17,
            spots = listOf(
                GlowSpot(0xFF46AA6E, 0.32f, 0.18f, 0.13f, 0.60f),
                GlowSpot(0xFFBED26E, 0.16f, 0.87f, 0.81f, 0.54f),
            ),
        ),
    ),
    Dark("dark", "Тёмная", 0xFF101113, 0xFF1F2125, 0xFF25282D, 0xFF1C1E22, 0xFF2C2F34, 0xFF454952, 0xFF16171A, 0xFF33363C),
    Graphite("graphite", "Графит", 0xFF141414, 0xFF222222, 0xFF292929, 0xFF1F1F1F, 0xFF323232, 0xFF4A4A4A, 0xFF191919, 0xFF3A3A3A),
    Ocean("ocean", "Океан", 0xFF0D1117, 0xFF161D27, 0xFF1B2431, 0xFF141B25, 0xFF243042, 0xFF3A4A63, 0xFF0F151E, 0xFF2A3546),
    Oled("oled", "OLED", 0xFF000000, 0xFF121214, 0xFF18181B, 0xFF0F0F11, 0xFF26262B, 0xFF3A3A42, 0xFF0A0A0C, 0xFF2A2A30),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Aurora
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

data class UiStyle(
    val base: String = UiBase.Aurora.id,
    val accent: Long = 0xFF8AB4F8,
    val corners: Float = 1f,
    val outline: Boolean = true,
    val buttons: String = ButtonLook.Soft.id,
    val font: String = "onest",
    val textScale: Float = 1f,
    val pressEffect: Boolean = true,
    val background: String = BackgroundKind.None.id,
    val backgroundDim: Float = 0.45f,
    val backgroundBlur: Float = 0f,
    val ambience: Boolean = true,
    val videoVolume: Float = 0.5f,
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
        put("ambience", ambience)
        put("videoVolume", videoVolume.toDouble())
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
                ambience = json.optBoolean("ambience", d.ambience),
                videoVolume = json.optDouble("videoVolume", d.videoVolume.toDouble()).toFloat().coerceIn(0f, 1f),
            )
        }
    }
}

data class StylePreset(
    val title: String,
    val base: UiBase,
    val accent: Long,
    val font: String? = null,
    val buttons: ButtonLook? = null,
    val corners: Float? = null,
) {
    fun applyTo(style: UiStyle) = style.copy(
        base = base.id,
        accent = accent,
        font = font ?: style.font,
        buttons = buttons?.id ?: style.buttons,
        corners = corners ?: style.corners,
        ambience = true,
    )

    fun matches(style: UiStyle) = style.base == base.id && style.accent == accent && (font == null || style.font == font)
}

val StyleTemplates = listOf(
    StylePreset("Сияние", UiBase.Aurora, 0xFF8AB4F8, font = "onest", buttons = ButtonLook.Soft, corners = 1f),
    StylePreset("Ночная библиотека", UiBase.Lamp, 0xFFF2C46B, font = "lora", buttons = ButtonLook.Soft, corners = 0.8f),
    StylePreset("Глубина океана", UiBase.Deep, 0xFF5EEAD4, font = "manrope", buttons = ButtonLook.Filled, corners = 1.1f),
    StylePreset("Сумерки", UiBase.Dusk, 0xFFF28BB8, font = "nunito", buttons = ButtonLook.Soft, corners = 1.2f),
    StylePreset("Графит и неон", UiBase.Neon, 0xFFC6F432, font = "onest", buttons = ButtonLook.Outline, corners = 0.6f),
    StylePreset("Лес", UiBase.Forest, 0xFF8BD3A5, font = "rubik", buttons = ButtonLook.Soft, corners = 1f),
)

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

    val backgroundImage = File(context.filesDir, "ui_background.jpg")
    val backgroundVideo = File(context.filesDir, "ui_background.mp4")

    init {
        AppStyle.current = _style.value
    }

    var onChanged: (() -> Unit)? = null

    fun update(transform: (UiStyle) -> UiStyle) {
        if (write(transform(_style.value))) onChanged?.invoke()
    }

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
    val OnAccent: Color get() = if (Accent.luminance() > 0.4f) Color(0xFF0D1424) else Color.White
    val AccentSoft: Color get() = Accent.copy(alpha = 0.18f)
    val AccentLight: Color get() = Color.White.copy(alpha = 0.22f).compositeOver(Accent)
    val AccentDark: Color get() = Color.Black.copy(alpha = 0.2f).compositeOver(Accent)
    val AccentTint: Color get() = Accent.copy(alpha = 0.12f).compositeOver(Field)
    val AccentTintStrong: Color get() = Accent.copy(alpha = 0.2f).compositeOver(Panel)
}

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
