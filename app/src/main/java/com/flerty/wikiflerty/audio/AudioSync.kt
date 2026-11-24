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

data class SyncPoint(val locator: Locator, val ms: Long, val end: Long = Long.MAX_VALUE)

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
            fun emit(count: Int): Boolean {
                if (count <= 0) return true
                val out = resample(pending.copyOf(count), rate)
                val keep = onChunk(out, (chunkStartUs / 1000).coerceAtLeast(0))
                System.arraycopy(pending, count, pending, 0, filled - count)
                filled -= count
                chunkStartUs += count * 1_000_000L / rate
                return keep
            }
            fun quiet(): Int {
                val win = (rate / 50).coerceAtLeast(1)
                val from = (filled - rate * 3).coerceAtLeast(win)
                var best = filled
                var bestE = Double.MAX_VALUE
                var i = from
                while (i + win <= filled) {
                    var e = 0.0
                    for (k in i until i + win) e += pending[k] * pending[k]
                    if (e < bestE) {
                        bestE = e
                        best = i + win / 2
                    }
                    i += win / 2
                }
                return best
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
                                    if (!emit(quiet())) {
                                        codec.releaseOutputBuffer(outIdx, false)
                                        break@loop
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            emit(filled)
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
    private const val GAP = -1
    private const val MAX_CELLS = 2_000_000
    private val splitter = Regex("[\\s\\p{Punct}«»—–…„“”]+")
    private val stop = Regex("[.!?…]+[»\"”)]*\\s+(?=[«\"„(—–\\-\\p{Lu}\\d])")
    private val abbr = Regex("(?:(?<![\\p{L}])\\p{Lu}\\.|(?<![\\p{L}])(?:г|гг|т|с|им|св|ст|см|др|пр|тов|ул|д)\\.)$")

    fun norm(w: String): String = w.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }

    fun words(text: String): List<String> = text.split(splitter).map(::norm).filter { it.isNotEmpty() }

    fun sentences(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        for (m in stop.findAll(text)) {
            val head = text.substring(start, m.range.first + 1).trimEnd()
            if (abbr.containsMatchIn(head)) continue
            val end = m.range.last + 1
            trimmed(text, start, end)?.let { out += it }
            start = end
        }
        trimmed(text, start, text.length)?.let { out += it }
        return out
    }

    private fun trimmed(text: String, from: Int, to: Int): IntRange? {
        var a = from
        var b = to
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        return if (b > a) a until b else null
    }

    private fun prefix(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var k = 0
        while (k < n && a[k] == b[k]) k++
        return k
    }

    private fun lev(a: String, b: String, cap: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > cap) return cap + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    fun sim(a: String, b: String): Int {
        if (a == b) return 2
        if (a.length >= 4 && b.length >= 4) {
            val p = prefix(a, b)
            if (p >= 4 || p >= minOf(a.length, b.length) - 1) return 1
            val cap = maxOf(1, maxOf(a.length, b.length) / 4)
            if (lev(a, b, cap) <= cap) return 1
        }
        return -1
    }

    private fun nw(book: List<String>, rec: List<String>, bFrom: Int, bTo: Int, rFrom: Int, rTo: Int, reversed: Boolean, freeEnd: Boolean, out: IntArray) {
        val n = bTo - bFrom
        val m = rTo - rFrom
        if (n <= 0 || m <= 0 || n.toLong() * m > MAX_CELLS) return
        fun bw(i: Int) = if (reversed) book[bTo - 1 - i] else book[bFrom + i]
        fun rw(j: Int) = if (reversed) rec[rTo - 1 - j] else rec[rFrom + j]
        val dir = ByteArray((n + 1) * (m + 1))
        var prev = IntArray(m + 1) { GAP * it }
        var cur = IntArray(m + 1)
        val lastCol = IntArray(n + 1)
        lastCol[0] = GAP * m
        for (j in 1..m) dir[j] = 2
        for (i in 1..n) {
            cur[0] = GAP * i
            dir[i * (m + 1)] = 1
            val b = bw(i - 1)
            for (j in 1..m) {
                val d = prev[j - 1] + sim(b, rw(j - 1))
                val u = prev[j] + GAP
                val l = cur[j - 1] + GAP
                val k = i * (m + 1) + j
                if (d >= u && d >= l) {
                    cur[j] = d
                    dir[k] = 0
                } else if (u >= l) {
                    cur[j] = u
                    dir[k] = 1
                } else {
                    cur[j] = l
                    dir[k] = 2
                }
            }
            lastCol[i] = cur[m]
            val t = prev
            prev = cur
            cur = t
        }
        var i = n
        var j = m
        if (freeEnd) {
            var best = prev[m]
            for (jj in 0..m) if (prev[jj] > best) { best = prev[jj]; i = n; j = jj }
            for (ii in 0..n) if (lastCol[ii] > best) { best = lastCol[ii]; i = ii; j = m }
        }
        while (i > 0 && j > 0) {
            when (dir[i * (m + 1) + j].toInt()) {
                0 -> {
                    if (sim(bw(i - 1), rw(j - 1)) > 0) {
                        val bi = if (reversed) bTo - i else bFrom + i - 1
                        val rj = if (reversed) rTo - j else rFrom + j - 1
                        out[bi] = rj
                    }
                    i--
                    j--
                }
                1 -> i--
                else -> j--
            }
        }
    }

    fun align(book: List<String>, rec: List<String>, check: () -> Unit = {}): IntArray {
        val out = IntArray(book.size) { -1 }
        if (book.size < 3 || rec.size < 3) return out
        val index = HashMap<String, IntArray>()
        val counts = HashMap<String, Int>()
        for (i in 0 until book.size - 2) {
            val key = book[i] + " " + book[i + 1] + " " + book[i + 2]
            counts[key] = (counts[key] ?: 0) + 1
        }
        val fill = HashMap<String, Int>()
        for (i in 0 until book.size - 2) {
            val key = book[i] + " " + book[i + 1] + " " + book[i + 2]
            val c = counts[key] ?: continue
            if (c > 4) continue
            val arr = index.getOrPut(key) { IntArray(c) }
            val k = fill[key] ?: 0
            arr[k] = i
            fill[key] = k + 1
        }
        counts.clear()
        fill.clear()
        val pi = ArrayList<Int>()
        val pj = ArrayList<Int>()
        for (j in 0 until rec.size - 2) {
            val arr = index[rec[j] + " " + rec[j + 1] + " " + rec[j + 2]] ?: continue
            for (k in arr.indices.reversed()) {
                pi += arr[k]
                pj += j
            }
        }
        index.clear()
        check()
        val tails = IntArray(pi.size)
        val tailAt = IntArray(pi.size)
        val back = IntArray(pi.size)
        var len = 0
        for (k in pi.indices) {
            val v = pi[k]
            var lo = 0
            var hi = len
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (tails[mid] < v) lo = mid + 1 else hi = mid
            }
            tails[lo] = v
            tailAt[lo] = k
            back[k] = if (lo > 0) tailAt[lo - 1] else -1
            if (lo == len) len++
        }
        val chain = ArrayList<Int>()
        var k = if (len > 0) tailAt[len - 1] else -1
        while (k >= 0) {
            chain += k
            k = back[k]
        }
        chain.reverse()
        var lastI = -1
        var lastJ = -1
        for (c in chain) {
            for (d in 0 until 3) {
                val i = pi[c] + d
                val j = pj[c] + d
                if (i > lastI && j > lastJ && i < book.size && j < rec.size) {
                    out[i] = j
                    lastI = i
                    lastJ = j
                }
            }
        }
        check()
        var pb = -1
        var pr = -1
        var steps = 0
        for (i in 0..book.size) {
            val j = if (i == book.size) rec.size else out[i]
            if (i < book.size && j < 0) continue
            if (i - pb > 1 && j - pr > 1) {
                if (pb < 0) nw(book, rec, 0, i, 0, j, reversed = true, freeEnd = true, out = out)
                else nw(book, rec, pb + 1, i, pr + 1, j, reversed = false, freeEnd = i == book.size, out = out)
            }
            pb = i
            pr = j
            if (++steps % 2000 == 0) check()
        }
        return out
    }

    class Timing(val start: LongArray, val end: LongArray)

    fun timing(owner: IntArray, sentenceWords: IntArray, match: IntArray, recMs: LongArray, lead: Long = 50): Timing {
        val ns = sentenceWords.size
        val first = IntArray(ns) { -1 }
        val firstK = IntArray(ns)
        val last = IntArray(ns) { -1 }
        val cnt = IntArray(ns)
        val pos = IntArray(ns)
        for (bi in owner.indices) {
            val si = owner[bi]
            val k = pos[si]++
            val j = match[bi]
            if (j < 0) continue
            cnt[si]++
            if (first[si] < 0) {
                first[si] = j
                firstK[si] = k
            }
            last[si] = j
        }
        val start = LongArray(ns) { -1 }
        val end = LongArray(ns) { -1 }
        var lastStart = -1L
        for (si in 0 until ns) {
            if (first[si] < 0) continue
            val total = sentenceWords[si]
            val need = if (total <= 2) 1 else maxOf(2, kotlin.math.ceil(0.3 * total).toInt())
            if (cnt[si] < need) continue
            val s = (recMs[first[si]] - 300L * firstK[si] - lead).coerceAtLeast(0)
            if (s <= lastStart) continue
            val jl = last[si]
            val e = if (jl + 1 < recMs.size && recMs[jl + 1] - recMs[jl] < 1200) recMs[jl + 1] else recMs[jl] + 500
            start[si] = s
            end[si] = maxOf(e, s + 300)
            lastStart = s
        }
        var prev = -1
        for (si in 0 until ns) {
            if (start[si] < 0) continue
            if (prev >= 0 && si - prev > 1) {
                var words = 0
                for (k in prev + 1 until si) words += sentenceWords[k]
                val gap = start[si] - end[prev]
                val maxRate = if (words <= 3) 2500 else 1200
                if (words in 1..12 && gap >= 120L * words && gap <= maxRate.toLong() * words) {
                    var acc = 0
                    for (k in prev + 1 until si) {
                        start[k] = end[prev] + gap * acc / words
                        acc += sentenceWords[k]
                        end[k] = end[prev] + gap * acc / words
                    }
                }
            }
            prev = si
        }
        return Timing(start, end)
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
            val raw = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                Locator.fromJSON(o.getJSONObject("l"))?.let { SyncPoint(it, o.getLong("ms"), o.optLong("e", -1)) }
            }
            raw.mapIndexed { i, p -> if (p.end >= 0) p else p.copy(end = raw.getOrNull(i + 1)?.ms ?: (p.ms + 15_000)) }
        }.getOrDefault(emptyList())
        cache[id] = list
        return list
    }

    fun indexAt(id: String, globalMs: Long): Int {
        val list = points(id)
        if (list.isEmpty() || list[0].ms > globalMs) return -1
        var lo = 0
        var hi = list.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (list[mid].ms <= globalMs) lo = mid else hi = mid - 1
        }
        val p = list[lo]
        val next = list.getOrNull(lo + 1)?.ms ?: Long.MAX_VALUE
        val hold = minOf(next, p.end + 2_500)
        return if (globalMs < maxOf(p.end, hold)) lo else -1
    }

    fun pointAt(id: String, globalMs: Long): SyncPoint? = indexAt(id, globalMs).takeIf { it >= 0 }?.let { points(id)[it] }

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
                val stop = minOf(p.end, list.getOrNull(best + 1)?.ms ?: p.end)
                return p.ms + (stop - p.ms).coerceAtLeast(0) * bestAt / total
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
    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    private suspend fun buildMap(id: String, publication: org.readium.r2.shared.publication.Publication, heard: List<Pair<String, Long>>) {
        val content = publication.findService(ContentService::class)?.content(null) ?: throw IllegalStateException("В книге не нашёлся текст")
        val iterator = content.iterator()
        val words = ArrayList<String>()
        val owners = ArrayList<Int>()
        val counts = ArrayList<Int>()
        val locators = ArrayList<Locator>()
        var guard = 0
        while (iterator.hasNext() && guard < 400_000) {
            guard++
            if (guard % 500 == 0) coroutineContext.ensureActive()
            val el = iterator.next() as? Content.TextualElement ?: continue
            val text = el.text.orEmpty()
            if (text.isBlank()) continue
            for (r in SyncAlign.sentences(text)) {
                val parts = SyncAlign.words(text.substring(r.first, r.last + 1))
                if (parts.isEmpty()) continue
                val owner = locators.size
                locators += el.locator.copy(
                    text = Locator.Text(
                        before = text.substring(maxOf(0, r.first - 60), r.first).takeIf { it.isNotEmpty() },
                        highlight = text.substring(r.first, r.last + 1),
                        after = text.substring(r.last + 1, minOf(text.length, r.last + 61)).takeIf { it.isNotEmpty() },
                    ),
                )
                counts += parts.size
                for (w in parts) {
                    words += w
                    owners += owner
                }
            }
        }
        val rec = heard.map { SyncAlign.norm(it.first) }
        val recMs = LongArray(heard.size) { heard[it].second }
        val job = coroutineContext[Job]
        val match = SyncAlign.align(words, rec) { if (job?.isActive == false) throw CancellationException() }
        val matched = match.count { it >= 0 }
        val timing = SyncAlign.timing(owners.toIntArray(), counts.toIntArray(), match, recMs)
        val out = JSONArray()
        for (si in locators.indices) {
            val st = timing.start[si]
            if (st < 0) continue
            out.put(JSONObject().put("l", locators[si].toJSON()).put("ms", st).put("e", timing.end[si]))
        }
        val coverage = if (rec.isEmpty()) 0f else matched.toFloat() / rec.size
        if (out.length() == 0) throw IllegalStateException("Текст не совпал с аудио. Проверь, что выбрана та же книга")
        val tmp = File(mapFile(id).path + ".tmp")
        tmp.writeText(JSONObject().put("v", 2).put("coverage", coverage.toDouble()).put("points", out).toString())
        tmp.renameTo(mapFile(id))
        cache.remove(id)
    }
}
