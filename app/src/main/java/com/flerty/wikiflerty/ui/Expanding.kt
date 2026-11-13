package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.layout.onGloballyPositioned
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
    val cut = remember { mutableIntStateOf(0) }
    val full = remember { mutableIntStateOf(0) }
    val viewport = LocalRevealViewport.current
    val self = remember { arrayOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        if (!Motion.enabled) {
            anim.snapTo(target)
            return@LaunchedEffect
        }
        var prev = anim.value
        anim.animateTo(target, tween(Motion.ms(420), easing = ExpandEase)) {
            val v = value
            if (!expanded && v < prev) {
                val span = (full.intValue - cut.intValue).coerceAtLeast(0)
                val shrink = span * (prev - v)
                val scroll = viewport?.scroll
                val box = viewport?.coords
                val me = self[0]
                if (scroll != null && box != null && me != null && box.isAttached && me.isAttached && scroll.value > 0) {
                    val top = box.localPositionOf(me, Offset.Zero).y
                    if (top < 0f) {
                        val move = minOf(shrink, -top, scroll.value.toFloat())
                        if (move > 0f) scroll.dispatchRawDelta(-move)
                    }
                }
            }
            prev = v
        }
    }
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
            .onGloballyPositioned { self[0] = it }
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipsFold(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
    rowPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    var overflow by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (!open && scroll.maxValue > 0) overflow = true
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.animation.AnimatedContent(
            open,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(Motion.ms(260), delayMillis = Motion.ms(80))) togetherWith androidx.compose.animation.fadeOut(tween(Motion.ms(140)))) using
                    androidx.compose.animation.SizeTransform(clip = true) { _, _ -> tween(Motion.ms(380), easing = ExpandEase) }
            },
            label = "chips-fold",
        ) { expanded ->
            if (expanded) {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(rowPadding),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing, androidx.compose.ui.Alignment.CenterHorizontally),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
                ) { content() }
            } else {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().horizontalScroll(scroll).padding(rowPadding),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) { content() }
            }
        }
        if (overflow) {
            val turn by androidx.compose.animation.core.animateFloatAsState(if (open) -90f else 90f, tween(Motion.ms(320)), label = "chips-turn")
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(width = 56.dp, height = 28.dp)
                        .liveClick(appCorner(14), sound = FeedbackSound.Toggle, pressedScale = 0.9f) { open = !open },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        AppIcons.Chevron,
                        if (open) "Свернуть" else "Развернуть",
                        tint = AppColors.Accent,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
                    )
                }
            }
        }
    }
}
