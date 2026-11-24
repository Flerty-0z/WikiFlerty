package com.flerty.wikiflerty.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.flerty.wikiflerty.App
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.ContentService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class SyncState(val stage: String, val progress: Float = 0f, val message: String = "")

data class SyncPoint(val locator: Locator, val ms: Long)

object AsrModel {
    private const val BASE = "https://huggingface.co/alphacep/vosk-model-small-ru/resolve/main/"
    private val FILES = listOf("am/encoder.int8.onnx" to 25_090_964L, "am/decoder.onnx" to 2_093_080L, "am/joiner.onnx" to 1_026_462L, "lang/tokens.txt" to 6_388L)
    const val SIZE_MB = 28

    fun dir(context: Context) = File(context.filesDir, "asr/vosk-small-ru")

    fun ready(context: Context) = File(dir(context), ".ready").exists()

    suspend fun download(context: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val d = dir(context).apply { mkdirs() }
        val total = FILES.sumOf { it.second }.toFloat()
        var done = 0L
        for ((path, size) in FILES) {
            val out = File(d, path.substringAfterLast('/'))
            if (out.exists() && out.length() == size) {
                done += size
                continue
            }
            val tmp = File(out.path + ".part")
            val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) throw IllegalStateException("Модель недоступна (${conn.responseCode})")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        onProgress((done / total).coerceIn(0f, 1f))
                    }
                }
            }
            tmp.renameTo(out)
        }
        File(d, ".ready").writeText("1")
    }

    fun recognizer(context: Context): OfflineRecognizer {
        val d = dir(context)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(d, "encoder.int8.onnx").path,
                    decoder = File(d, "decoder.onnx").path,
                    joiner = File(d, "joiner.onnx").path,
                ),
                tokens = File(d, "tokens.txt").path,
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                modelType = "transducer",
            ),
        )
        return OfflineRecognizer(null, config)
    }
}

object PcmDecoder {
    const val RATE = 16_000

    fun decode(context: Context, uri: Uri, chunkSeconds: Int, startMs: Long, onChunk: (FloatArray, Long) -> Boolean) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    break
                }
            }
            if (track < 0) return
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            if (startMs > 0) ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var pending = FloatArray(rate * chunkSeconds)
            var filled = 0
            var chunkStartUs = -1L
            fun flush(): Boolean {
                if (filled == 0) return true
                val src = if (filled == pending.size) pending else pending.copyOf(filled)
                val out = resample(src, rate)
                val keep = onChunk(out, (chunkStartUs / 1000).coerceAtLeast(0))
                filled = 0
                chunkStartUs = -1
                return keep
            }
            loop@ while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (filled == 0) pending = FloatArray(rate * chunkSeconds)
                    }
                    outIdx >= 0 -> {
                        val out = codec.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frames = shorts.remaining() / channels.coerceAtLeast(1)
                            if (chunkStartUs < 0) chunkStartUs = info.presentationTimeUs
                            var f = 0
                            while (f < frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += shorts.get() / 32768f
                                pending[filled++] = sum / channels
                                f++
                                if (filled == pending.size) {
                                    if (!flush()) {
                                        codec.releaseOutputBuffer(outIdx, false)
                                        break@loop
                                    }
                                    chunkStartUs = info.presentationTimeUs + f * 1_000_000L / rate
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            flush()
                            break@loop
                        }
                    }
                }
            }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        } finally {
            runCatching { ex.release() }
        }
    }

    private fun resample(src: FloatArray, rate: Int): FloatArray {
        if (rate == RATE) return src
        val n = (src.size.toLong() * RATE / rate).toInt()
        val out = FloatArray(n)
        val step = rate.toDouble() / RATE
        for (i in 0 until n) {
            val pos = i * step
            val j = pos.toInt()
            val t = (pos - j).toFloat()
            val a = src[j.coerceAtMost(src.lastIndex)]
            val b = src[(j + 1).coerceAtMost(src.lastIndex)]
            out[i] = a + (b - a) * t
        }
        return out
    }
}

