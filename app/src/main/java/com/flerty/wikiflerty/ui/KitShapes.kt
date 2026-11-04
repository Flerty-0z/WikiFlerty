package com.flerty.wikiflerty.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
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

class PixelShape(private val step: Float = 4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = (step * density.density).coerceAtMost(minOf(size.width, size.height) / 4f)
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(d, 0f)
            lineTo(w - d, 0f); lineTo(w - d, d); lineTo(w, d)
            lineTo(w, h - d); lineTo(w - d, h - d); lineTo(w - d, h)
            lineTo(d, h); lineTo(d, h - d); lineTo(0f, h - d)
            lineTo(0f, d); lineTo(d, d)
            close()
        }
        return Outline.Generic(p)
    }
}

val Pixel4 = PixelShape(4f)
val Pixel3 = PixelShape(3f)
