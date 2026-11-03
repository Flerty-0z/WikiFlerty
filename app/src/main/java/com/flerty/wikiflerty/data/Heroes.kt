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

    fun books(): List<String> = dir.listFiles()
        ?.filter { it.name.endsWith(".json") && !it.name.endsWith("_chat.json") }
        ?.map { it.name.removeSuffix(".json") }
        .orEmpty()

    fun snapshot(bookId: String): JSONObject? {
        val heroes = load(bookId)
        if (heroes.isEmpty()) return null
        val chats = JSONObject()
        heroes.forEach { h ->
            val chat = loadChat(bookId, h.id)
            if (chat.isNotEmpty()) chats.put(h.id, messagesJson(chat))
        }
        val authorChat = loadChat(bookId, Hero.AUTHOR_ID)
        if (authorChat.isNotEmpty()) chats.put(Hero.AUTHOR_ID, messagesJson(authorChat))
        return JSONObject().put("heroes", JSONArray().apply { heroes.forEach { put(it.toJson()) } }).put("chats", chats)
    }

    fun merge(bookId: String, data: JSONObject): Int {
        val incoming = data.optJSONArray("heroes")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Hero::fromJson) } }.orEmpty()
        val chats = data.optJSONObject("chats") ?: JSONObject()
        val local = load(bookId).toMutableList()
        var changed = 0
        val idMap = mutableMapOf<String, String>()
        incoming.filter { it.name.isNotBlank() }.forEach { h ->
            val same = local.firstOrNull { it.id == h.id || it.name.equals(h.name, true) }
            if (same == null) {
                local.add(h)
                idMap[h.id] = h.id
                changed++
            } else {
                idMap[h.id] = same.id
            }
        }
        if (changed > 0) save(bookId, local)
        val ids = chats.keys().asSequence().toList()
        ids.forEach { remoteId ->
            val localId = if (remoteId == Hero.AUTHOR_ID) Hero.AUTHOR_ID else idMap[remoteId] ?: return@forEach
            val remote = messagesFrom(chats.optJSONArray(remoteId))
            if (remote.size > loadChat(bookId, localId).size) {
                saveChat(bookId, localId, remote)
                changed++
            }
        }
        return changed
    }

    fun rename(from: String, to: String) {
        val data = snapshot(from) ?: return
        merge(to, data)
        load(from).forEach { h ->
            val old = avatar(from, h.id)
            val target = load(to).firstOrNull { it.id == h.id || it.name.equals(h.name, true) }?.id ?: h.id
            if (old.exists() && !avatar(to, target).exists()) old.copyTo(avatar(to, target))
            old.delete()
            chatFile(from, h.id).delete()
        }
        chatFile(from, Hero.AUTHOR_ID).delete()
        file(from).delete()
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
