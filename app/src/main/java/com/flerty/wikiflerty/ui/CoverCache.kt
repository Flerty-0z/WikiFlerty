package com.flerty.wikiflerty.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Book covers, decoded once at the size they are shown and kept in memory,
 * so scrolling the library does not decode the same picture again and again.
 */
object CoverCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Call from a background thread. [maxWidth] is in pixels. */
    fun load(file: File, maxWidth: Int = 420): ImageBitmap? {
        if (!file.exists()) return null
        val key = "${file.path}:${file.lastModified()}:$maxWidth"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}
