package com.flerty.wikiflerty.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

val clayShadow: Color get() = if (AppStyle.light) Color(0xFFC9B8EE) else Color(0xFF110D22)

private fun DrawScope.bevel(shape: Shape, width: Dp, light: Boolean) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    clipPath(path) {
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (light) 0.85f else 0.16f), Color.Transparent, Color.Black.copy(alpha = if (light) 0.1f else 0.28f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width.toPx() * 2),
        )
    }
}

fun Modifier.clayBevel(shape: Shape, width: Dp = 6.dp): Modifier = this.drawBehind { bevel(shape, width, AppStyle.light) }

fun Modifier.clayBevelOver(shape: Shape, width: Dp = 5.dp): Modifier = this.drawWithContent {
    drawContent()
    bevel(shape, width, AppStyle.light)
}

fun Modifier.sketchBorder(shape: Shape, color: Color, width: Dp = 1.3.dp): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val w = width.toPx()
    drawOutline(outline, color, style = Stroke(w * 2))
    translate(1.2f * density, 0.9f * density) { drawOutline(outline, color.copy(alpha = color.alpha * 0.4f), style = Stroke(w)) }
}

fun Modifier.tapeStrip(color: Color): Modifier = this.drawWithContent {
    drawContent()
    val w = size.width * 0.5f
    val h = 9.dp.toPx()
    rotate(-5f, Offset(size.width / 2, 0f)) {
        drawRect(color, Offset((size.width - w) / 2, -h * 0.45f), androidx.compose.ui.geometry.Size(w, h))
    }
}
