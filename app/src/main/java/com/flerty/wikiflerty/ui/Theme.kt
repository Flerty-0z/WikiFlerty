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
    val light: Boolean = false,
    val text: Long = 0xFFF2EBE1,
    val muted: Long = 0xFFB4A797,
    val faint: Long = 0xFF8E8273,
) {
    Cream(
        "cream", "Крем", 0xFF15120F, 0xFF201B17, 0xFF26201B, 0xFF1C1814, 0xFF342C25, 0xFF43392F, 0xFF211C17, 0xFF3A3129,
    ),
    Aurora(
        "aurora", "Сияние", 0xFF0A0D18, 0xFF161B2E, 0xFF1B2240, 0xFF13172A, 0xFF262D48, 0xFF3C4670, 0xFF111528, 0xFF2A3150,
        Ambience(
            spots = listOf(
                GlowSpot(0xFF466EFF, 0.42f, 0.23f, 0.08f, 0.60f),
                GlowSpot(0xFF9262FF, 0.30f, 0.85f, 0.71f, 0.62f),
                GlowSpot(0xFF3CAAFF, 0.18f, 0.38f, 1.01f, 0.55f),
            ),
        ),
        text = 0xFFE8ECF7, muted = 0xFFA3ABC4, faint = 0xFF7B839C,
    ),
    Lamp(
        "lamp", "Ночная библиотека", 0xFF110D09, 0xFF1E1813, 0xFF261E17, 0xFF1A1510, 0xFF33291F, 0xFF4E3F30, 0xFF17120D, 0xFF3A2F24,
        Ambience(
            spots = listOf(
                GlowSpot(0xFFE8A048, 0.34f, 0.77f, 0.08f, 0.62f),
                GlowSpot(0xFFAA5A28, 0.26f, 0.08f, 0.78f, 0.60f),
            ),
        ),
        text = 0xFFF2E6D6, muted = 0xFFBDAA92, faint = 0xFF8F7E68,
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
        text = 0xFFE3F2F4, muted = 0xFF9BB7BD, faint = 0xFF6F8D94,
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
        text = 0xFFF0E6F7, muted = 0xFFB6A6C8, faint = 0xFF8A7C9E,
    ),
    Neon(
        "neon", "Графит и неон", 0xFF0B0C0E, 0xFF15171B, 0xFF1A1D22, 0xFF121418, 0xFF262A30, 0xFF3A3F47, 0xFF111316, 0xFF2A2E35,
        Ambience(topColor = 0xFFC6F432, topAlpha = 0.10f, topHeight = 0.31f, dots = true),
        text = 0xFFECEEF0, muted = 0xFFA4A9B0, faint = 0xFF7A7F87,
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
        text = 0xFFE6F0E8, muted = 0xFFA3B8A9, faint = 0xFF76917E,
    ),
    Paper(
        "paper", "Бумага", 0xFFF7F5F0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F5F0, 0xFFE6E1D8, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE9E4DA,
        light = true,
        text = 0xFF26262B, muted = 0xFF77746E, faint = 0xFF9A968E,
    ),
    Mint(
        "mint", "Мята", 0xFFF1F7F4, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFEEF5F1, 0xFFD8E8E0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFDDEBE4,
        Ambience(
            spots = listOf(
                GlowSpot(0xFF8FE0B8, 0.30f, 0.20f, 0.06f, 0.60f),
                GlowSpot(0xFFB8E6F0, 0.24f, 0.88f, 0.80f, 0.58f),
            ),
        ),
        light = true,
        text = 0xFF1F2D27, muted = 0xFF62786E, faint = 0xFF8A9C93,
    ),
    Lavender(
        "lavender", "Лаванда", 0xFFF5F3FB, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF2EFFA, 0xFFE3DEF2, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE6E1F4,
        Ambience(
            spots = listOf(
                GlowSpot(0xFFC6B4FF, 0.32f, 0.78f, 0.06f, 0.60f),
                GlowSpot(0xFFFFC2DA, 0.24f, 0.12f, 0.78f, 0.58f),
            ),
        ),
        light = true,
        text = 0xFF26223A, muted = 0xFF6E6889, faint = 0xFF958FAE,
    ),
    ;

    companion object {
        fun of(id: String): UiBase {
            return entries.firstOrNull { it.id == id } ?: Cream
        }
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
    val base: String = UiBase.Cream.id,
    val accent: Long = DefaultAccent,
    val corners: Float = 1.1f,
    val outline: Boolean = true,
    val buttons: String = ButtonLook.Filled.id,
    val font: String = "nunito",
    val textScale: Float = 1f,
    val pressEffect: Boolean = true,
    val background: String = BackgroundKind.None.id,
    val backgroundDim: Float = 0.45f,
    val backgroundBlur: Float = 0f,
    val ambience: Boolean = true,
    val videoVolume: Float = 0.5f,
    val readingSound: String = ReadingSound.Normal.id,
    val animations: Boolean = true,
    val motionSpeed: String = MotionSpeed.Normal.id,
    val motionStyle: String = MotionStyle.Soft.id,
    val press: String = PressStrength.Normal.id,
    val navigation: String = "drawer",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 3)
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
        put("readingSound", readingSound)
        put("animations", animations)
        put("motionSpeed", motionSpeed)
        put("motionStyle", motionStyle)
        put("press", press)
        put("navigation", navigation)
    }

    companion object {
        fun fromJson(json: JSONObject): UiStyle {
            val d = UiStyle()
            if (json.optInt("version", 1) < 2) {
                return d.copy(
                    textScale = json.optDouble("textScale", d.textScale.toDouble()).toFloat().coerceIn(0.8f, 1.3f),
                    background = json.optString("background", d.background),
                    backgroundDim = json.optDouble("backgroundDim", d.backgroundDim.toDouble()).toFloat().coerceIn(0f, 0.9f),
                    backgroundBlur = json.optDouble("backgroundBlur", d.backgroundBlur.toDouble()).toFloat().coerceIn(0f, 30f),
                    videoVolume = json.optDouble("videoVolume", d.videoVolume.toDouble()).toFloat().coerceIn(0f, 1f),
                    readingSound = json.optString("readingSound", d.readingSound),
                    animations = json.optBoolean("animations", d.animations),
                    motionSpeed = json.optString("motionSpeed", d.motionSpeed),
                    motionStyle = json.optString("motionStyle", d.motionStyle),
                    press = json.optString("press", d.press),
                )
            }
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
                readingSound = json.optString("readingSound", d.readingSound),
                animations = json.optBoolean("animations", d.animations),
                motionSpeed = json.optString("motionSpeed", d.motionSpeed),
                motionStyle = json.optString("motionStyle", d.motionStyle),
                navigation = json.optString("navigation", d.navigation),
                press = json.optString(
                    "press",
                    if (json.has("pressEffect") && !json.optBoolean("pressEffect", true)) PressStrength.Off.id else d.press,
                ),
            )
        }
    }
}

