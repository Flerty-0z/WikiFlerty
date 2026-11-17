package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object BookOpening {
    private val sources = HashMap<String, LinkedHashMap<Any, Rect>>()

    class Opening(val book: Book, val from: Rect?, val cover: File?) {
        var launch by mutableStateOf<(() -> Unit)?>(null)
        var failed by mutableStateOf(false)
    }

    var active by mutableStateOf<Opening?>(null)
        private set

    internal fun place(id: String, token: Any, rect: Rect) {
        val map = sources.getOrPut(id) { LinkedHashMap() }
        map.remove(token)
        map[token] = rect
    }

    internal fun forget(id: String, token: Any) {
        val map = sources[id] ?: return
        map.remove(token)
        if (map.isEmpty()) sources.remove(id)
    }

    fun begin(app: App, book: Book) {
        if (!Motion.enabled || EInk.on) {
            active = null
            return
        }
        val from = sources[book.id]?.values?.lastOrNull()?.takeIf { it.width > 4f && it.height > 4f }
        val cover = app.library.coverFile(book)?.takeIf { it.exists() }
        active = Opening(book, from, cover)
    }

    fun deliver(start: () -> Unit) {
        val o = active
        if (o == null || o.failed) start() else o.launch = start
    }

    fun fail() {
        active?.failed = true
    }

    internal fun clear(o: Opening) {
        if (active === o) active = null
    }
}

fun Modifier.openSource(bookId: String): Modifier = composed {
    val token = remember { Any() }
    DisposableEffect(bookId) { onDispose { BookOpening.forget(bookId, token) } }
    this.onGloballyPositioned { c ->
        if (c.isAttached) BookOpening.place(bookId, token, Rect(c.positionOnScreen(), Size(c.size.width.toFloat(), c.size.height.toFloat())))
    }
}

private val Lift = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Swing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

@Composable
fun BookOpeningLayer() {
    val opening = BookOpening.active ?: return
    androidx.compose.runtime.key(opening) { OpeningDialog(opening) }
}

@Composable
private fun OpeningDialog(o: BookOpening.Opening) {
    val app = LocalContext.current.applicationContext as App
    val pageColor = remember { Color(app.readerSettings.settings.value.pageTheme.background) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var launched by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, launched) {
        val obs = LifecycleEventObserver { _, e -> if (launched && e == Lifecycle.Event.ON_STOP) BookOpening.clear(o) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val cover by produceState<ImageBitmap?>(o.cover?.let { CoverCache.peek(it, 420) }, o.cover) {
        if (value == null) value = o.cover?.let { withContext(Dispatchers.IO) { CoverCache.load(it, 420) } }
    }
    val lift = remember { Animatable(0f) }
    val swing = remember { Animatable(0f) }
    val gone = remember { Animatable(0f) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(o) {
        launch { lift.animateTo(1f, tween(420, easing = Lift)) }
        delay(300)
        swing.animateTo(1f, tween(620, easing = Swing))
        val waitStart = System.currentTimeMillis()
        while (o.launch == null && !o.failed) {
            val waited = System.currentTimeMillis() - waitStart
            if (waited > 300) waiting = true
            if (waited > 12_000) o.failed = true
            delay(16)
        }
        if (o.failed) {
            gone.animateTo(1f, tween(220))
            BookOpening.clear(o)
            return@LaunchedEffect
        }
        o.launch?.invoke()
        launched = true
        delay(1600)
        BookOpening.clear(o)
    }
    LaunchedEffect(o.failed) {
        if (o.failed) {
            gone.animateTo(1f, tween(220))
            BookOpening.clear(o)
        }
    }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogWindowSetup()
        val density = LocalDensity.current
        var origin by remember { mutableStateOf<Offset?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionOnScreen() }
                .graphicsLayer { alpha = 1f - gone.value },
        ) {
            val at = origin ?: return@Box
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = size.width
                    val h = size.height
                    val coverW = minOf(w * 0.58f, h * 0.58f / 1.5f)
                    val coverH = coverW * 1.5f
                    val target = Rect(Offset((w - coverW) / 2f, (h - coverH) / 2f), Size(coverW, coverH))
                    val s = swing.value
                    drawRect(Color.Black.copy(alpha = 0.42f * lift.value * (1f - s)))
                    if (s > 0f) {
                        val pageRect = lerp(target, Rect(0f, 0f, w, h), s * s)
                        drawRect(pageColor, pageRect.topLeft, pageRect.size)
                        val shade = (1f - s) * 0.35f
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = shade), Color.Transparent), pageRect.left, pageRect.left + pageRect.width * 0.35f),
                            pageRect.topLeft,
                            pageRect.size,
                        )
                    }
                },
            )
            CoverLeaf(o, cover, at, lift.value, swing.value)
            if (waiting) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KitSpinner(Modifier.size(26.dp), color = if (Color(app.readerSettings.settings.value.pageTheme.text).luminance() > 0.5f) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun CoverLeaf(o: BookOpening.Opening, cover: ImageBitmap?, origin: Offset, lift: Float, swing: Float) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val coverW = minOf(w * 0.58f, h * 0.58f / 1.5f)
        val coverH = coverW * 1.5f
        val target = Rect(Offset((w - coverW) / 2f, (h - coverH) / 2f), Size(coverW, coverH))
        val start = o.from?.let { it.translate(-origin.x, -origin.y) } ?: Rect(target.center - Offset(coverW * 0.3f, coverH * 0.3f), Size(coverW * 0.6f, coverH * 0.6f))
        val rect = lerp(start, target, lift)
        val angle = -180f * swing
        val back = angle < -90f
        val fadeIn = (if (o.from == null) lift.coerceIn(0f, 1f) else 1f) * (1f - ((swing - 0.55f) / 0.35f).coerceIn(0f, 1f))
        with(density) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = rect.left
                        translationY = rect.top
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = angle
                        cameraDistance = 14f * this.density
                        alpha = fadeIn
                        shadowElevation = (6f + 18f * lift * (1f - swing)) * this.density
                        shape = appCorner(6)
                        clip = true
                    }
                    .size(rect.width.toDp(), rect.height.toDp()),
            ) {
                if (back) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2A2219)))
                } else if (cover != null) {
                    Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    GeneratedCover(o.book.title, o.book.author, Modifier.fillMaxSize())
                }
                if (!back) {
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.18f * swing), Color.Transparent, Color.Black.copy(alpha = 0.35f * swing))))
                        },
                    )
                }
            }
        }
    }
}
