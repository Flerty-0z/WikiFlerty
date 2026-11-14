package com.flerty.wikiflerty.sync

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Extras {
    private val KEYS = mapOf(
        "ambience" to listOf("mix"),
        "heroes" to listOf("no_spoilers"),
        "pdf" to listOf("paged", "filter"),
        "random_book" to listOf("all", "shelves"),
        "focus" to listOf("enabled"),
    )

    @Volatile
    var applying = false

    @Volatile
    private var appliedAt = 0L

    private fun quiet() = applying || android.os.SystemClock.uptimeMillis() - appliedAt < 3000

    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    fun watch(context: Context, onChange: () -> Unit) {
        KEYS.forEach { (name, keys) ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (!quiet() && key in keys) onChange() }
            listeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    fun snapshot(context: Context): JSONObject {
        val out = JSONObject()
        KEYS.forEach { (name, keys) ->
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val j = JSONObject()
            keys.forEach { k ->
                when (val v = all[k]) {
                    is String -> j.put(k, JSONObject().put("t", "s").put("v", v))
                    is Boolean -> j.put(k, JSONObject().put("t", "b").put("v", v))
                    is Int -> j.put(k, JSONObject().put("t", "i").put("v", v))
                    is Long -> j.put(k, JSONObject().put("t", "l").put("v", v))
                    is Float -> j.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                    is Set<*> -> j.put(k, JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }.sorted())))
                    else -> Unit
                }
            }
            if (j.length() > 0) out.put(name, j)
        }
        return out
    }

    fun apply(context: Context, remote: JSONObject) {
        applying = true
        try {
            KEYS.forEach { (name, keys) ->
                val j = remote.optJSONObject(name) ?: return@forEach
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                keys.forEach { k ->
                    val e = j.optJSONObject(k) ?: return@forEach
                    when (e.optString("t")) {
                        "s" -> edit.putString(k, e.optString("v"))
                        "b" -> edit.putBoolean(k, e.optBoolean("v"))
                        "i" -> edit.putInt(k, e.optInt("v"))
                        "l" -> edit.putLong(k, e.optLong("v"))
                        "f" -> edit.putFloat(k, e.optDouble("v").toFloat())
                        "set" -> edit.putStringSet(k, e.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty())
                    }
                }
                edit.commit()
            }
        } finally {
            appliedAt = android.os.SystemClock.uptimeMillis()
            applying = false
        }
        com.flerty.wikiflerty.system.FocusMode.load(context)
    }
}
