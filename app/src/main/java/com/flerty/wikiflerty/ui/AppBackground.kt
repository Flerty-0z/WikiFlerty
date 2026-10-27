package com.flerty.wikiflerty.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppBackground() {
    val style = AppStyle.current
    val app = LocalContext.current.applicationContext as App
    val kind = BackgroundKind.of(style.background)
    if (kind == BackgroundKind.None) {
        AppStyle.base.ambience?.takeIf { style.ambience }?.let { AmbienceBackground(AppStyle.base, it, Modifier.fillMaxSize()) }
        return
    }

    Box(Modifier.fillMaxSize()) {
        when (kind) {
            BackgroundKind.Image -> {
                val file = app.style.backgroundImage
                val version = file.lastModified()
                val bitmap by produceState<ImageBitmap?>(null, version) {
                    value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
                }
                bitmap?.let {
                    Image(
                        it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (style.backgroundBlur >= 0.5f) Modifier.blur(style.backgroundBlur.dp) else Modifier),
                    )
                }
            }
            BackgroundKind.Video -> {
                val listening by app.readAloud.session.collectAsStateWithLifecycle()
                LoopingVideo(app.style.backgroundVideo, if (listening != null) 0f else style.videoVolume, Modifier.fillMaxSize())
            }
            BackgroundKind.None -> Unit
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.backgroundDim)))
    }
}

@Composable
private fun LoopingVideo(file: File, volume: Float, modifier: Modifier) {
    val currentVolume by androidx.compose.runtime.rememberUpdatedState(volume)
    if (!file.exists()) return
    key(file.path, file.lastModified()) {
        val player = remember { MediaPlayer() }
        androidx.compose.runtime.LaunchedEffect(volume) { runCatching { player.setVolume(volume, volume) } }
        DisposableEffect(Unit) {
            onDispose { runCatching { player.release() } }
        }
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            runCatching {
                                player.reset()
                                player.setDataSource(file.path)
                                player.setSurface(Surface(texture))
                                player.isLooping = true
                                player.setVolume(currentVolume, currentVolume)
                                player.setOnPreparedListener { mp ->
                                    cropToFill(this@apply, mp.videoWidth, mp.videoHeight)
                                    mp.start()
                                }
                                player.prepareAsync()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                            runCatching { cropToFill(this@apply, player.videoWidth, player.videoHeight) }
                        }

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            runCatching { player.stop() }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
        )
    }
}

private fun cropToFill(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = maxOf(viewW / videoWidth, viewH / videoHeight)
    val matrix = Matrix().apply {
        setScale(videoWidth * scale / viewW, videoHeight * scale / viewH, viewW / 2f, viewH / 2f)
    }
    view.setTransform(matrix)
}

val hasAppBackground: Boolean
    get() {
        val style = AppStyle.current
        return BackgroundKind.of(style.background) != BackgroundKind.None || (style.ambience && AppStyle.base.ambience != null)
    }

@Composable
fun AmbienceBackground(base: UiBase, ambience: Ambience, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(base.background))
        ambience.topColor?.let { top ->
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(top).copy(alpha = ambience.topAlpha),
                    1f to Color(top).copy(alpha = 0f),
                    startY = 0f,
                    endY = h * ambience.topHeight,
                ),
            )
        }
        ambience.spots.forEach { spot ->
            val center = androidx.compose.ui.geometry.Offset(w * spot.x, h * spot.y)
            val radius = w * spot.radius
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to Color(spot.color).copy(alpha = spot.alpha),
                    1f to Color(spot.color).copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        if (ambience.dots) {
            val step = 18.dp.toPx()
            val dot = 1.dp.toPx()
            val color = Color.White.copy(alpha = 0.07f)
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) {
                    drawCircle(color, dot, androidx.compose.ui.geometry.Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
}
