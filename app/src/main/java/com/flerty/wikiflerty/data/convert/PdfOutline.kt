package com.flerty.wikiflerty.data.convert

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.Inflater

object PdfOutline {
    data class Entry(val title: String, val depth: Int, val page: Int)

    fun read(file: File): List<Entry> = runCatching {
        if (file.length() > 300L * 1024 * 1024) return emptyList()
        RandomAccessFile(file, "r").use { raf ->
            val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
            OutlineParser(MappedBytes(map)).outline()
        }
    }.getOrDefault(emptyList())
}

private interface Bytes {
    val size: Int
    operator fun get(i: Int): Int
}

private class MappedBytes(private val b: ByteBuffer) : Bytes {
    override val size = b.limit()
    override fun get(i: Int): Int = b.get(i).toInt() and 0xFF
}

private class ArrayBytes(private val b: ByteArray) : Bytes {
    override val size = b.size
    override fun get(i: Int): Int = b[i].toInt() and 0xFF
}

private data class PName(val v: String)
private data class PRef(val num: Int, val gen: Int)
private class PStr(val bytes: ByteArray)
private class PKeyword(val v: String)

private fun isWs(c: Int) = c == 0x20 || c == 0x0A || c == 0x0D || c == 0x09 || c == 0x0C || c == 0x00
private fun isDelim(c: Int) = c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code || c == '['.code || c == ']'.code ||
    c == '{'.code || c == '}'.code || c == '/'.code || c == '%'.code
private fun isDigit(c: Int) = c in '0'.code..'9'.code

private class Lexer(val b: Bytes, var pos: Int) {
    fun skipWs() {
        while (pos < b.size) {
            val c = b[pos]
            if (isWs(c)) {
                pos++
            } else if (c == '%'.code) {
                while (pos < b.size && b[pos] != 0x0A && b[pos] != 0x0D) pos++
            } else {
                break
            }
        }
    }

    fun peekKeyword(word: String): Boolean {
        skipWs()
        for (i in word.indices) if (pos + i >= b.size || b[pos + i] != word[i].code) return false
        val after = pos + word.length
        return after >= b.size || isWs(b[after]) || isDelim(b[after])
    }

    fun parse(depth: Int = 0): Any? {
        if (depth > 64) return null
        skipWs()
        if (pos >= b.size) return null
        val c = b[pos]
        return when {
            c == '<'.code && pos + 1 < b.size && b[pos + 1] == '<'.code -> dict(depth)
            c == '<'.code -> hex()
            c == '['.code -> array(depth)
            c == '('.code -> literal()
            c == '/'.code -> name()
            isDigit(c) || c == '+'.code || c == '-'.code || c == '.'.code -> number()
            isDelim(c) -> {
                pos++
                null
            }
            else -> {
                val start = pos
                while (pos < b.size && !isWs(b[pos]) && !isDelim(b[pos])) pos++
                val w = String(ByteArray(pos - start) { b[start + it].toByte() }, Charsets.ISO_8859_1)
                when (w) {
                    "true" -> true
                    "false" -> false
                    "null" -> null
                    else -> PKeyword(w)
                }
            }
        }
    }

    private fun dict(depth: Int): Map<String, Any?> {
        pos += 2
        val out = HashMap<String, Any?>()
        while (true) {
            skipWs()
            if (pos >= b.size) break
            if (b[pos] == '>'.code && pos + 1 < b.size && b[pos + 1] == '>'.code) {
                pos += 2
                break
            }
            val before = pos
            val key = parse(depth + 1)
            if (key !is PName) {
                if (pos == before) pos++
                continue
            }
            out[key.v] = parse(depth + 1)
        }
        return out
    }

    private fun array(depth: Int): List<Any?> {
        pos++
        val out = ArrayList<Any?>()
        while (true) {
            skipWs()
            if (pos >= b.size) break
            if (b[pos] == ']'.code) {
                pos++
                break
            }
            val before = pos
            out += parse(depth + 1)
            if (pos == before) pos++
        }
        return out
    }

    private fun name(): PName {
        pos++
        val sb = StringBuilder()
        while (pos < b.size && !isWs(b[pos]) && !isDelim(b[pos])) {
            val c = b[pos]
            if (c == '#'.code && pos + 2 < b.size) {
                val v = runCatching { String(charArrayOf(b[pos + 1].toChar(), b[pos + 2].toChar())).toInt(16) }.getOrNull()
                if (v != null) {
                    sb.append(v.toChar())
                    pos += 3
                    continue
                }
            }
            sb.append(c.toChar())
            pos++
        }
        return PName(sb.toString())
    }

