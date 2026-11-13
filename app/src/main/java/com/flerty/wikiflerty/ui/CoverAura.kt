package com.flerty.wikiflerty.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverAura {
    private val cache = ConcurrentHashMap<String, List<Color>>()

    private fun key(file: File) = file.path + "|" + file.lastModified()

    fun peek(file: File?): List<Color>? = file?.let { cache[key(it)] }

    fun load(file: File?): List<Color>? {
        if (file == null || !file.exists()) return null
        val k = key(file)
        cache[k]?.let { return it }
        val found = runCatching { compute(file) }.getOrNull() ?: return null
        cache[k] = found
        return found
    }

    private fun tune(hue: Float, sat: Float, value: Float): Color {
        val hsv = floatArrayOf(hue, sat.coerceIn(0.42f, 0.9f), value.coerceIn(0.6f, 0.92f))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun compute(file: File): List<Color>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 96) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        val bins = 12
        val weight = FloatArray(bins)
        val hueX = FloatArray(bins)
        val hueY = FloatArray(bins)
        val sat = FloatArray(bins)
        val value = FloatArray(bins)
        val hsv = FloatArray(3)
        var grayValue = 0f
        var grayCount = 0
        val step = maxOf(1, pixels.size / 2400)
        var i = 0
        while (i < pixels.size) {
            android.graphics.Color.colorToHSV(pixels[i], hsv)
            grayValue += hsv[2]
            grayCount++
            if (hsv[1] >= 0.16f && hsv[2] >= 0.16f) {
                val b = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
                val wgt = hsv[1] * hsv[1] * (0.4f + 0.6f * hsv[2])
                val rad = Math.toRadians(hsv[0].toDouble())
                weight[b] += wgt
                hueX[b] += (Math.cos(rad) * wgt).toFloat()
                hueY[b] += (Math.sin(rad) * wgt).toFloat()
                sat[b] += hsv[1] * wgt
                value[b] += hsv[2] * wgt
            }
            i += step
        }
        val total = weight.sum()
        if (grayCount == 0) return null
        if (total < grayCount * 0.012f) {
            val v = (grayValue / grayCount).coerceIn(0.45f, 0.85f)
            val tone = Color(android.graphics.Color.HSVToColor(floatArrayOf(36f, 0.12f, v)))
            return listOf(tone, tone)
        }
        fun color(b: Int): Color {
            val hue = ((Math.toDegrees(Math.atan2(hueY[b].toDouble(), hueX[b].toDouble())) + 360.0) % 360.0).toFloat()
            return tune(hue, sat[b] / weight[b], value[b] / weight[b])
        }
        val first = weight.indices.maxByOrNull { weight[it] } ?: return null
        val second = weight.indices
            .filter { val d = Math.abs(it - first); minOf(d, bins - d) >= 2 && weight[it] > weight[first] * 0.18f }
            .maxByOrNull { weight[it] }
        val main = color(first)
        return listOf(main, second?.let { color(it) } ?: main)
    }
}

@Composable
fun rememberCoverAura(file: File?, enabled: Boolean = true): State<List<Color>?> =
    produceState(if (enabled) CoverAura.peek(file) else null, file, enabled) {
        if (!enabled) {
            value = null
            return@produceState
        }
        repeat(8) {
            val found = withContext(Dispatchers.IO) { CoverAura.load(file) }
            if (found != null) {
                value = found
                return@produceState
            }
            kotlinx.coroutines.delay(250)
        }
    }
