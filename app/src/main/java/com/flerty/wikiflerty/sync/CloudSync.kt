package com.flerty.wikiflerty.sync

import android.content.Context
import android.util.Base64
import com.flerty.wikiflerty.App
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CloudSync(private val app: App) {
    data class Status(
        val code: String? = null,
        val running: Boolean = false,
        val lastSync: Long = 0,
        val error: String? = null,
        val lastReport: String? = null,
    ) {
        val enabled: Boolean get() = code != null
    }

    class WrongCode : Exception("Код не подходит")
    class UnknownCode : Exception("Такого кода нет. Проверь, что он введён без ошибок")
    private class Conflict : Exception()

    private val prefs = app.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(
        Status(
            code = prefs.getString("code", null),
            lastSync = prefs.getLong("lastSync", 0),
            error = prefs.getString("error", null),
        ),
    )
    val status: StateFlow<Status> = _status.asStateFlow()
    private val mutex = Mutex()
    private var pending: Job? = null

    fun newCode(): String {
        val random = SecureRandom()
        return format(String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))
    }

    fun normalize(input: String): String? {
        val clean = input.uppercase()
            .replace('O', '0').replace('I', '1').replace('L', '1').replace('U', 'V')
            .filter { it in ALPHABET }
        return if (clean.length == LENGTH) format(clean) else null
    }

    private fun format(raw: String) = raw.chunked(5).joinToString("-")

    suspend fun start(code: String, existing: Boolean): Result<MergeReport> {
        val previous = _status.value.code
        setCode(code)
        val result = runSync(requireExisting = existing)
        if (result.isFailure && previous == null) {
            val error = result.exceptionOrNull()
            if (error is WrongCode || error is UnknownCode) setCode(null)
        }
        return result
    }

    fun stop() {
        pending?.cancel()
        setCode(null)
        prefs.edit().remove("seenGens").remove("pushedParts").remove("uploaded").remove("remoteImages").remove("error").apply()
        _status.value = _status.value.copy(error = null, lastReport = null)
    }

    suspend fun forget(): Result<Unit> = withContext(Dispatchers.IO) {
        val code = _status.value.code ?: return@withContext Result.success(Unit)
        runCatching {
            mutex.withLock {
                val space = space(code)
                get(space, HEAD)?.let { head ->
                    parseHead(head.blob).values.forEach { ref -> for (i in 0 until ref.n) delete(space, chunk(ref.gen, i)) }
                    delete(space, HEAD)
                }
                Unit
            }
        }.also { if (it.isSuccess) stop() }
    }

    fun request(delayMs: Long = 1500) {
        if (!_status.value.enabled) return
        if (pending?.isActive == true) return
        pending = app.appScope.launch {
            delay(delayMs)
            runSync(requireExisting = false)
        }
    }

    suspend fun syncNow(): Result<MergeReport> = runSync(requireExisting = false)

    private fun setCode(code: String?) {
        prefs.edit().apply { if (code == null) remove("code") else putString("code", code) }.apply()
        if (code != _status.value.code) prefs.edit().remove("seenGens").remove("pushedParts").remove("uploaded").remove("remoteImages").apply()
        _status.value = _status.value.copy(code = code)
    }

    private suspend fun runSync(requireExisting: Boolean): Result<MergeReport> = withContext(Dispatchers.IO) {
        val code = _status.value.code ?: return@withContext Result.failure(IllegalStateException("Синхронизация выключена"))
        _status.value = _status.value.copy(running = true)
        val result = runCatching { mutex.withLock { exchange(code, requireExisting) } }
        val now = System.currentTimeMillis()
        val error = result.exceptionOrNull()?.let { describe(it) }
        if (error == null) prefs.edit().putLong("lastSync", now).remove("error").apply() else prefs.edit().putString("error", error).apply()
        _status.value = _status.value.copy(
            running = false,
            lastSync = if (error == null) now else _status.value.lastSync,
            error = error,
            lastReport = result.getOrNull()?.describe() ?: _status.value.lastReport,
        )
        result
    }

    private fun describe(e: Throwable): String = when (e) {
        is WrongCode, is UnknownCode -> e.message.orEmpty()
        is IOException -> "Нет связи с сервером. Попробую позже"
        else -> "Не получилось синхронизировать"
    }

    private class PartRef(val gen: String, val n: Int)

    private fun parseHead(blob: String): Map<String, PartRef> {
        val meta = JSONObject(blob)
        val parts = meta.optJSONObject("parts")
        if (parts == null) {
            val gen = meta.optString("gen")
            return if (gen.isBlank()) emptyMap() else mapOf(LEGACY to PartRef(gen, meta.optInt("n")))
        }
        return parts.keys().asSequence().toList().mapNotNull { k ->
            val j = parts.optJSONObject(k) ?: return@mapNotNull null
            k to PartRef(j.optString("gen"), j.optInt("n"))
        }.toMap()
    }

    private fun headJson(parts: Map<String, PartRef>): String {
        val j = JSONObject()
        parts.forEach { (k, r) -> j.put(k, JSONObject().put("gen", r.gen).put("n", r.n)) }
        return JSONObject().put("v", 2).put("parts", j).toString()
    }

    private fun readMap(name: String): MutableMap<String, String> {
        val j = runCatching { JSONObject(prefs.getString(name, null) ?: "{}") }.getOrElse { JSONObject() }
        return j.keys().asSequence().associateWith { j.optString(it) }.toMutableMap()
    }

    private fun writeMap(name: String, map: Map<String, String>) {
        prefs.edit().putString(name, JSONObject(map).toString()).apply()
    }

    private fun bucket(fingerprint: String): String = "b" + hex(sha("bucket", fingerprint)).first()

    private fun split(snapshot: JSONObject, images: JSONObject): Map<String, JSONObject> {
        val out = linkedMapOf<String, JSONObject>()
        val books = snapshot.optJSONArray("books") ?: org.json.JSONArray()
        for (i in 0 until books.length()) {
            val b = books.optJSONObject(i) ?: continue
            val fp = b.optString("fingerprint").ifBlank { continue }
            val part = out.getOrPut(bucket(fp)) { JSONObject().put("books", org.json.JSONArray()) }
            part.getJSONArray("books").put(b)
        }
        fun group(name: String, vararg keys: String) {
            val j = JSONObject()
            keys.forEach { k -> if (snapshot.has(k)) j.put(k, snapshot.get(k)) }
            out[name] = j
        }
        group("notes", "notes", "deletedNotes")
        group("marks", "bookmarks", "deletedBookmarks")
        group("words", "dictionary", "deletedWords")
        group("stats", "stats", "achievements", "freezes", "paperSessions")
        group("ai", "aiHistory", "heroes")
        group("settings", "settings")
        out["images"] = JSONObject().put("images", images)
        return out
    }

    private fun combinedImages(code: String, space: String, key: SecretKeySpec, upload: Boolean): JSONObject {
        val remoteImages = runCatching { JSONObject(prefs.getString("remoteImages", null) ?: "{}") }.getOrElse { JSONObject() }
        val local = localImages()
        if (upload) uploadImages(space, key, code, local)
        val images = JSONObject(remoteImages.toString())
        local.forEach { (k, entry) ->
            val known = images.optJSONObject(k)
            if (known == null || entry.json.optLong("at") >= known.optLong("at")) images.put(k, entry.json)
        }
        return images
    }

    private fun localParts(code: String, space: String, key: SecretKeySpec, upload: Boolean): Map<String, JSONObject> =
        split(Backup.snapshot(app), combinedImages(code, space, key, upload))

    private fun hashes(parts: Map<String, JSONObject>) = parts.mapValues { hex(sha("part", it.value.toString())) }

    private suspend fun exchange(code: String, requireExisting: Boolean): MergeReport {
        val space = space(code)
        val key = key(code)
        var report = MergeReport()
        repeat(4) {
            val head = get(space, HEAD)
            if (head == null && requireExisting) throw UnknownCode()
            val remote = head?.let { parseHead(it.blob) }.orEmpty()
            val seen = readMap("seenGens")
            val pushed = readMap("pushedParts")
            val fetch = remote.filter { (name, ref) -> seen[name] != ref.gen }

            if (fetch.isNotEmpty()) {
                val before = hashes(localParts(code, space, key, upload = false))
                val merged = JSONObject().put("format", "wikiflerty").put("version", 1)
                for ((name, ref) in fetch) {
                    val text = StringBuilder()
                    for (i in 0 until ref.n) {
                        val part = get(space, chunk(ref.gen, i)) ?: return@repeat
                        text.append(part.blob)
                    }
                    val json = JSONObject(unpack(text.toString(), key))
                    json.keys().asSequence().toList().forEach { k ->
                        if (k == "books") {
                            val all = merged.optJSONArray("books") ?: org.json.JSONArray().also { merged.put("books", it) }
                            val arr = json.getJSONArray("books")
                            for (i in 0 until arr.length()) all.put(arr.get(i))
                        } else {
                            merged.put(k, json.get(k))
                        }
                    }
                }
                report += Backup.merge(app, merged, applySettings = app.sync.store.state.value.syncSettings)
                merged.optJSONObject("images")?.let { prefs.edit().putString("remoteImages", it.toString()).apply() }
                applyImages(space, key, code, runCatching { JSONObject(prefs.getString("remoteImages", null) ?: "{}") }.getOrElse { JSONObject() })
                val after = hashes(localParts(code, space, key, upload = false))
                fetch.keys.forEach { name ->
                    if (name != LEGACY && before[name] == pushed[name] && after[name] != null) pushed[name] = after[name]!!
                    seen[name] = fetch.getValue(name).gen
                }
                writeMap("pushedParts", pushed)
                writeMap("seenGens", seen)
            } else {
                applyImages(space, key, code, runCatching { JSONObject(prefs.getString("remoteImages", null) ?: "{}") }.getOrElse { JSONObject() })
            }

            val parts = localParts(code, space, key, upload = true)
            val now = hashes(parts)
            val changed = parts.keys.filter { name -> now[name] != pushed[name] || remote[name] == null }
            if (changed.isEmpty() && LEGACY !in remote) return report

            val next = remote.filterKeys { it != LEGACY }.toMutableMap()
            val written = mutableListOf<Pair<String, PartRef>>()
            changed.forEach { name ->
                val blob = pack(parts.getValue(name).toString(), key)
                val gen = java.lang.Long.toString(System.currentTimeMillis(), 36) + (1000 + SecureRandom().nextInt(9000))
                val chunks = blob.chunked(CHUNK)
                chunks.forEachIndexed { i, c -> put(space, chunk(gen, i), c, Precondition.None) }
                val ref = PartRef(gen, chunks.size)
                next[name] = ref
                written += name to ref
            }
            next.keys.retainAll(parts.keys)
            try {
                put(space, HEAD, headJson(next), if (head == null) Precondition.Missing else Precondition.At(head.time))
            } catch (e: Conflict) {
                written.forEach { (_, ref) -> for (i in 0 until ref.n) runCatching { delete(space, chunk(ref.gen, i)) } }
                return@repeat
            }
            remote.forEach { (name, old) ->
                if (next[name]?.gen != old.gen) for (i in 0 until old.n) runCatching { delete(space, chunk(old.gen, i)) }
            }
            written.forEach { (name, ref) ->
                pushed[name] = now.getValue(name)
                seen[name] = ref.gen
            }
            seen.keys.retainAll(next.keys)
            writeMap("pushedParts", pushed)
            writeMap("seenGens", seen)
            return report
        }
        throw IOException("busy")
    }

    private class LocalImage(val json: JSONObject, val file: java.io.File)

    private fun seconds(ms: Long) = ms / 1000 * 1000

    private fun localImages(): Map<String, LocalImage> {
        val out = mutableMapOf<String, LocalImage>()
        val books = app.library.books.value
        books.forEach { b ->
            val fp = b.fingerprint ?: return@forEach
            val file = app.library.customCover(b) ?: return@forEach
            out["c|$fp"] = LocalImage(JSONObject().put("kind", "c").put("fp", fp).put("at", seconds(file.lastModified())), file)
        }
        val fpById = books.filter { it.fingerprint != null }.associate { it.id to it.fingerprint!! }
        app.heroes.books().forEach { bookId ->
            val fp = fpById[bookId] ?: bookId.takeIf { it.startsWith("fp:") }?.removePrefix("fp:") ?: return@forEach
            app.heroes.load(bookId).forEach { h ->
                val file = app.heroes.avatar(bookId, h.id)
                if (!file.exists()) return@forEach
                val name = h.name.trim().lowercase()
                out["a|$fp|$name"] = LocalImage(
                    JSONObject().put("kind", "a").put("fp", fp).put("hero", name).put("at", seconds(file.lastModified())),
                    file,
                )
            }
        }
        return out
    }

    private fun imageName(code: String, entry: String, at: Long) = "i" + hex(sha("wikiflerty-img|$code", "$entry|$at")).take(24)

    private fun uploadImages(space: String, key: SecretKeySpec, code: String, local: Map<String, LocalImage>) {
        val done = prefs.getStringSet("uploaded", emptySet()).orEmpty().toMutableSet()
        var changed = false
        local.forEach { (k, img) ->
            val name = imageName(code, k, img.json.optLong("at"))
            if (name in done) return@forEach
            val bytes = if (img.json.optString("kind") == "c") shrink(img.file) else img.file.readBytes()
            if (bytes == null) return@forEach
            val blob = encrypt(bytes, key)
            if (blob.length >= CHUNK) return@forEach
            put(space, name, blob, Precondition.None)
            done += name
            changed = true
        }
        if (changed) prefs.edit().putStringSet("uploaded", done).apply()
    }

    private suspend fun applyImages(space: String, key: SecretKeySpec, code: String, remote: JSONObject) {
        val books = app.library.books.value
        val idByFp = books.filter { it.fingerprint != null }.associate { it.fingerprint!! to it.id }
        remote.keys().asSequence().toList().forEach { k ->
            val entry = remote.optJSONObject(k) ?: return@forEach
            val fp = entry.optString("fp").ifBlank { return@forEach }
            val at = entry.optLong("at")
            when (entry.optString("kind")) {
                "c" -> {
                    val book = books.firstOrNull { it.fingerprint == fp } ?: return@forEach
                    val current = app.library.customCover(book)?.let { seconds(it.lastModified()) } ?: -1L
                    if (at <= current) return@forEach
                    val bytes = download(space, key, imageName(code, k, at)) ?: return@forEach
                    app.library.applyCover(book.id, bytes, at)
                }
                "a" -> {
                    val bookId = idByFp[fp] ?: "fp:$fp"
                    val name = entry.optString("hero")
                    val hero = app.heroes.load(bookId).firstOrNull { it.name.trim().lowercase() == name } ?: return@forEach
                    val file = app.heroes.avatar(bookId, hero.id)
                    val current = if (file.exists()) seconds(file.lastModified()) else -1L
                    if (at <= current) return@forEach
                    val bytes = download(space, key, imageName(code, k, at)) ?: return@forEach
                    file.writeBytes(bytes)
                    file.setLastModified(at)
                }
            }
        }
    }

    private fun download(space: String, key: SecretKeySpec, name: String): ByteArray? {
        val doc = get(space, name) ?: return null
        return runCatching { decrypt(doc.blob, key) }.getOrNull()
    }

    private fun shrink(file: java.io.File): ByteArray? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= 600) sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val scaled = if (bitmap.height > 600) {
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * 600f / bitmap.height).toInt().coerceAtLeast(1), 600, true)
        } else {
            bitmap
        }
        ByteArrayOutputStream().also { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }.getOrNull()

    private fun encrypt(bytes: ByteArray, key: SecretKeySpec): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return Base64.encodeToString(iv + cipher.doFinal(bytes), Base64.NO_WRAP)
    }

    private fun decrypt(blob: String, key: SecretKeySpec): ByteArray {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12)) }
        return try {
            cipher.doFinal(bytes, 12, bytes.size - 12)
        } catch (e: AEADBadTagException) {
            throw WrongCode()
        }
    }


    private fun space(code: String) = hex(sha("wikiflerty-space", code))

    private fun key(code: String) = SecretKeySpec(sha("wikiflerty-key", code), "AES")

    private fun sha(salt: String, value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$salt:$value".toByteArray(Charsets.UTF_8))

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun pack(text: String, key: SecretKeySpec): String {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        return encrypt(zipped, key)
    }

    private fun unpack(blob: String, key: SecretKeySpec): String {
        val zipped = decrypt(blob, key)
        return GZIPInputStream(ByteArrayInputStream(zipped)).use { it.readBytes() }.toString(Charsets.UTF_8)
    }

    private class Doc(val blob: String, val time: String)

    private sealed class Precondition {
        data object None : Precondition()
        data object Missing : Precondition()
        data class At(val time: String) : Precondition()
    }

    private fun url(space: String, part: String, extra: String = "") = "$BASE$space/parts/$part?key=$KEY$extra"

    private fun get(space: String, part: String): Doc? {
        val r = call("GET", url(space, part))
        if (r.code == 404) return null
        if (r.code !in 200..299) throw IOException("HTTP ${r.code}")
        val json = JSONObject(r.body)
        val blob = json.optJSONObject("fields")?.optJSONObject("blob")?.optString("stringValue") ?: return null
        return Doc(blob, json.optString("updateTime"))
    }

    private fun put(space: String, part: String, blob: String, condition: Precondition): String {
        val write = JSONObject()
            .put(
                "update",
                JSONObject()
                    .put("name", "$NAME$space/parts/$part")
                    .put(
                        "fields",
                        JSONObject()
                            .put("blob", JSONObject().put("stringValue", blob))
                            .put("updated", JSONObject().put("integerValue", System.currentTimeMillis().toString())),
                    ),
            )
        when (condition) {
            Precondition.None -> Unit
            Precondition.Missing -> write.put("currentDocument", JSONObject().put("exists", false))
            is Precondition.At -> write.put("currentDocument", JSONObject().put("updateTime", condition.time))
        }
        val body = JSONObject().put("writes", org.json.JSONArray().put(write)).toString()
        val r = call("POST", "$COMMIT?key=$KEY", body)
        if (r.code in 200..299) {
            val json = JSONObject(r.body)
            return json.optJSONArray("writeResults")?.optJSONObject(0)?.optString("updateTime")?.takeIf { it.isNotEmpty() }
                ?: json.optString("commitTime")
        }
        if (condition != Precondition.None && (r.code == 409 || r.code == 412 || r.code == 404 || (r.code == 400 && r.body.contains("FAILED_PRECONDITION")))) throw Conflict()
        throw IOException("HTTP ${r.code}")
    }

    private fun delete(space: String, part: String) {
        val r = call("DELETE", url(space, part))
        if (r.code !in 200..299 && r.code != 404) throw IOException("HTTP ${r.code}")
    }

    private class Reply(val code: Int, val body: String)

    private fun call(method: String, address: String, body: String? = null): Reply {
        val c = URL(address).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.requestMethod = method
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Reply(code, text)
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LENGTH = 25
        const val CHUNK = 700_000
        const val HEAD = "head"
        const val LEGACY = "all"
        const val KEY = "AIzaSyCu6eR2CIY4EKqllpI8AJ6NfX1urr_klYM"
        const val BASE = "https://firestore.googleapis.com/v1/projects/wikiflerty-main/databases/(default)/documents/spaces/"
        const val NAME = "projects/wikiflerty-main/databases/(default)/documents/spaces/"
        const val COMMIT = "https://firestore.googleapis.com/v1/projects/wikiflerty-main/databases/(default)/documents:commit"

        fun chunk(gen: String, i: Int) = "d$gen-$i"
    }
}
