package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val ExpandEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun ExpandingText(
    text: String,
    expanded: Boolean,
    lines: Int,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    onOverflow: (Boolean) -> Unit = {},
) = ExpandingText(AnnotatedString(text), expanded, lines, color, fontSize, lineHeight, modifier, fontWeight, onOverflow)

@Composable
fun ExpandingText(
    text: AnnotatedString,
    expanded: Boolean,
    lines: Int,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    onOverflow: (Boolean) -> Unit = {},
) {
    val anim = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        if (Motion.enabled) anim.animateTo(target, tween(Motion.ms(420), easing = ExpandEase)) else anim.snapTo(target)
    }
    val cut = remember { mutableIntStateOf(0) }
    val full = remember { mutableIntStateOf(0) }
    val fadePx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    Text(
        text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        onTextLayout = { r ->
            val over = r.lineCount > lines
            cut.intValue = if (over) r.getLineBottom(lines - 1).roundToInt() else r.size.height
            full.intValue = r.size.height
            onOverflow(over)
        },
        modifier = modifier
            .clipToBounds()
            .layout { m, c ->
                val p = m.measure(c.copy(maxHeight = Constraints.Infinity))
                val low = cut.intValue.takeIf { it in 1 until p.height } ?: p.height
                val h = (low + (p.height - low) * anim.value).roundToInt().coerceIn(c.minHeight, if (c.hasBoundedHeight) c.maxHeight else Int.MAX_VALUE)
                layout(p.width, h) { p.place(0, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val v = anim.value
                if (v < 1f && cut.intValue in 1 until full.intValue) {
                    val bottom = size.height
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Black.copy(alpha = v),
                            startY = bottom - fadePx,
                            endY = bottom,
                        ),
                        topLeft = Offset(0f, bottom - fadePx),
                        size = androidx.compose.ui.geometry.Size(size.width, fadePx),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    )
}
