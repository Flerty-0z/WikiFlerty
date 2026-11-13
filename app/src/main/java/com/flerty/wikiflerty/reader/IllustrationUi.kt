package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.Illustration
import com.flerty.wikiflerty.data.InkStroke
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import com.flerty.wikiflerty.ui.revealScroll
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

class IllustrationOpen(
    val href: String,
    val image: ImageBitmap,
    val chapter: String?,
    val locatorJson: String?,
    val fromGallery: Boolean,
)

class IllustrationActions(
    val load: suspend (String, Int) -> ImageBitmap?,
    val onFavorite: (IllustrationOpen, Boolean) -> Unit,
    val onStrokes: (IllustrationOpen, List<InkStroke>) -> Unit,
    val onOpen: (Illustration) -> Unit,
    val onGoTo: (IllustrationOpen) -> Unit,
)

private val InkWidths = listOf(0.005f, 0.011f, 0.022f)

private class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fitIn(w: Float, h: Float, iw: Int, ih: Int): Fit {
    if (iw <= 0 || ih <= 0) return Fit(0f, 0f, w, h)
    val k = min(w / iw, h / ih)
    val dw = iw * k
    val dh = ih * k
    return Fit((w - dw) / 2f, (h - dh) / 2f, dw, dh)
}

private fun DrawScope.ink(stroke: InkStroke, fit: Fit, alpha: Float = 0.9f) {
    val p = stroke.points
    if (p.size < 2) return
    val color = Color(stroke.color).copy(alpha = alpha)
    val w = (stroke.width * fit.width).coerceAtLeast(1f)
    fun x(i: Int) = fit.left + p[i] * fit.width
    fun y(i: Int) = fit.top + p[i + 1] * fit.height
    if (p.size == 2) {
        drawCircle(color, w / 2f, Offset(x(0), y(0)))
        return
    }
    val path = Path()
    path.moveTo(x(0), y(0))
    var i = 2
    while (i + 2 < p.size) {
        val mx = (x(i) + x(i + 2)) / 2f
        val my = (y(i) + y(i + 2)) / 2f
        path.quadraticTo(x(i), y(i), mx, my)
        i += 2
    }
    path.lineTo(x(p.size - 2), y(p.size - 2))
    drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun InkLayer(strokes: List<InkStroke>, live: List<Float>, liveColor: Long, liveWidth: Float, iw: Int, ih: Int, modifier: Modifier) {
    Canvas(modifier) {
        val fit = fitIn(size.width, size.height, iw, ih)
        strokes.forEach { ink(it, fit) }
        if (live.size >= 2) ink(InkStroke(liveColor, liveWidth, live.toFloatArray()), fit)
    }
}

@Composable
fun IllustrationViewer(
    open: IllustrationOpen,
    entry: Illustration?,
    palette: List<Long>,
    actions: IllustrationActions,
    onClose: () -> Unit,
) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val strokes = remember(open.href) { mutableStateListOf<InkStroke>().apply { entry?.strokes?.let { addAll(it) } } }
    val live = remember { mutableStateListOf<Float>() }
    var drawing by remember { mutableStateOf(false) }
    var color by remember { mutableLongStateOf(palette.firstOrNull() ?: 0xFFEF6B6BL) }
    var level by remember { mutableIntStateOf(1) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val drop = remember { Animatable(0f) }
    val favorite = entry?.favorite == true
    val iw = open.image.width
    val ih = open.image.height
    val density = LocalDensity.current
    val closeAt = with(density) { 120.dp.toPx() }

    fun commit() = actions.onStrokes(open, strokes.toList())

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val fit = fitIn(cw, ch, iw, ih)
        val center = Offset(cw / 2f, ch / 2f)
        fun clamp(o: Offset, s: Float): Offset {
            val mx = max(0f, (fit.width * s - cw) / 2f)
            val my = max(0f, (fit.height * s - ch) / 2f)
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }
        fun toImage(p: Offset): Offset {
            val q = (p - center - offset) / scale + center
            return Offset(((q.x - fit.left) / fit.width).coerceIn(0f, 1f), ((q.y - fit.top) / fit.height).coerceIn(0f, 1f))
        }
        fun transform(c: Offset, zoom: Float, pan: Offset) {
            val ns = (scale * zoom).coerceIn(1f, 6f)
            val k = ns / scale
            val o = offset + (c - center - offset) * (1f - k) + pan
            scale = ns
            offset = clamp(o, ns)
        }
        fun zoomTo(target: Float, at: Offset) {
            val fromS = scale
            val fromO = offset
            val toO = if (target <= 1f) Offset.Zero else clamp((center - at) * (target - 1f), target)
            scope.launch {
                val t = Animatable(0f)
                t.animateTo(1f, tween(Motion.ms(300), easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                    scale = fromS + (target - fromS) * value
                    offset = fromO + (toO - fromO) * value
                }
            }
        }
        val dim = (1f - abs(drop.value) / (closeAt * 3f)).coerceIn(0.4f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f * dim))
                .pointerInput(drawing) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pts = ArrayList<Float>()
                        var multi = false
                        var dragging = false
                        var dy = 0f
                        if (drawing) {
                            val q = toImage(down.position)
                            pts += q.x
                            pts += q.y
                            live.clear()
                            live.addAll(pts)
                            down.consume()
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break
                            if (pressed >= 2) {
                                if (!multi) {
                                    multi = true
                                    live.clear()
                                }
                                transform(event.calculateCentroid(useCurrent = true), event.calculateZoom(), event.calculatePan())
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (drawing && !multi) {
                                val c = event.changes.first { it.pressed }
                                val q = toImage(c.position)
                                val n = pts.size
                                if (n < 2 || abs(q.x - pts[n - 2]) * fit.width * scale > 1.5f || abs(q.y - pts[n - 1]) * fit.height * scale > 1.5f) {
                                    pts += q.x
                                    pts += q.y
                                    live.add(q.x)
                                    live.add(q.y)
                                }
                                c.consume()
                            } else if (!drawing) {
                                val pan = event.calculatePan()
                                if (scale > 1.01f) {
                                    if (pan != Offset.Zero) {
                                        offset = clamp(offset + pan, scale)
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (!multi) {
                                    dy += pan.y
                                    if (!dragging && abs(dy) > viewConfiguration.touchSlop) dragging = true
                                    if (dragging) {
                                        val at = dy
                                        scope.launch { drop.snapTo(at) }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        if (drawing && !multi && pts.size >= 2) {
                            strokes.add(InkStroke(color, InkWidths[level] / scale, pts.toFloatArray()))
                            commit()
                        }
                        live.clear()
                        if (!drawing && dragging) {
                            if (abs(dy) > closeAt) {
                                feedback?.play(FeedbackSound.Close)
                                onClose()
                            } else {
                                scope.launch { drop.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                            }
                        }
                    }
                }
                .then(
                    if (drawing) Modifier else Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { p ->
                            feedback?.play(FeedbackSound.Tick)
                            zoomTo(if (scale > 1.1f) 1f else 2.6f, p)
                        })
                    },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + drop.value
                    },
            ) {
                Image(open.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                InkLayer(strokes, live, color, InkWidths[level] / scale, iw, ih, Modifier.fillMaxSize())
            }
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewerButton(AppIcons.Close, "Закрыть", false, FeedbackSound.Close) { onClose() }
            Box(Modifier.weight(1f))
            val beat = remember { Animatable(1f) }
            LaunchedEffect(favorite) {
                if (favorite) {
                    beat.snapTo(0.7f)
                    beat.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
                }
            }
            ViewerButton(
                if (favorite) AppIcons.HeartFilled else AppIcons.Heart,
                if (favorite) "Убрать из избранного" else "В избранное",
                favorite,
                FeedbackSound.Toggle,
                iconModifier = Modifier.graphicsLayer { scaleX = beat.value; scaleY = beat.value },
            ) { actions.onFavorite(open, !favorite) }
            ViewerButton(AppIcons.Pencil, "Рисовать", drawing, FeedbackSound.Toggle) { drawing = !drawing }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = drawing,
                enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(280)) { it / 2 },
                exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(220)) { it / 2 },
            ) {
                InkTools(
                    palette = palette,
                    color = color,
                    level = level,
                    canUndo = strokes.isNotEmpty(),
                    onColor = { color = it },
                    onLevel = { level = it },
                    onUndo = {
                        if (strokes.isNotEmpty()) {
                            strokes.removeAt(strokes.lastIndex)
                            commit()
                        }
                    },
                    onClear = {
                        if (strokes.isNotEmpty()) {
                            strokes.clear()
                            commit()
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = !drawing && open.fromGallery && open.locatorJson != null,
                enter = fadeIn(Motion.fade(200)),
                exit = fadeOut(Motion.fade(160)),
            ) {
                Row(
                    Modifier
                        .height(46.dp)
                        .panelSurface(appCorner(23), elevation = 10.dp)
                        .liveClick(appCorner(23), sound = FeedbackSound.Open) { actions.onGoTo(open) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(AppIcons.Book, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    Text("К месту в книге", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ViewerButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    sound: FeedbackSound,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val on by animateFloatAsState(if (active) 1f else 0f, Motion.tween(220), label = "viewer-btn")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .background(AppColors.Accent.copy(alpha = 0.22f * on))
            .border(1.dp, if (active) AppColors.Accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f), CircleShape)
            .liveClick(CircleShape, sound = sound, pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) AppColors.Accent else Color.White, modifier = iconModifier.size(20.dp))
    }
}

@Composable
private fun InkTools(
    palette: List<Long>,
    color: Long,
    level: Int,
    canUndo: Boolean,
    onColor: (Long) -> Unit,
    onLevel: (Int) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().panelSurface(appCorner(22), elevation = 12.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (palette + listOf(0xFFFFFFFFL, 0xFF1B1B1BL)).distinct().forEach { c ->
                val on by animateFloatAsState(if (c == color) 1f else 0f, Motion.tween(200), label = "ink-color")
                Box(
                    Modifier
                        .size(36.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.86f) { onColor(c) }
                        .border((2f * on).dp, AppColors.Text.copy(alpha = on), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(1.dp, Color(0x44FFFFFF), CircleShape),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InkWidths.indices.forEach { i ->
                val on = i == level
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(appCorner(12))
                        .background(if (on) AppColors.AccentSoft else Color.Transparent)
                        .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.88f) { onLevel(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size((5 + i * 5).dp).clip(CircleShape).background(if (on) AppColors.Accent else AppColors.Muted))
                }
            }
            Box(Modifier.weight(1f))
            ToolIcon(AppIcons.Undo, "Отменить", canUndo, onUndo)
            ToolIcon(AppIcons.Trash, "Стереть всё", canUndo, onClear)
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, Motion.tween(200), label = "tool")
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { this.alpha = alpha }
            .liveClick(appCorner(12), sound = FeedbackSound.Tap, pressedScale = 0.88f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
}

@Composable
internal fun IllustrationGallery(items: List<Illustration>, actions: IllustrationActions, modifier: Modifier = Modifier) {
    val list = remember(items) { items.filter { it.favorite }.sortedByDescending { it.createdAt } }
    if (list.isEmpty()) {
        Row(
            modifier.padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).clip(appCorner(14)).background(AppColors.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Image, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Иллюстраций нет", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Нажми на картинку в книге → ♡", color = AppColors.Muted, fontSize = 12.5.sp)
            }
        }
        return
    }
    Column(modifier.revealScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> GalleryTile(item, actions, Modifier.weight(1f)) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GalleryTile(item: Illustration, actions: IllustrationActions, modifier: Modifier) {
    val shape = appCorner(14)
    val image by produceState<ImageBitmap?>(null, item.href) { value = actions.load(item.href, 420) }
    val shown by animateFloatAsState(if (image != null) 1f else 0f, Motion.tween(260), label = "tile")
    Box(
        modifier
            .aspectRatio(0.78f)
            .clip(shape)
            .background(AppColors.Field)
            .border(1.dp, AppColors.PanelBorder, shape)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.95f) { actions.onOpen(item) },
    ) {
        image?.let { bmp ->
            Box(Modifier.fillMaxSize().padding(4.dp).graphicsLayer { alpha = shown }) {
                Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                InkLayer(item.strokes, emptyList(), 0, 0f, bmp.width, bmp.height, Modifier.fillMaxSize())
            }
        }
        if (item.strokes.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Pencil, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
        item.chapter?.let { ch ->
            Text(
                ch,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            )
        }
    }
}
