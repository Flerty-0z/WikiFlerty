package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SwapHost {
    var request by mutableStateOf<CompletableDeferred<ImageBitmap?>?>(null)
    var shot by mutableStateOf<ImageBitmap?>(null)
    var center = Offset.Zero
    var origin = Offset.Zero
    var ring by mutableStateOf<Color?>(null)
    val progress = Animatable(0f)
    var scope: CoroutineScope? = null
    var busy = false
}

object ThemeSwap {
    private val hosts = ArrayList<SwapHost>()

    internal fun add(host: SwapHost) { hosts.remove(host); hosts.add(host) }
    internal fun remove(host: SwapHost) { hosts.remove(host) }

    fun run(atWindow: Offset?, ring: Color? = null, apply: () -> Unit) {
        val host = hosts.lastOrNull()
        val scope = host?.scope
        if (host == null || scope == null || !Motion.enabled || host.busy) {
            apply()
            return
        }
        host.busy = true
        scope.launch {
            try {
                val wait = CompletableDeferred<ImageBitmap?>()
                host.request = wait
                val img = withTimeoutOrNull(320) { wait.await() }
                host.request = null
                if (img == null) {
                    apply()
                    return@launch
                }
                host.center = atWindow?.let { it - host.origin } ?: Offset(img.width / 2f, img.height / 2f)
                host.ring = ring
                host.progress.snapTo(0f)
                host.shot = img
                withFrameNanos { }
                apply()
                repeat(3) { withFrameNanos { } }
                delay(70)
                host.progress.animateTo(1f, tween(Motion.ms(640), easing = FastOutSlowInEasing))
            } finally {
                host.request = null
                host.shot = null
                host.busy = false
            }
        }
    }
}

@Composable
fun ThemeSwapHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val host = remember { SwapHost() }
    host.scope = scope
    DisposableEffect(host) {
        ThemeSwap.add(host)
        onDispose { ThemeSwap.remove(host) }
    }
    val layer = rememberGraphicsLayer()
    Box(modifier.fillMaxSize().onGloballyPositioned { host.origin = it.positionInWindow() }) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val req = host.request
                    if (req != null && !req.isCompleted) {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                        scope.launch { req.complete(runCatching { layer.toImageBitmap() }.getOrNull()) }
                    } else {
                        drawContent()
                    }
                },
        ) { content() }
        val shot = host.shot
        if (shot != null) {
            val ring = host.ring ?: AppColors.Accent
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            ) {
                val p = host.progress.value
                val c = host.center
                val far = max(
                    max(hypot(c.x, c.y), hypot(size.width - c.x, c.y)),
                    max(hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y)),
                )
                val r = far * p
                val hole = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    if (r > 0f) addOval(Rect(c, r))
                }
                clipPath(hole) { drawImage(shot) }
                if (p > 0f && p < 1f) {
                    val fade = 1f - p
                    drawCircle(ring.copy(alpha = 0.55f * fade), r, c, style = Stroke(2.5f * density))
                    drawCircle(ring.copy(alpha = 0.18f * fade), r + 6f * density, c, style = Stroke(6f * density))
                }
            }
        }
    }
}
