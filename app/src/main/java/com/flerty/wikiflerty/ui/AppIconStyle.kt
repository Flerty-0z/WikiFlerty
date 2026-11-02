package com.flerty.wikiflerty.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.withTransform

enum class AppIconStyle(
    val id: String,
    val title: String,
    val from: Long,
    val to: Long,
    val book: Long,
    val ribbon: Long,
    val pixel: Boolean,
) {
    Cream("cream", "Крем", 0xFF15120F, 0xFF2A2018, 0xFFD9772A, 0xFFF2C38B, false),
    Night("night", "Ночь", 0xFF0A0D18, 0xFF161B2E, 0xFF8FB3FF, 0xFFC9D8FF, false),
    Neon("neon", "Неон", 0xFF0E0F12, 0xFF16181D, 0xFFB6F35A, 0xFF5AF3E0, false),
    Ocean("ocean", "Океан", 0xFF06283A, 0xFF0B4A5E, 0xFF6FE3F0, 0xFFFFFFFF, false),
    Forest("forest", "Лес", 0xFF0F1F17, 0xFF1A3326, 0xFF7ED3A0, 0xFFE9D58A, false),
    Paper("paper", "Бумага", 0xFFF3EBDD, 0xFFE6D9C3, 0xFF6B4A2F, 0xFFC4553A, false),
    Minimal("minimal", "Минимал", 0xFFFFFFFF, 0xFFF1F1F1, 0xFF111111, 0xFF111111, false),
    Gold("gold", "Золото", 0xFF1A1408, 0xFF3A2A0C, 0xFFE8B64C, 0xFFFFF0C2, false),
    Pixel("pixel", "Ретро", 0xFF2B1B4A, 0xFF3D2766, 0xFFFF7AB6, 0xFFFFD84D, true);

    val alias: String get() = "com.flerty.wikiflerty.icon." + id.replaceFirstChar { it.uppercase() }

    companion object {
        const val BOOK_LEFT = "M30,38 C38,35 46,35 52,39 L52,72 C46,68 38,68 30,71 Z"
        const val BOOK_RIGHT = "M78,38 C70,35 62,35 56,39 L56,72 C62,68 70,68 78,71 Z"
        const val RIBBON = "M61,37.2 L67,36.6 L67,52 L64,49 L61,52 Z"
        const val PIXEL_LEFT = "M30,42 h6 v-2 h8 v-2 h8 v34 h-8 v-2 h-8 v2 h-6 z"
        const val PIXEL_RIGHT = "M78,42 h-6 v-2 h-8 v-2 h-8 v34 h8 v-2 h8 v2 h6 z"
        const val PIXEL_RIBBON = "M62,36 h6 v16 h-2 v-2 h-2 v2 h-2 z"

        fun current(context: Context): AppIconStyle {
            val pm = context.packageManager
            return entries.firstOrNull { style ->
                when (pm.getComponentEnabledSetting(ComponentName(context.packageName, style.alias))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> style == Cream
                    else -> false
                }
            } ?: Cream
        }

        fun apply(context: Context, target: AppIconStyle) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, target.alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != target }.forEach { other ->
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, other.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun AppIconPreview(style: AppIconStyle, size: androidx.compose.ui.unit.Dp, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val paths = androidx.compose.runtime.remember(style) {
        val parse = { d: String -> androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath() }
        if (style.pixel) {
            listOf(parse(AppIconStyle.PIXEL_LEFT), parse(AppIconStyle.PIXEL_RIGHT), parse(AppIconStyle.PIXEL_RIBBON))
        } else {
            listOf(parse(AppIconStyle.BOOK_LEFT), parse(AppIconStyle.BOOK_RIGHT), parse(AppIconStyle.RIBBON))
        }
    }
    androidx.compose.foundation.Canvas(modifier.then(androidx.compose.ui.Modifier.size(size))) {
        val s = this.size.minDimension
        val r = s * 0.24f
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(androidx.compose.ui.graphics.Color(style.from), androidx.compose.ui.graphics.Color(style.to)),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(s, s),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        val k = s / 76f
        withTransform({
            translate(-16f * k, -16f * k)
            scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawPath(paths[0], androidx.compose.ui.graphics.Color(style.book))
            drawPath(paths[1], androidx.compose.ui.graphics.Color(style.book))
            drawPath(paths[2], androidx.compose.ui.graphics.Color(style.ribbon))
        }
    }
}
