package com.flerty.wikiflerty.data.convert

internal class MobiIndex(val entries: List<Entry>, private val strings: Map<Int, String>) {
    class Entry(val ident: String, val tags: Map<Int, List<Int>>) {
        fun tag(t: Int, i: Int = 0): Int? = tags[t]?.getOrNull(i)
    }

    fun text(offset: Int?): String? = offset?.let { strings[it] }

    companion object {
        private fun u16(b: ByteArray, at: Int): Int = if (at < 0 || at + 2 > b.size) 0 else ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
        private fun u32(b: ByteArray, at: Int): Int = if (at < 0 || at + 4 > b.size) 0 else
            ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

        private fun varint(b: ByteArray, at: Int): Pair<Int, Int> {
            var v = 0
            var n = 0
            while (at + n < b.size && n < 5) {
                val c = b[at + n].toInt() and 0xFF
                v = (v shl 7) or (c and 0x7F)
                n++
                if (c and 0x80 != 0) break
            }
            return v to maxOf(n, 1)
        }

        private fun isIndx(b: ByteArray) = b.size >= 56 && b[0] == 'I'.code.toByte() && b[1] == 'N'.code.toByte() && b[2] == 'D'.code.toByte() && b[3] == 'X'.code.toByte()

        fun read(record: (Int) -> ByteArray, first: Int): MobiIndex? = runCatching {
            if (first <= 0 || first == 0x7FFFFFFF || first == -1) return null
            val primary = record(first)
            if (!isIndx(primary)) return null
            val headerLength = u32(primary, 4)
            val recordCount = u32(primary, 24)
            val cncxCount = u32(primary, 52)
            val at = headerLength
            if (at + 12 > primary.size || String(primary, at, 4, Charsets.ISO_8859_1) != "TAGX") return null
            val tagxLength = u32(primary, at + 4)
            val controlCount = u32(primary, at + 8)
            val tagx = ArrayList<IntArray>()
            var p = at + 12
            while (p + 4 <= at + tagxLength && p + 4 <= primary.size) {
                tagx += intArrayOf(primary[p].toInt() and 0xFF, primary[p + 1].toInt() and 0xFF, primary[p + 2].toInt() and 0xFF, primary[p + 3].toInt() and 0xFF)
                p += 4
            }
            val entries = ArrayList<Entry>()
            for (j in 1..recordCount) {
                val rec = record(first + j)
                if (!isIndx(rec)) continue
                val idxt = u32(rec, 20)
                val count = u32(rec, 24)
                if (idxt + 4 > rec.size) continue
                for (i in 0 until count) {
                    val off = u16(rec, idxt + 4 + 2 * i)
                    val end = if (i + 1 < count) u16(rec, idxt + 6 + 2 * i) else idxt
                    if (off <= 0 || off >= rec.size || end > rec.size || end <= off) continue
                    val identLength = rec[off].toInt() and 0xFF
                    val ident = String(rec, off + 1, minOf(identLength, end - off - 1), Charsets.ISO_8859_1)
                    var q = off + 1 + identLength
                    val control = IntArray(controlCount) { k -> if (q + k < end) rec[q + k].toInt() and 0xFF else 0 }
                    q += controlCount
                    var ci = 0
                    val pending = ArrayList<IntArray>()
                    for (t in tagx) {
                        val (tag, perEntry, mask, eof) = listOf(t[0], t[1], t[2], t[3])
                        if (eof == 1) {
                            ci++
                            continue
                        }
                        if (ci >= control.size) break
                        var value = control[ci] and mask
                        if (value == 0) continue
                        if (value == mask) {
                            if (Integer.bitCount(mask) > 1) {
                                val (v, n) = varint(rec, q)
                                q += n
                                pending += intArrayOf(tag, -1, v, perEntry)
                            } else {
                                pending += intArrayOf(tag, 1, -1, perEntry)
                            }
                        } else {
                            var m = mask
                            while (m and 1 == 0) {
                                m = m shr 1
                                value = value shr 1
                            }
                            pending += intArrayOf(tag, value, -1, perEntry)
                        }
                    }
                    val tags = HashMap<Int, List<Int>>()
                    for (t in pending) {
                        val values = ArrayList<Int>()
                        if (t[1] >= 0) {
                            repeat(t[1] * t[3]) {
                                if (q < end) {
                                    val (v, n) = varint(rec, q)
                                    q += n
                                    values += v
                                }
                            }
                        } else {
                            var consumed = 0
                            while (consumed < t[2] && q < end) {
                                val (v, n) = varint(rec, q)
                                q += n
                                consumed += n
                                values += v
                            }
                        }
                        tags[t[0]] = values
                    }
                    entries += Entry(ident, tags)
                }
            }
            val strings = HashMap<Int, String>()
            for (c in 0 until cncxCount) {
                val rec = record(first + recordCount + 1 + c)
                var pos = 0
                while (pos < rec.size) {
                    val (len, n) = varint(rec, pos)
                    if (len <= 0 || pos + n + len > rec.size) {
                        pos += n
                        if (len <= 0) continue else break
                    }
                    strings[c * 0x10000 + pos] = String(rec, pos + n, len, Charsets.UTF_8)
                    pos += n + len
                }
            }
            MobiIndex(entries, strings)
        }.getOrNull()
    }
}
