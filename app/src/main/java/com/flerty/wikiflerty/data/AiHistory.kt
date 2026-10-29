package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class AiMessage(val fromUser: Boolean, val shown: String, val sent: String)

data class AiRecord(
    val id: String,
    val action: String,
    val fragment: String,
    val context: String,
    val locatorJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<AiMessage>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("action", action)
        put("fragment", fragment)
        put("context", context)
        put("locator", locatorJson)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("messages", messagesJson(messages))
    }

    companion object {
        fun fromJson(j: JSONObject) = AiRecord(
            id = j.optString("id"),
            action = j.optString("action"),
            fragment = j.optString("fragment"),
            context = j.optString("context"),
            locatorJson = j.optString("locator").takeIf { it.isNotBlank() && it != "null" },
            createdAt = j.optLong("createdAt"),
            updatedAt = j.optLong("updatedAt"),
            messages = messagesFrom(j.optJSONArray("messages")),
        )
    }
}

fun messagesJson(list: List<AiMessage>): JSONArray = JSONArray().apply {
    list.forEach { put(JSONObject().put("u", it.fromUser).put("shown", it.shown).put("sent", it.sent)) }
}

fun messagesFrom(arr: JSONArray?): List<AiMessage> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        AiMessage(o.optBoolean("u"), o.optString("shown"), o.optString("sent"))
    }
}

class AiHistoryStore(context: Context) {
    private val dir = File(context.filesDir, "ai_history").apply { mkdirs() }

    private fun file(bookId: String) = File(dir, "$bookId.json")

    fun load(bookId: String): List<AiRecord> = runCatching {
        val f = file(bookId)
        if (!f.exists()) return@runCatching emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { AiRecord.fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.updatedAt }
    }.getOrElse { emptyList() }

    private fun write(bookId: String, list: List<AiRecord>) {
        runCatching {
            val arr = JSONArray()
            list.sortedByDescending { it.updatedAt }.take(150).forEach { arr.put(it.toJson()) }
            val tmp = File(dir, "$bookId.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file(bookId))) {
                file(bookId).writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    @Synchronized
    fun save(bookId: String, record: AiRecord): List<AiRecord> {
        val list = load(bookId).filter { it.id != record.id } + record
        write(bookId, list)
        return list.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun delete(bookId: String, id: String): List<AiRecord> {
        val list = load(bookId).filter { it.id != id }
        write(bookId, list)
        return list
    }

    @Synchronized
    fun clear(bookId: String) {
        file(bookId).delete()
    }
}