object SyncAlign {
    fun norm(w: String): String = w.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }

    fun words(text: String): List<String> = text.split(Regex("[\\s\\p{Punct}«»—–…]+")).map(::norm).filter { it.isNotEmpty() }

    fun align(book: List<String>, rec: List<String>, recMs: LongArray): IntArray {
        val map = IntArray(rec.size) { -1 }
        if (book.size < 3 || rec.size < 3) return map
        val index = HashMap<String, MutableList<Int>>()
        for (i in 0 until book.size - 2) index.getOrPut(book[i] + " " + book[i + 1] + " " + book[i + 2]) { mutableListOf() }.add(i)
        var last = -1
        var j = 0
        while (j < rec.size - 2) {
            val key = rec[j] + " " + rec[j + 1] + " " + rec[j + 2]
            val list = index[key]
            var hit = -1
            if (list != null) {
                val limit = if (last < 0) Int.MAX_VALUE else last + 4000
                for (i in list) if (i > last && i <= limit) { hit = i; break }
                if (hit >= 0 && last < 0 && list.size > 3) hit = -1
            }
            if (hit >= 0) {
                map[j] = hit
                map[j + 1] = hit + 1
                map[j + 2] = hit + 2
                last = hit + 2
                j += 3
                while (j < rec.size && last + 1 < book.size && rec[j] == book[last + 1]) {
                    last++
                    map[j] = last
                    j++
                }
            } else {
                j++
            }
        }
        return map
    }
}

class AudioSync(private val app: App) {
    private val _state = MutableStateFlow<Map<String, SyncState>>(emptyMap())
    val state: StateFlow<Map<String, SyncState>> = _state.asStateFlow()
    private val jobs = HashMap<String, Job>()
    private val cache = HashMap<String, List<SyncPoint>>()

    private fun dir() = File(app.filesDir, "audiosync").apply { mkdirs() }
    private fun wordsFile(id: String) = File(dir(), "$id.words")
    private fun mapFile(id: String) = File(dir(), "$id.map.json")

    fun has(id: String) = mapFile(id).exists()

    fun coverage(id: String): Float = runCatching { JSONObject(mapFile(id).readText()).optDouble("coverage", 0.0).toFloat() }.getOrDefault(0f)

    private fun set(id: String, s: SyncState?) {
        _state.value = if (s == null) _state.value - id else _state.value + (id to s)
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        set(id, null)
    }

    fun clear(id: String) {
        cancel(id)
        wordsFile(id).delete()
        mapFile(id).delete()
        cache.remove(id)
    }

