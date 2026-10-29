package com.flerty.wikiflerty.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Hero(val id: String, val name: String, val role: String, val custom: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("role", role).put("custom", custom)

    companion object {
        const val AUTHOR_ID = "author"

        fun fromJson(j: JSONObject) = Hero(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = j.optString("name"),
            role = j.optString("role"),
            custom = j.optBoolean("custom", false),
        )
    }
}

class HeroStore(private val context: Context) {
    private val dir = File(context.filesDir, "heroes").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("heroes", Context.MODE_PRIVATE)

    var noSpoilers: Boolean
        get() = prefs.getBoolean("no_spoilers", true)
        set(value) = prefs.edit().putBoolean("no_spoilers", value).apply()

    private fun file(bookId: String) = File(dir, "$bookId.json")

    fun load(bookId: String): List<Hero> = runCatching {
        val f = file(bookId)
        if (!f.exists()) return@runCatching emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { Hero.fromJson(arr.getJSONObject(it)) }.filter { it.name.isNotBlank() }
    }.getOrElse { emptyList() }

    fun save(bookId: String, heroes: List<Hero>) {
        runCatching {
            val arr = JSONArray()
            heroes.forEach { arr.put(it.toJson()) }
            file(bookId).writeText(arr.toString())
        }
    }

    fun avatar(bookId: String, heroId: String): File = File(dir, "${bookId}_$heroId.jpg")

    private fun chatFile(bookId: String, heroId: String) = File(dir, "${bookId}_${heroId}_chat.json")

    fun loadChat(bookId: String, heroId: String): List<AiMessage> = runCatching {
        val f = chatFile(bookId, heroId)
        if (f.exists()) messagesFrom(JSONArray(f.readText())) else emptyList()
    }.getOrElse { emptyList() }

    fun saveChat(bookId: String, heroId: String, messages: List<AiMessage>) {
        runCatching { chatFile(bookId, heroId).writeText(messagesJson(messages.takeLast(200)).toString()) }
    }

    fun clearChat(bookId: String, heroId: String) {
        chatFile(bookId, heroId).delete()
    }

    fun setAvatar(bookId: String, heroId: String, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 360) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > 360) Bitmap.createScaledBitmap(square, 360, 360, true) else square
        avatar(bookId, heroId).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    fun removeAvatar(bookId: String, heroId: String) {
        avatar(bookId, heroId).delete()
        chatFile(bookId, heroId).delete()
    }

    companion object {
        fun parse(raw: String): List<Hero> = runCatching {
            val start = raw.indexOf('[')
            val end = raw.lastIndexOf(']')
            if (start < 0 || end <= start) return@runCatching emptyList()
            val arr = JSONArray(raw.substring(start, end + 1))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                if (name.isEmpty()) null else Hero(UUID.randomUUID().toString(), name, o.optString("role").trim())
            }.distinctBy { it.name.lowercase() }.take(10)
        }.getOrElse { emptyList() }
    }
}
