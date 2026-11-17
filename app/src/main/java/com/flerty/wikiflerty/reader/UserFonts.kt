package com.flerty.wikiflerty.reader

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object UserFonts {
    const val PATH = "wf-user-fonts/"
    private const val LIMIT = 30L * 1024 * 1024

    private val _entries = MutableStateFlow<List<FontEntry>>(emptyList())
    val entries: StateFlow<List<FontEntry>> = _entries.asStateFlow()
    private var loaded = false

    fun dir(context: Context): File = File(context.filesDir, "user_fonts").apply { mkdirs() }

    private fun index(context: Context) = File(dir(context), "index.json")

    @Synchronized
    fun list(context: Context): List<FontEntry> {
        if (!loaded) {
            loaded = true
            _entries.value = runCatching {
                val arr = JSONArray(index(context).readText())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val file = File(dir(context), o.getString("file"))
                    if (file.isFile) entry(o.getString("id"), o.getString("title"), file) else null
                }
            }.getOrDefault(emptyList())
        }
        return _entries.value
    }

    private fun entry(id: String, title: String, file: File) = FontEntry(
        id = "user:$id",
        title = title,
        category = FontCategory.Own,
        css = "WF User $id",
        file = file.absolutePath,
        variable = true,
    )

    @Synchronized
    private fun save(context: Context, list: List<FontEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("id", e.id.removePrefix("user:")).put("title", e.title).put("file", File(e.file!!).name))
        }
        index(context).writeText(arr.toString())
        _entries.value = list
    }

    fun add(context: Context, uri: Uri): Result<FontEntry> = runCatching {
        list(context)
        val shownName = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > LIMIT) throw IllegalArgumentException("Файл шрифта слишком большой")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("Не удалось прочитать файл")
        val ext = kindOf(bytes) ?: throw IllegalArgumentException("Это не шрифт. Подойдут файлы TTF и OTF")
        val id = java.lang.Long.toHexString(System.currentTimeMillis()) + Integer.toHexString((bytes.contentHashCode() and 0xFFFF))
        val file = File(dir(context), "$id.$ext")
        file.writeBytes(bytes)
        val ok = runCatching { Typeface.createFromFile(file) }.getOrNull()
        if (ok == null || ok == Typeface.DEFAULT) {
            file.delete()
            throw IllegalArgumentException("Шрифт повреждён или не поддерживается")
        }
        val title = nameOf(bytes)
            ?: shownName?.substringBeforeLast('.')?.replace('_', ' ')?.replace('-', ' ')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Мой шрифт"
        val same = _entries.value.firstOrNull { it.title.equals(title, true) && File(it.file!!).length() == file.length() }
        if (same != null) {
            file.delete()
            return@runCatching same
        }
        val e = entry(id, title, file)
        save(context, _entries.value + e)
        e
    }

    fun remove(context: Context, entry: FontEntry) {
        val list = list(context)
        entry.file?.let { File(it).delete() }
        save(context, list.filter { it.id != entry.id })
    }

    fun css(context: Context, origin: String = ""): String = list(context).joinToString("\n") { e ->
        val name = File(e.file!!).name
        "@font-face { font-family: \"${e.css}\"; src: url(\"$origin/$PATH$name\"); font-weight: 100 900; font-style: normal; }"
    }

    fun file(context: Context, url: String): File? {
        val at = url.indexOf(PATH)
        if (at < 0) return null
        val name = url.substring(at + PATH.length).substringBefore('?').substringBefore('#')
        if (name.isEmpty() || name.contains('/') || name.contains("..")) return null
        return File(dir(context), name).takeIf { it.isFile }
    }

    private fun kindOf(b: ByteArray): String? {
        if (b.size < 12) return null
        val tag = String(b, 0, 4, Charsets.ISO_8859_1)
        return when {
            tag == "OTTO" -> "otf"
            tag == "true" || (b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0) -> "ttf"
            else -> null
        }
    }

    private fun nameOf(b: ByteArray): String? = runCatching {
        fun u16(i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = (u16(i).toLong() shl 16) or u16(i + 2).toLong()
        val tables = u16(4)
        var nameAt = -1
        for (t in 0 until tables) {
            val rec = 12 + t * 16
            if (String(b, rec, 4, Charsets.ISO_8859_1) == "name") nameAt = u32(rec + 8).toInt()
        }
        if (nameAt < 0) return null
        val count = u16(nameAt + 2)
        val strings = nameAt + u16(nameAt + 4)
        val found = HashMap<Int, String>()
        for (i in 0 until count) {
            val r = nameAt + 6 + i * 12
            val platform = u16(r)
            val nameId = u16(r + 6)
            if (nameId != 1 && nameId != 4 && nameId != 16) continue
            val len = u16(r + 8)
            val off = strings + u16(r + 10)
            if (off + len > b.size) continue
            val text = when (platform) {
                3, 0 -> String(b, off, len, Charsets.UTF_16BE)
                1 -> String(b, off, len, Charsets.ISO_8859_1)
                else -> continue
            }.trim()
            if (text.isNotEmpty() && (platform == 3 || nameId !in found)) found[nameId] = text
        }
        (found[16] ?: found[1] ?: found[4])?.take(60)
    }.getOrNull()
}