    private fun hex(): PStr {
        pos++
        val digits = StringBuilder()
        while (pos < b.size && b[pos] != '>'.code) {
            val c = b[pos]
            if (!isWs(c)) digits.append(c.toChar())
            pos++
        }
        pos++
        if (digits.length % 2 == 1) digits.append('0')
        val out = ByteArray(digits.length / 2) { i -> (digits.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0).toByte() }
        return PStr(out)
    }

    private fun literal(): PStr {
        pos++
        val out = java.io.ByteArrayOutputStream()
        var depth = 1
        while (pos < b.size) {
            val c = b[pos]
            if (c == '\\'.code && pos + 1 < b.size) {
                val n = b[pos + 1]
                when (n) {
                    'n'.code -> out.write(0x0A)
                    'r'.code -> out.write(0x0D)
                    't'.code -> out.write(0x09)
                    'b'.code -> out.write(0x08)
                    'f'.code -> out.write(0x0C)
                    0x0A -> {}
                    0x0D -> if (pos + 2 < b.size && b[pos + 2] == 0x0A) pos++
                    in '0'.code..'7'.code -> {
                        var j = pos + 1
                        var v = 0
                        while (j < b.size && j < pos + 4 && b[j] in '0'.code..'7'.code) {
                            v = v * 8 + (b[j] - '0'.code)
                            j++
                        }
                        out.write(v and 0xFF)
                        pos = j
                        continue
                    }
                    else -> out.write(n)
                }
                pos += 2
                continue
            }
            if (c == '('.code) depth++
            if (c == ')'.code) {
                depth--
                if (depth == 0) {
                    pos++
                    break
                }
            }
            out.write(c)
            pos++
        }
        return PStr(out.toByteArray())
    }

    private fun readInt(): Long? {
        val start = pos
        var neg = false
        if (pos < b.size && (b[pos] == '-'.code || b[pos] == '+'.code)) {
            neg = b[pos] == '-'.code
            pos++
        }
        var v = 0L
        var any = false
        while (pos < b.size && isDigit(b[pos])) {
            v = v * 10 + (b[pos] - '0'.code)
            pos++
            any = true
            if (v > Int.MAX_VALUE) break
        }
        if (!any) {
            pos = start
            return null
        }
        return if (neg) -v else v
    }

    private fun number(): Any? {
        val start = pos
        val whole = readInt()
        if (pos < b.size && b[pos] == '.'.code) {
            pos = start
            val sb = StringBuilder()
            while (pos < b.size && (isDigit(b[pos]) || b[pos] == '.'.code || b[pos] == '-'.code || b[pos] == '+'.code)) {
                sb.append(b[pos].toChar())
                pos++
            }
            return sb.toString().toDoubleOrNull()
        }
        if (whole == null) {
            pos++
            return null
        }
        val save = pos
        skipWs()
        val gen = if (pos < b.size && isDigit(b[pos])) readInt() else null
        if (gen != null) {
            skipWs()
            if (pos < b.size && b[pos] == 'R'.code && (pos + 1 >= b.size || isWs(b[pos + 1]) || isDelim(b[pos + 1]))) {
                pos++
                return PRef(whole.toInt(), gen.toInt())
            }
        }
        pos = save
        return whole.toInt()
    }
}

private class OutlineParser(private val file: Bytes) {
    private val offsets = HashMap<Int, Int>()
    private var streamIndex: HashMap<Int, Pair<ArrayBytes, Int>>? = null
    private val cache = HashMap<Int, Any?>()
    private var pageMap: Map<Int, Int>? = null
    private var named: Map<String, Any?>? = null
    private lateinit var catalog: Map<String, Any?>

    private fun indexObjects() {
        val n = file.size
        var i = 0
        while (i + 3 <= n) {
            if (file[i] == 'o'.code && file[i + 1] == 'b'.code && file[i + 2] == 'j'.code && (i + 3 == n || isWs(file[i + 3]) || isDelim(file[i + 3]))) {
                var j = i - 1
                if (j >= 0 && isWs(file[j])) {
                    while (j >= 0 && isWs(file[j])) j--
                    val genEnd = j
                    while (j >= 0 && isDigit(file[j])) j--
                    if (j < genEnd && j >= 0 && isWs(file[j])) {
                        while (j >= 0 && isWs(file[j])) j--
                        val numEnd = j
                        while (j >= 0 && isDigit(file[j])) j--
                        if (j < numEnd && (j < 0 || isWs(file[j]) || isDelim(file[j]))) {
                            var num = 0L
                            for (k in j + 1..numEnd) num = num * 10 + (file[k] - '0'.code)
                            if (num in 1..Int.MAX_VALUE.toLong()) offsets[num.toInt()] = j + 1
                        }
                    }
                }
                i += 3
            } else {
                i++
            }
        }
    }

