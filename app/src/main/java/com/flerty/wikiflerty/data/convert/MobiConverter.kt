package com.flerty.wikiflerty.data.convert

import java.io.File

class DrmException : Exception("Эта книга защищена DRM — её можно открыть только в приложении магазина, где она куплена")

object MobiConverter {
    private class Records(file: File) {
        val data: ByteArray = file.readBytes()
        val offsets: IntArray

        init {
            if (data.size < 78) throw IllegalArgumentException("short")
            val count = u16(data, 76)
            offsets = IntArray(count) { i -> u32(data, 78 + i * 8).toInt() }
        }

        val size get() = offsets.size

        fun get(i: Int): ByteArray {
            if (i !in offsets.indices) return ByteArray(0)
            val start = offsets[i]
            val end = if (i + 1 < offsets.size) offsets[i + 1] else data.size
            if (start < 0 || end > data.size || end < start) return ByteArray(0)
            return data.copyOfRange(start, end)
        }
    }

    fun isMobi(head: ByteArray, read: Int): Boolean {
        if (read < 68) return false
        val type = String(head, 60, 8, Charsets.ISO_8859_1)
        return type == "BOOKMOBI" || type == "TEXtREAd"
    }

    private fun u16(b: ByteArray, at: Int): Int = if (at + 2 > b.size) 0 else ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, at: Int): Long = if (at + 4 > b.size) 0 else
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private class Header(val rec: ByteArray, val start: Int) {
        val compression = u16(rec, 0)
        val textLength = u32(rec, 4).toInt()
        val textRecords = u16(rec, 8)
        val encryption = u16(rec, 12)
        val isMobi = rec.size >= 24 && String(rec, 16, 4, Charsets.ISO_8859_1) == "MOBI"
        val headerLength = if (isMobi) u32(rec, 20).toInt() else 0
        val encoding = if (isMobi) u32(rec, 28).toInt() else 1252
        val version = if (isMobi) u32(rec, 36).toInt() else 0
        val nameOffset = if (isMobi) u32(rec, 84).toInt() else 0
        val nameLength = if (isMobi) u32(rec, 88).toInt() else 0
        val firstImage = if (isMobi) u32(rec, 108).toInt() else -1
        val huffOffset = if (isMobi) u32(rec, 112).toInt() else 0
        val huffCount = if (isMobi) u32(rec, 116).toInt() else 0
        val exthFlags = if (isMobi) u32(rec, 128).toInt() else 0
        val fdst = if (isMobi && headerLength >= 0xB0) u32(rec, 0xC0).toInt() else -1
        val extraFlags = if (isMobi && headerLength >= 0xE4) u16(rec, 0xF2) else 0
        val exth = HashMap<Int, MutableList<ByteArray>>()

        init {
            if (isMobi && exthFlags and 0x40 != 0) {
                val at = 16 + headerLength
                if (at + 12 <= rec.size && String(rec, at, 4, Charsets.ISO_8859_1) == "EXTH") {
                    val count = u32(rec, at + 8).toInt()
                    var p = at + 12
                    repeat(count) {
                        if (p + 8 > rec.size) return@repeat
                        val type = u32(rec, p).toInt()
                        val len = u32(rec, p + 4).toInt()
                        if (len < 8 || p + len > rec.size) return@repeat
                        exth.getOrPut(type) { mutableListOf() } += rec.copyOfRange(p + 8, p + len)
                        p += len
                    }
                }
            }
        }

        fun charset() = if (encoding == 65001) Charsets.UTF_8 else runCatching { charset("windows-$encoding") }.getOrDefault(charset("windows-1252"))

        fun text(type: Int): String? = exth[type]?.firstOrNull()?.let { String(it, charset()).trim() }?.takeIf { it.isNotEmpty() }

        fun texts(type: Int): List<String> = exth[type].orEmpty().map { String(it, charset()).trim() }.filter { it.isNotEmpty() }

        fun number(type: Int): Int? = exth[type]?.firstOrNull()?.takeIf { it.size >= 4 }?.let { u32(it, 0).toInt() }

        fun name(): String? = if (nameOffset > 0 && nameLength > 0 && nameOffset + nameLength <= rec.size) String(rec, nameOffset, nameLength, charset()).trim().takeIf { it.isNotEmpty() } else null
    }

    private class Buf(capacity: Int) {
        var data = ByteArray(maxOf(16, capacity))
        var size = 0
        fun put(b: Int) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = b.toByte()
        }
        fun put(bytes: ByteArray) = bytes.forEach { put(it.toInt()) }
        fun bytes() = data.copyOf(size)
    }

    private fun palmdoc(src: ByteArray): ByteArray {
        val out = Buf(src.size * 2)
        var i = 0
        while (i < src.size) {
            val c = src[i++].toInt() and 0xFF
            when {
                c == 0 || c in 0x09..0x7F -> out.put(c)
                c in 1..8 -> repeat(c) { if (i < src.size) out.put(src[i++].toInt() and 0xFF) }
                c in 0x80..0xBF -> {
                    if (i >= src.size) return out.bytes()
                    val c2 = src[i++].toInt() and 0xFF
                    val m = ((c shl 8) or c2) and 0x3FFF
                    val dist = m shr 3
                    val len = (m and 7) + 3
                    if (dist <= 0 || dist > out.size) continue
                    var from = out.size - dist
                    repeat(len) { out.put(out.data[from++].toInt() and 0xFF) }
                }
                else -> {
                    out.put(0x20)
                    out.put(c xor 0x80)
                }
            }
        }
        return out.bytes()
    }

    private class Huff(huff: ByteArray, cdics: List<ByteArray>) {
        private val codeLen = IntArray(256)
        private val term = BooleanArray(256)
        private val maxCode1 = LongArray(256)
        private val minCode = LongArray(33)
        private val maxCode = LongArray(33)
        private val dict = ArrayList<ByteArray?>()
        private val ready = ArrayList<Boolean>()

        init {
            if (huff.size < 16 || String(huff, 0, 4, Charsets.ISO_8859_1) != "HUFF") throw IllegalArgumentException("huff")
            val off1 = u32(huff, 8).toInt()
            val off2 = u32(huff, 12).toInt()
            for (i in 0 until 256) {
                val v = u32(huff, off1 + i * 4)
                val len = (v and 0x1F).toInt()
                codeLen[i] = len
                term[i] = v and 0x80 != 0L
                val mc = v ushr 8
                maxCode1[i] = if (len == 0) 0 else ((mc + 1) shl (32 - len)) - 1
            }
            minCode[0] = 0
            maxCode[0] = 0
            for (len in 1..32) {
                val mn = u32(huff, off2 + (len - 1) * 8)
                val mx = u32(huff, off2 + (len - 1) * 8 + 4)
                minCode[len] = mn shl (32 - len)
                maxCode[len] = ((mx + 1) shl (32 - len)) - 1
            }
            cdics.forEach { c ->
                if (c.size < 16 || String(c, 0, 4, Charsets.ISO_8859_1) != "CDIC") return@forEach
                val phrases = u32(c, 8).toInt()
                val bits = u32(c, 12).toInt()
                val n = minOf(1 shl bits, phrases - dict.size)
                for (k in 0 until n) {
                    val off = u16(c, 16 + k * 2)
                    val blen = u16(c, 16 + off)
                    val len = blen and 0x7FFF
                    val s = 18 + off
                    dict += if (s + len <= c.size) c.copyOfRange(s, s + len) else ByteArray(0)
                    ready += blen and 0x8000 != 0
                }
            }
        }

        fun unpack(src: ByteArray, depth: Int = 0): ByteArray {
            val data = src.copyOf(src.size + 8)
            var bitsLeft = src.size.toLong() * 8
            var pos = 0
            fun q(at: Int): Long {
                var v = 0L
                for (k in 0 until 8) v = (v shl 8) or (data[at + k].toLong() and 0xFF)
                return v
            }
            var x = q(0)
            var n = 32
            val out = Buf(src.size * 3)
            while (true) {
                if (n <= 0) {
                    pos += 4
                    if (pos + 8 > data.size) break
                    x = q(pos)
                    n += 32
                }
                val code = (x ushr n) and 0xFFFFFFFFL
                val top = (code ushr 24).toInt()
                var len = codeLen[top]
                var mc = maxCode1[top]
                if (len == 0) break
                if (!term[top]) {
                    while (len < 32 && code < minCode[len]) len++
                    mc = maxCode[len]
                }
                n -= len
                bitsLeft -= len
                if (bitsLeft < 0) break
                val r = ((mc - code) ushr (32 - len)).toInt()
                if (r !in dict.indices) break
                var slice = dict[r] ?: ByteArray(0)
                if (!ready[r] && depth < 32) {
                    dict[r] = null
                    slice = unpack(slice, depth + 1)
                    dict[r] = slice
                    ready[r] = true
                }
                out.put(slice)
            }
            return out.bytes()
        }
    }

    private fun trailing(rec: ByteArray, flags: Int): Int {
        var size = rec.size
        var num = 0
        fun entry(end: Int): Int {
            var bitpos = 0
            var result = 0
            var s = end
            if (s <= 0) return 0
            while (true) {
                val v = rec[s - 1].toInt() and 0xFF
                result = result or ((v and 0x7F) shl bitpos)
                bitpos += 7
                s--
                if (v and 0x80 != 0 || bitpos >= 28 || s == 0) return result
            }
        }
        var test = flags shr 1
        while (test != 0) {
            if (test and 1 != 0) num += entry(size - num)
            test = test shr 1
        }
        if (flags and 1 != 0 && size - num - 1 >= 0) num += (rec[size - num - 1].toInt() and 0x3) + 1
        return num.coerceIn(0, size)
    }

    private fun rawText(r: Records, h: Header): ByteArray {
        if (h.encryption != 0) throw DrmException()
        val huff = if (h.compression == 17480) {
            val base = h.start + h.huffOffset
            Huff(r.get(base), (1 until h.huffCount).map { r.get(base + it) })
        } else null
        val out = Buf(maxOf(h.textLength, 1024))
        for (k in 1..h.textRecords) {
            val rec = r.get(h.start + k)
            val cut = trailing(rec, h.extraFlags)
            val body = rec.copyOf(rec.size - cut)
            out.put(
                when (h.compression) {
                    1 -> body
                    2 -> palmdoc(body)
                    17480 -> huff!!.unpack(body)
                    else -> throw IllegalArgumentException("compression ${h.compression}")
                },
            )
        }
        val all = out.bytes()
        return if (h.textLength in 1..all.size) all.copyOf(h.textLength) else all
    }

    fun read(file: File, fallbackTitle: String): RichDoc {
        val r = Records(file)
        val h0 = Header(r.get(0), 0)
        if (h0.encryption != 0) throw DrmException()
        var kf8 = if (h0.version == 8) h0 else null
        if (kf8 == null) {
            val boundary = (0 until r.size).firstOrNull { i -> r.get(i).let { it.size >= 8 && String(it, 0, 8, Charsets.ISO_8859_1) == "BOUNDARY" } }
            if (boundary != null && boundary + 1 < r.size) {
                val cand = Header(r.get(boundary + 1), boundary + 1)
                if (cand.isMobi && cand.version == 8 && !h0.isMobi) kf8 = cand
            }
        }
        val h = kf8 ?: h0
        val charset = h.charset()
        var text = String(rawText(r, h), charset)
        if (kf8 != null && h.fdst > 0) {
            val f = r.get(h.start + h.fdst)
            if (f.size >= 12 && String(f, 0, 4, Charsets.ISO_8859_1) == "FDST") {
                val sections = u32(f, 8).toInt()
                if (sections > 0) {
                    val raw = rawText(r, h)
                    val s0 = u32(f, 12).toInt()
                    val e0 = u32(f, 16).toInt()
                    if (e0 in (s0 + 1)..raw.size) text = String(raw, s0, e0 - s0, charset)
                }
            }
        }
        val title = h0.text(503) ?: h0.name() ?: h.text(503) ?: h.name() ?: fallbackTitle
        val authors = h0.texts(100).ifEmpty { h.texts(100) }
        val doc = RichDoc(title, authors.flatMap { a -> a.split('&', ';').map { it.trim() } }.filter { it.isNotBlank() }.distinct())
        doc.description = (h0.text(103) ?: h.text(103))?.let { d -> Entities.unescape(d.replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim() }
        doc.language = h0.text(524) ?: h.text(524)
        val firstImage = (if (h.firstImage in 1 until 0x7FFFFFFF) h.start + h.firstImage else -1)
        val imageCache = HashMap<Int, String?>()
        fun image(index: Int): String? {
            if (firstImage < 0 || index < 0) return null
            val rec = firstImage + index
            return imageCache.getOrPut(rec) {
                val bytes = r.get(rec)
                if (RichDoc.imageType(bytes) == null) null else doc.addImage(bytes)
            }
        }
        val coverIndex = h0.number(201) ?: h.number(201)
        if (coverIndex != null && coverIndex != -1 && coverIndex < 0x7FFFFFFF) image(coverIndex)?.let { doc.cover = it }
        if (!h.isMobi) {
            text = text.split('\n').joinToString("") { "<p>" + EpubBuilder.esc(it) + "</p>" }
        }
        if (kf8 != null) {
            val parts = text.split(Regex("(?i)(?=<\\?xml|<html[\\s>])")).filter { it.isNotBlank() }
            text = parts.joinToString("<mbp:pagebreak/>")
        }
        HtmlBlocks.parse(text, doc) { src, a ->
            when {
                src.startsWith("recindex:") -> src.removePrefix("recindex:").trim().toIntOrNull()?.let { image(it - 1) }
                src.startsWith("kindle:embed:", ignoreCase = true) -> {
                    val code = src.substring(13).substringBefore('?').trim()
                    code.toIntOrNull(32)?.let { image(it - 1) }
                }
                else -> a["recindex"]?.trim()?.toIntOrNull()?.let { image(it - 1) }
            }
        }
        doc.cover?.let { c ->
            val at = doc.blocks.indexOfFirst { it is Block.Image && it.href == c }
            if (at in 0..2) doc.blocks.removeAt(at)
        }
        return doc
    }
}
