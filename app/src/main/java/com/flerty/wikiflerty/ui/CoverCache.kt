package com.flerty.wikiflerty.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

object CoverCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun load(file: File, maxWidth: Int = 420): ImageBitmap? {
        if (!file.exists()) return null
        val key = "${file.path}:${file.lastModified()}:$maxWidth"
        cache.get(key)?.let {
            latest.put("${file.path}:$maxWidth", it)
            return it
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap() ?: return null
        bitmap.prepareToDraw()
        cache.put(key, bitmap)
        latest.put("${file.path}:$maxWidth", bitmap)
        return bitmap
    }

    private val latest = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun peek(file: File?, maxWidth: Int = 420): ImageBitmap? = file?.let { latest.get("${it.path}:$maxWidth") }

    private val tones = LruCache<String, List<androidx.compose.ui.graphics.Color>>(400)

    fun peekTones(file: File?): List<androidx.compose.ui.graphics.Color>? = file?.let { tones.get(it.path) }

    fun putTones(file: File, value: List<androidx.compose.ui.graphics.Color>) {
        tones.put(file.path, value)
    }
}
