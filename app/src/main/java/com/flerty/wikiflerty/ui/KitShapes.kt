package com.flerty.wikiflerty.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape

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
