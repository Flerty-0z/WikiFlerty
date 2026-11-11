package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object ThemeSwap {
    internal var request by mutableStateOf<CompletableDeferred<ImageBitmap?>?>(null)
    internal var shot by mutableStateOf<ImageBitmap?>(null)
    internal var center = Offset.Zero
    internal val progress = Animatable(0f)
    internal var scope: CoroutineScope? = null
    private var busy = false

    fun run(at: Offset?, apply: () -> Unit) {
        val host = scope
        if (host == null || !Motion.enabled || busy) {
            apply()
            return
        }
        busy = true
        host.launch {
            try {
                val wait = CompletableDeferred<ImageBitmap?>()
                request = wait
                val img = withTimeoutOrNull(320) { wait.await() }
                request = null
                if (img == null) {
                    apply()
                    return@launch
                }
                center = at ?: Offset(img.width / 2f, img.height / 2f)
                progress.snapTo(0f)
                shot = img
                withFrameNanos { }
                apply()
                repeat(3) { withFrameNanos { } }
                delay(70)
                progress.animateTo(1f, tween(Motion.ms(640), easing = FastOutSlowInEasing))
            } finally {
                request = null
                shot = null
                busy = false
            }
        }
    }
}

@Composable
fun ThemeSwapHost(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    ThemeSwap.scope = scope
    val layer = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val req = ThemeSwap.request
                    if (req != null && !req.isCompleted) {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                        scope.launch { req.complete(runCatching { layer.toImageBitmap() }.getOrNull()) }
                    } else {
                        drawContent()
                    }
                },
        ) { content() }
        val shot = ThemeSwap.shot
        if (shot != null) {
            val ring = AppColors.Accent
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            ) {
                val p = ThemeSwap.progress.value
                val c = ThemeSwap.center
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
