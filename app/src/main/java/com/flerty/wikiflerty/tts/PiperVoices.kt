package com.flerty.wikiflerty.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

data class PiperVoice(val id: String, val title: String, val hint: String, val sizeMb: Int)

object PiperVoices {
    val all = listOf(
        PiperVoice("ru_RU-irina-medium", "Ирина", "Женский · русский", 64),
        PiperVoice("ru_RU-dmitri-medium", "Дмитрий", "Мужской · русский", 64),
        PiperVoice("ru_RU-denis-medium", "Денис", "Мужской · русский", 64),
        PiperVoice("ru_RU-ruslan-medium", "Руслан", "Мужской · русский", 64),
        PiperVoice("en_US-lessac-medium", "Lessac", "Мужской · английский", 64),
        PiperVoice("en_US-amy-medium", "Amy", "Женский · английский", 64),
    )

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    @Volatile private var supportedCache: Boolean? = null

    fun supported(): Boolean {
        supportedCache?.let { return it }
        val ok = runCatching { System.loadLibrary("sherpa-onnx-jni"); true }.getOrElse { false }
        supportedCache = ok
        return ok
    }

    private fun root(context: Context) = File(context.filesDir, "piper").apply { mkdirs() }

    fun dir(context: Context, id: String) = File(root(context), id)

    fun isInstalled(context: Context, id: String): Boolean =
        File(dir(context, id), ".ready").exists()

    fun refresh(context: Context) {
        _installed.value = all.filter { isInstalled(context, it.id) }.map { it.id }.toSet()
    }

    fun delete(context: Context, id: String) {
        dir(context, id).deleteRecursively()
        engines.remove(id)?.runCatching { release() }
        refresh(context)
    }

    suspend fun download(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        if (_progress.value.containsKey(id)) return@withContext false
        _progress.value = _progress.value + (id to 0f)
        val target = dir(context, id)
        val tmp = File(root(context), "$id.part").apply { deleteRecursively(); mkdirs() }
        try {
            val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$id.tar.bz2"
            var conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            var hops = 0
            while (conn.responseCode in 300..399 && hops < 5) {
                val next = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = URL(URL(url), next).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                hops++
            }
            if (conn.responseCode !in 200..299) return@withContext false
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: (64L shl 20)
            var read = 0L
            val counting = object : java.io.FilterInputStream(conn.inputStream.buffered(1 shl 16)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) {
                        read += n
                        _progress.value = _progress.value + (id to (read.toFloat() / total).coerceIn(0f, 0.99f))
                    }
                    return n
                }
            }
            TarArchiveInputStream(BZip2CompressorInputStream(counting)).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val name = entry.name.substringAfter('/', "")
                    if (name.isBlank() || name.contains("..")) continue
                    val out = File(tmp, name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                }
            }
            if (tmp.walkTopDown().none { it.name.endsWith(".onnx") }) return@withContext false
            target.deleteRecursively()
            if (!tmp.renameTo(target)) {
                tmp.copyRecursively(target, overwrite = true)
                tmp.deleteRecursively()
            }
            File(target, ".ready").writeText("1")
            true
        } catch (e: Exception) {
            false
        } finally {
            tmp.deleteRecursively()
            _progress.value = _progress.value - id
            refresh(context)
        }
    }

    private val engines = java.util.concurrent.ConcurrentHashMap<String, OfflineTts>()

    @Synchronized
    fun engine(context: Context, id: String): OfflineTts? {
        engines[id]?.let { return it }
        if (!supported() || !isInstalled(context, id)) return null
        val d = dir(context, id)
        val model = d.walkTopDown().firstOrNull { it.name.endsWith(".onnx") } ?: return null
        val tokens = File(d, "tokens.txt")
        val data = File(d, "espeak-ng-data")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = model.path, tokens = tokens.path, dataDir = data.path),
                numThreads = 2,
            ),
            maxNumSentences = 1,
        )
        val tts = runCatching { OfflineTts(null, config) }.getOrNull() ?: return null
        engines.keys.toList().forEach { k -> engines.remove(k)?.runCatching { release() } }
        engines[id] = tts
        return tts
    }
}