    fun points(id: String): List<SyncPoint> {
        cache[id]?.let { return it }
        val list = runCatching {
            val arr = JSONObject(mapFile(id).readText()).getJSONArray("points")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                Locator.fromJSON(o.getJSONObject("l"))?.let { SyncPoint(it, o.getLong("ms")) }
            }
        }.getOrDefault(emptyList())
        cache[id] = list
        return list
    }

    fun pointAt(id: String, globalMs: Long): SyncPoint? {
        val list = points(id)
        if (list.isEmpty()) return null
        var lo = 0
        var hi = list.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (list[mid].ms <= globalMs) lo = mid else hi = mid - 1
        }
        return list[lo].takeIf { it.ms <= globalMs + 500 }
    }

    fun msFor(id: String, at: Locator): Long? {
        val list = points(id)
        if (list.isEmpty()) return null
        val href = res(at)
        val same = list.indices.filter { res(list[it].locator) == href }
        if (same.isEmpty()) return null
        val key = SyncAlign.words(at.text.highlight.orEmpty()).take(6)
        val context = SyncAlign.words(at.text.before.orEmpty()).takeLast(10).toSet()
        var size = key.size
        while (size >= 2) {
            val k = key.take(size)
            var best = -1
            var bestAt = 0
            var bestScore = -1
            for (i in same) {
                val pw = SyncAlign.words(list[i].locator.text.highlight.orEmpty())
                val pos = find(pw, k)
                if (pos < 0) continue
                val around = (SyncAlign.words(list[i].locator.text.before.orEmpty()).takeLast(10) + pw.take(pos)).takeLast(10)
                val score = around.count { it in context }
                if (score > bestScore) {
                    best = i
                    bestAt = pos
                    bestScore = score
                }
            }
            if (best >= 0) {
                val p = list[best]
                val total = SyncAlign.words(p.locator.text.highlight.orEmpty()).size.coerceAtLeast(1)
                val next = list.getOrNull(best + 1)?.ms ?: return p.ms
                return p.ms + (next - p.ms) * bestAt / total
            }
            size = if (size > 3) 3 else size - 1
        }
        return list[same.first()].ms
    }

    private fun res(l: Locator): String = Uri.decode(l.href.toString().substringBefore('#').substringBefore('?')).trimStart('/')

    private fun find(hay: List<String>, needle: List<String>): Int {
        if (needle.isEmpty() || hay.size < needle.size) return -1
        for (i in 0..hay.size - needle.size) {
            var ok = true
            for (j in needle.indices) if (hay[i + j] != needle[j]) { ok = false; break }
            if (ok) return i
        }
        return -1
    }

    fun start(id: String) {
        if (jobs[id]?.isActive == true) return
        jobs[id] = app.appScope.launch(Dispatchers.Default) {
            val lock = (app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "wikiflerty:sync")
            try {
                lock.acquire(6 * 60 * 60 * 1000L)
                run(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                set(id, SyncState("error", 0f, if (e is LinkageError) "Распознавание речи не работает на этом телефоне" else e.message ?: "Не получилось"))
            } finally {
                runCatching { if (lock.isHeld) lock.release() }
                jobs.remove(id)
            }
        }
    }

    private suspend fun run(id: String) {
        val book = app.audiobooks.find(id) ?: return
        val textId = book.bookId ?: throw IllegalStateException("Сначала свяжи аудиокнигу с книгой")
        if (!com.flerty.wikiflerty.tts.PiperVoices.supported()) throw IllegalStateException("Распознавание речи не работает на этом телефоне")
        if (!AsrModel.ready(app)) {
            set(id, SyncState("model", 0f))
            AsrModel.download(app) { p -> set(id, SyncState("model", p)) }
        }
        set(id, SyncState("listen", 0f))
        val heard = transcribe(book)
        set(id, SyncState("match", 0f))
        val text = app.library.find(textId) ?: throw IllegalStateException("Книга не найдена в библиотеке")
        val publication = app.readium.open(app.library.bookFile(text)).getOrElse { throw IllegalStateException("Не удалось открыть книгу") }
        try {
            buildMap(id, publication, heard)
        } finally {
            runCatching { publication.close() }
        }
        set(id, SyncState("done", 1f))
    }

    private suspend fun transcribe(book: Audiobook): List<Pair<String, Long>> = withContext(Dispatchers.Default) {
        val file = wordsFile(book.id)
        val done = HashSet<Int>()
        val words = ArrayList<Pair<String, Long>>()
        if (file.exists()) {
            file.forEachLine { line ->
                if (line.startsWith("#")) {
                    line.removePrefix("#").toIntOrNull()?.let { done += it }
                } else {
                    val tab = line.indexOf('\t')
                    if (tab > 0) words += line.substring(tab + 1) to (line.substring(0, tab).toLongOrNull() ?: 0L)
                }
            }
        }
        val total = book.totalMs.coerceAtLeast(1)
        val recognizer = AsrModel.recognizer(app)
        val job = kotlin.coroutines.coroutineContext[Job]
        try {
            book.tracks.forEachIndexed { t, track ->
                if (t in done) return@forEachIndexed
                val offset = book.offsetOf(t)
                val part = ArrayList<Pair<String, Long>>()
                PcmDecoder.decode(app, Uri.parse(track.uri), 20, 0) { samples, startMs ->
                    if (job?.isActive == false) return@decode false
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(samples, PcmDecoder.RATE)
                        recognizer.decode(stream)
                        val r = recognizer.getResult(stream)
                        var cur = StringBuilder()
                        var at = 0L
                        r.tokens.forEachIndexed { k, tok ->
                            val ms = offset + startMs + ((r.timestamps.getOrNull(k) ?: 0f) * 1000).toLong()
                            if (tok.startsWith(" ") || tok.startsWith("▁") || cur.isEmpty()) {
                                if (cur.isNotEmpty()) part += cur.toString() to at
                                cur = StringBuilder()
                                at = ms
                            }
                            cur.append(tok.trimStart(' ', '▁'))
                        }
                        if (cur.isNotEmpty()) part += cur.toString() to at
                    } finally {
                        stream.release()
                    }
                    set(book.id, SyncState("listen", ((offset + startMs + 20_000).toFloat() / total).coerceIn(0f, 0.99f)))
                    true
                }
                kotlin.coroutines.coroutineContext.ensureActive()
                file.appendText(part.joinToString("") { "${it.second}\t${it.first}\n" } + "#$t\n")
                words += part
            }
        } finally {
            recognizer.release()
        }
        words
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    private suspend fun buildMap(id: String, publication: org.readium.r2.shared.publication.Publication, heard: List<Pair<String, Long>>) {
        val content = publication.findService(ContentService::class)?.content(null) ?: throw IllegalStateException("В книге не нашёлся текст")
        val iterator = content.iterator()
        val words = ArrayList<String>()
        val owners = ArrayList<Int>()
        val locators = ArrayList<Locator>()
        var guard = 0
        while (iterator.hasNext() && guard < 400_000) {
            guard++
            val el = iterator.next() as? Content.TextualElement ?: continue
            val parts = SyncAlign.words(el.text.orEmpty())
            if (parts.isEmpty()) continue
            val owner = locators.size
            locators += el.locator
            for (w in parts) {
                words += w
                owners += owner
            }
        }
        val rec = heard.map { SyncAlign.norm(it.first) }
        val recMs = LongArray(heard.size) { heard[it].second }
        val match = SyncAlign.align(words, rec, recMs)
        val firstMs = LongArray(locators.size) { -1L }
        var matched = 0
        match.forEachIndexed { j, i ->
            if (i < 0) return@forEachIndexed
            matched++
            val o = owners[i]
            if (firstMs[o] < 0 || recMs[j] < firstMs[o]) firstMs[o] = recMs[j]
        }
        val counts = IntArray(locators.size)
        owners.forEach { counts[it]++ }
        val before = LongArray(locators.size + 1)
        for (o in locators.indices) before[o + 1] = before[o] + counts[o]
        val kept = ArrayList<Int>()
        var lastMs = -1L
        firstMs.forEachIndexed { o, ms ->
            if (ms < 0 || ms <= lastMs) return@forEachIndexed
            lastMs = ms
            kept += o
        }
        val out = JSONArray()
        kept.forEachIndexed { k, o ->
            out.put(JSONObject().put("l", locators[o].toJSON()).put("ms", firstMs[o]))
            val next = kept.getOrNull(k + 1) ?: return@forEachIndexed
            val gap = next - o - 1
            if (gap <= 0 || gap > 40) return@forEachIndexed
            val span = (before[next] - before[o]).coerceAtLeast(1)
            val dt = firstMs[next] - firstMs[o]
            var prev = firstMs[o]
            for (g in o + 1 until next) {
                val ms = firstMs[o] + dt * (before[g] - before[o]) / span
                if (ms <= prev || ms >= firstMs[next]) continue
                prev = ms
                out.put(JSONObject().put("l", locators[g].toJSON()).put("ms", ms).put("g", 1))
            }
        }
        val coverage = if (rec.isEmpty()) 0f else matched.toFloat() / rec.size
        if (out.length() == 0) throw IllegalStateException("Текст не совпал с аудио. Проверь, что выбрана та же книга")
        val tmp = File(mapFile(id).path + ".tmp")
        tmp.writeText(JSONObject().put("coverage", coverage.toDouble()).put("points", out).toString())
        tmp.renameTo(mapFile(id))
        cache.remove(id)
    }
}