const val DefaultAccent = 0xFFD9772AL

data class StyleTemplate(
    val title: String,
    val base: UiBase,
    val accent: Long,
    val font: String,
    val buttons: ButtonLook,
    val corners: Float,
) {
    fun applyTo(style: UiStyle) = style.copy(
        base = base.id,
        accent = accent,
        font = font,
        buttons = buttons.id,
        corners = corners,
        ambience = true,
    )

    fun matches(style: UiStyle) = UiBase.of(style.base) == base
}

val StyleTemplates = listOf(
    StyleTemplate("Крем", UiBase.Cream, DefaultAccent, "nunito", ButtonLook.Filled, 1.1f),
    StyleTemplate("Сияние", UiBase.Aurora, 0xFF8AB4F8, "onest", ButtonLook.Soft, 1f),
    StyleTemplate("Ночная библиотека", UiBase.Lamp, 0xFFF2C46B, "lora", ButtonLook.Soft, 0.8f),
    StyleTemplate("Глубина океана", UiBase.Deep, 0xFF5EEAD4, "manrope", ButtonLook.Filled, 1.1f),
    StyleTemplate("Сумерки", UiBase.Dusk, 0xFFF28BB8, "nunito", ButtonLook.Soft, 1.2f),
    StyleTemplate("Графит и неон", UiBase.Neon, 0xFFC6F432, "onest", ButtonLook.Outline, 0.6f),
    StyleTemplate("Лес", UiBase.Forest, 0xFF8BD3A5, "rubik", ButtonLook.Soft, 1f),
    StyleTemplate("Бумага", UiBase.Paper, 0xFF3E63DD, "lora", ButtonLook.Filled, 0.8f),
    StyleTemplate("Мята", UiBase.Mint, 0xFF2E9E6E, "manrope", ButtonLook.Soft, 1.2f),
    StyleTemplate("Лаванда", UiBase.Lavender, 0xFF7C5CD6, "nunito", ButtonLook.Filled, 1.3f),
)

val AccentChoices = listOf(
    0xFFD9772A, 0xFFE0A04A, 0xFFC4553A, 0xFFD45D79, 0xFFB0603A,
    0xFF6E8B3D, 0xFF2E8B6F, 0xFF3E7CB1, 0xFF6C5BC7, 0xFF8A6A4F,
)

object AppStyle {
    var current by mutableStateOf(UiStyle())
        internal set

    val base: UiBase get() = UiBase.of(current.base)
    val light: Boolean get() = base.light
    val bottomNav: Boolean get() = true
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
    private val light get() = AppStyle.light
    val Text: Color get() = Color(base.text)
    val Muted: Color get() = Color(base.muted)
    val Faint: Color get() = Color(base.faint)
    val Good: Color get() = if (light) Color(0xFF2E8B57) else Color(0xFF7ED3A0)
    val Warn: Color get() = if (light) Color(0xFFC07514) else Color(0xFFF2B36B)
    val Bad: Color get() = if (light) Color(0xFFD0443E) else Color(0xFFF08A8A)
    val Info: Color get() = if (light) Color(0xFF3D6EF5) else Color(0xFF8AB4F8)
    val Shade: Color get() = Color(base.text)

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
    val view = androidx.compose.ui.platform.LocalView.current
    val light = AppStyle.light
    androidx.compose.runtime.SideEffect {
        val activity = view.context as? android.app.Activity ?: return@SideEffect
        if (activity is com.flerty.wikiflerty.reader.ReaderActivity) return@SideEffect
        val window = activity.window
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
    val scheme = if (light) androidx.compose.material3.lightColorScheme() else darkColorScheme()
    val colors = scheme.copy(
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