    private fun lastIndexOf(pattern: String): Int {
        val p = pattern.toByteArray(Charsets.ISO_8859_1)
        var i = file.size - p.size
        while (i >= 0) {
            var ok = true
            for (k in p.indices) if (file[i + k] != (p[k].toInt() and 0xFF)) {
                ok = false
                break
            }
            if (ok) return i
            i--
        }
        return -1
    }

    private fun parseAt(bytes: Bytes, offset: Int, direct: Boolean): Any? {
        val lx = Lexer(bytes, offset)
        if (direct) {
            lx.parse()
            lx.parse()
            if (!lx.peekKeyword("obj")) return null
            lx.pos += 3
        }
        return lx.parse()
    }

    private fun streamData(offset: Int): ByteArray? {
        val lx = Lexer(file, offset)
        lx.parse()
        lx.parse()
        if (!lx.peekKeyword("obj")) return null
        lx.pos += 3
        val dict = lx.parse() as? Map<*, *> ?: return null
        if (!lx.peekKeyword("stream")) return null
        lx.pos += 6
        if (lx.pos < file.size && file[lx.pos] == 0x0D) lx.pos++
        if (lx.pos < file.size && file[lx.pos] == 0x0A) lx.pos++
        val start = lx.pos
        var len = (resolve(dict["Length"]) as? Int) ?: -1
        if (len < 0 || start + len > file.size) {
            len = -1
            var i = start
            while (i + 9 <= file.size) {
                if (file[i] == 'e'.code && file[i + 1] == 'n'.code && file[i + 2] == 'd'.code && file[i + 3] == 's'.code && file[i + 4] == 't'.code) {
                    len = i - start
                    break
                }
                i++
            }
            if (len < 0) return null
        }
        val raw = ByteArray(len) { file[start + it].toByte() }
        val filter = resolve(dict["Filter"])
        val names = when (filter) {
            is PName -> listOf(filter.v)
            is List<*> -> filter.mapNotNull { (it as? PName)?.v }
            else -> emptyList()
        }
        return when {
            names.isEmpty() -> raw
            names.size == 1 && names[0] == "FlateDecode" -> inflate(raw)
            else -> null
        }
    }

    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = java.io.ByteArrayOutputStream(data.size * 3)
        val buf = ByteArray(16 * 1024)
        var guard = 0
        while (!inflater.finished() && guard++ < 100_000) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 64 * 1024 * 1024) break
        }
        inflater.end()
        out.toByteArray()
    }.getOrNull()

    private fun buildStreamIndex(): HashMap<Int, Pair<ArrayBytes, Int>> {
        val index = HashMap<Int, Pair<ArrayBytes, Int>>()
        val sorted = offsets.entries.sortedBy { it.value }
        val marker = "/ObjStm".toByteArray(Charsets.ISO_8859_1)
        var i = 0
        var done = 0
        while (i + marker.size <= file.size && done < 4000) {
            var ok = true
            for (k in marker.indices) if (file[i + k] != (marker[k].toInt() and 0xFF)) {
                ok = false
                break
            }
            if (ok) {
                done++
                var lo = 0
                var hi = sorted.size - 1
                var found = -1
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (sorted[mid].value <= i) {
                        found = mid
                        lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                if (found >= 0) runCatching { loadObjStm(sorted[found].value, index) }
                i += marker.size
            } else {
                i++
            }
        }
        return index
    }

    private fun loadObjStm(offset: Int, index: HashMap<Int, Pair<ArrayBytes, Int>>) {
        val lx = Lexer(file, offset)
        lx.parse()
        lx.parse()
        if (!lx.peekKeyword("obj")) return
        lx.pos += 3
        val dict = lx.parse() as? Map<*, *> ?: return
        if ((dict["Type"] as? PName)?.v != "ObjStm") return
        val n = dict["N"] as? Int ?: return
        val first = dict["First"] as? Int ?: return
        val data = streamData(offset) ?: return
        val bytes = ArrayBytes(data)
        val head = Lexer(bytes, 0)
        repeat(n) {
            val num = head.parse() as? Int ?: return
            val rel = head.parse() as? Int ?: return
            if (!offsets.containsKey(num) && !index.containsKey(num)) index[num] = bytes to (first + rel)
        }
    }

    fun get(num: Int): Any? {
        if (cache.containsKey(num)) return cache[num]
        cache[num] = null
        val direct = offsets[num]
        val value = if (direct != null) {
            runCatching { parseAt(file, direct, true) }.getOrNull()
        } else {
            val idx = streamIndex ?: buildStreamIndex().also { streamIndex = it }
            idx[num]?.let { (bytes, off) -> runCatching { parseAt(bytes, off, false) }.getOrNull() }
        }
        cache[num] = value
        return value
    }

    fun resolve(v: Any?): Any? {
        var cur = v
        var guard = 0
        while (cur is PRef && guard++ < 32) cur = get(cur.num)
        return cur
    }

    private fun dictOf(v: Any?): Map<*, *>? = resolve(v) as? Map<*, *>

    fun outline(): List<PdfOutline.Entry> {
        val encrypt = lastIndexOf("/Encrypt")
        if (encrypt >= 0) return emptyList()
        val rootAt = lastIndexOf("/Root")
        if (rootAt < 0) return emptyList()
        val rootRef = Lexer(file, rootAt + 5).parse() as? PRef ?: return emptyList()
        indexObjects()
        @Suppress("UNCHECKED_CAST")
        catalog = (dictOf(rootRef) ?: return emptyList()) as Map<String, Any?>
        val outlines = dictOf(catalog["Outlines"]) ?: return emptyList()
        val out = ArrayList<PdfOutline.Entry>()
        val visited = HashSet<Int>()
        walk(outlines["First"], 0, out, visited)
        var last = 0
        return out.map { e ->
            if (e.page >= 0) {
                last = e.page
                e
            } else {
                e.copy(page = last)
            }
        }
    }

    private fun walk(first: Any?, depth: Int, out: MutableList<PdfOutline.Entry>, visited: MutableSet<Int>) {
        var cur = first
        while (cur is PRef && visited.add(cur.num) && out.size < 3000) {
            val d = dictOf(cur) ?: break
            val title = (resolve(d["Title"]) as? PStr)?.let { PdfSupport.decodeText(it.bytes) }
                ?.replace(Regex("[\\u0000-\\u001F]+"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            var dest = d["Dest"]
            if (dest == null) {
                val action = dictOf(d["A"])
                if (action != null && (action["S"] as? PName)?.v == "GoTo") dest = action["D"]
            }
            val page = runCatching { destPage(dest) }.getOrNull() ?: -1
            if (title.isNotEmpty()) out += PdfOutline.Entry(title, depth, page)
            if (depth < 10) walk(d["First"], depth + 1, out, visited)
            cur = d["Next"]
        }
    }

    private fun destPage(dest: Any?): Int? {
        var d = resolve(dest)
        if (d is PName) d = resolve(namedDests()[d.v])
        if (d is PStr) d = resolve(namedDests()[String(d.bytes, Charsets.ISO_8859_1)])
        if (d is Map<*, *>) d = resolve(d["D"])
        val arr = d as? List<*> ?: return null
        return when (val first = arr.firstOrNull()) {
            is PRef -> pages()[first.num]
            is Int -> first
            else -> null
        }
    }

    private fun pages(): Map<Int, Int> {
        pageMap?.let { return it }
        val map = HashMap<Int, Int>()
        val visited = HashSet<Int>()
        var counter = 0
        fun visit(ref: Any?, depth: Int) {
            if (ref !is PRef || depth > 40 || !visited.add(ref.num)) return
            val node = dictOf(ref) ?: return
            val kids = resolve(node["Kids"]) as? List<*>
            if (kids != null && (node["Type"] as? PName)?.v != "Page") {
                kids.forEach { visit(it, depth + 1) }
            } else {
                map[ref.num] = counter++
            }
        }
        visit(catalog["Pages"], 0)
        pageMap = map
        return map
    }

    private fun namedDests(): Map<String, Any?> {
        named?.let { return it }
        val map = HashMap<String, Any?>()
        dictOf(catalog["Dests"])?.forEach { (k, v) -> if (k is String) map[k] = v }
        val tree = dictOf(dictOf(catalog["Names"])?.get("Dests"))
        val visited = HashSet<Any>()
        fun visit(node: Map<*, *>?, depth: Int) {
            if (node == null || depth > 30 || map.size > 50_000) return
            (resolve(node["Names"]) as? List<*>)?.let { list ->
                var i = 0
                while (i + 1 < list.size) {
                    val key = list[i] as? PStr
                    if (key != null) map[String(key.bytes, Charsets.ISO_8859_1)] = list[i + 1]
                    i += 2
                }
            }
            (resolve(node["Kids"]) as? List<*>)?.forEach { kid ->
                if (kid is PRef && !visited.add(kid.num)) return@forEach
                visit(dictOf(kid), depth + 1)
            }
        }
        visit(tree, 0)
        named = map
        return map
    }
}
