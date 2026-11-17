package com.flerty.wikiflerty.studio

import org.json.JSONArray
import org.json.JSONObject

class Step(val json: JSONObject) {
    val kind: String = json.optString("do")
    val text: String? = json.optString("text").takeIf { it.isNotEmpty() }
    val optional: Boolean = json.optBoolean("optional", false)
    val timeout: Long = json.optLong("timeout", 12_000)
    val index: Int = json.optInt("index", 0)
    val exact: Boolean = json.optBoolean("exact", false)
    val ms: Long = json.optLong("ms", 0)
    val x: Float = json.optDouble("x", 0.5).toFloat()
    val y: Float = json.optDouble("y", 0.5).toFloat()
    val x2: Float = json.optDouble("x2", 0.5).toFloat()
    val y2: Float = json.optDouble("y2", 0.5).toFloat()
    val times: Int = json.optInt("times", 1)
    val dx: Float = json.optDouble("dx", 0.0).toFloat()
    val dy: Float = json.optDouble("dy", 0.0).toFloat()
    val after: Long = json.optLong("after", 700)
    val within: String? = json.optString("within").takeIf { it.isNotEmpty() }

    fun label(): String = buildString {
        append(kind)
        text?.let { append(" «").append(it).append("»") }
    }
}

class Scenario(val id: String, val title: String, val note: String, val steps: List<Step>) {
    companion object {
        fun parse(id: String, raw: String): Scenario? = runCatching {
            val o = JSONObject(raw)
            val arr: JSONArray = o.getJSONArray("steps")
            Scenario(
                id = id,
                title = o.optString("title", id),
                note = o.optString("note", ""),
                steps = (0 until arr.length()).map { Step(arr.getJSONObject(it)) },
            )
        }.getOrNull()
    }
}
