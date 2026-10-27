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
import com.flerty.wikiflerty.App
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The user's own background picture or silent looping video behind the main screens, with darkening. */
@Composable
fun AppBackground() {
    val style = AppStyle.current
    val app = LocalContext.current.applicationContext as App
    val kind = BackgroundKind.of(style.background)
    if (kind == BackgroundKind.None) return

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
            BackgroundKind.Video -> LoopingVideo(app.style.backgroundVideo, Modifier.fillMaxSize())
            BackgroundKind.None -> Unit
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.backgroundDim)))
    }
}

/** A silent video playing in a loop, filling the area (cropped, never stretched). */
@Composable
private fun LoopingVideo(file: File, modifier: Modifier) {
    if (!file.exists()) return
    key(file.path, file.lastModified()) {
        val player = remember { MediaPlayer() }
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
                                player.setVolume(0f, 0f)
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
