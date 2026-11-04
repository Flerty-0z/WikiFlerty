package com.flerty.wikiflerty.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

object WashShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val m = minOf(size.width, size.height)
        val cap = 26f * density.density
        fun r(f: Float) = (m * f).coerceAtMost(cap).let { CornerRadius(it, it * 0.82f) }
        return Outline.Rounded(
            RoundRect(0f, 0f, size.width, size.height, r(0.34f), r(0.2f), r(0.3f), r(0.24f)),
        )
    }
}
