package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class MapPerson(val name: String, val role: String, val group: String, val about: String, val fresh: Boolean = false) {
    val key: String get() = name.trim().lowercase()
}

data class MapLink(val from: String, val to: String, val label: String, val fresh: Boolean = false)

data class CharacterMap(
    val people: List<MapPerson>,
    val links: List<MapLink>,
    val place: String,
    val progression: Double,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("people", JSONArray().apply {
            people.forEach { put(JSONObject().put("name", it.name).put("role", it.role).put("group", it.group).put("about", it.about).put("fresh", it.fresh)) }
        })
        .put("links", JSONArray().apply {
            links.forEach { put(JSONObject().put("from", it.from).put("to", it.to).put("label", it.label).put("fresh", it.fresh)) }
        })
        .put("place", place)
        .put("progression", progression)
        .put("updatedAt", updatedAt)

    fun mergedWith(previous: CharacterMap?): CharacterMap {
        if (previous == null) return this
        val oldPeople = previous.people.map { it.key }.toSet()
        val oldLinks = previous.links.map { linkKey(it) }.toSet()
        return copy(
            people = people.map { it.copy(fresh = it.key !in oldPeople) },
            links = links.map { it.copy(fresh = linkKey(it) !in oldLinks) },
        )
    }

    companion object {
        private fun linkKey(l: MapLink) = listOf(l.from.lowercase().trim(), l.to.lowercase().trim()).sorted().joinToString("|")

        fun fromJson(j: JSONObject): CharacterMap {
            val p = j.optJSONArray("people") ?: JSONArray()
            val l = j.optJSONArray("links") ?: JSONArray()
            return CharacterMap(
                people = (0 until p.length()).mapNotNull { i ->
                    val o = p.optJSONObject(i) ?: return@mapNotNull null
                    val name = o.optString("name").trim()
                    if (name.isEmpty()) null else MapPerson(name, o.optString("role").trim(), o.optString("group").trim(), o.optString("about").trim(), o.optBoolean("fresh"))
                }.distinctBy { it.key },
                links = (0 until l.length()).mapNotNull { i ->
                    val o = l.optJSONObject(i) ?: return@mapNotNull null
                    MapLink(o.optString("from").trim(), o.optString("to").trim(), o.optString("label").trim(), o.optBoolean("fresh"))
                },
                place = j.optString("place"),
                progression = j.optDouble("progression", 0.0),
                updatedAt = j.optLong("updatedAt"),
            ).cleaned()
        }

        fun parse(raw: String, place: String, progression: Double): CharacterMap? = runCatching {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return@runCatching null
            fromJson(JSONObject(raw.substring(start, end + 1)).put("place", place).put("progression", progression).put("updatedAt", System.currentTimeMillis()))
        }.getOrNull()?.takeIf { it.people.isNotEmpty() }

        private fun CharacterMap.cleaned(): CharacterMap {
            val names = people.associateBy { it.key }
            val links = links
                .filter { it.from.lowercase() in names && it.to.lowercase() in names && !it.from.equals(it.to, true) }
                .map { it.copy(from = names.getValue(it.from.lowercase()).name, to = names.getValue(it.to.lowercase()).name) }
                .distinctBy { listOf(it.from, it.to).sorted() }
            return copy(people = people.take(16), links = links)
        }
    }
}

class CharacterMapStore(context: Context) {
    private val dir = File(context.filesDir, "charmap").apply { mkdirs() }

    fun load(bookId: String): CharacterMap? = runCatching {
        val f = File(dir, "$bookId.json")
        if (f.exists()) CharacterMap.fromJson(JSONObject(f.readText())) else null
    }.getOrNull()

    fun save(bookId: String, map: CharacterMap) {
        runCatching { File(dir, "$bookId.json").writeText(map.toJson().toString()) }
    }
}
