package com.flerty.wikiflerty.data.convert

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.RandomAccessFile

object PdfSupport {
    class Meta(val title: String?, val author: String?, val pages: Int)

    fun inspect(file: File): Meta {
        val pages = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = PdfRenderer(fd)
            try { renderer.pageCount } finally { renderer.close() }
        }
        val info = runCatching { readInfo(file) }.getOrNull()
        return Meta(info?.get("Title"), info?.get("Author"), pages)
    }

    fun isBlank(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return true
        var dark = 0
        var total = 0
        for (y in 0 until 48) {
            for (x in 0 until 32) {
                val c = bitmap.getPixel(x * (w - 1) / 31, y * (h - 1) / 47)
                val l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (l < 225) dark++
                total++
            }
        }
        return dark * 100 < total * 3
    }

    fun cover(file: File, width: Int): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = PdfRenderer(fd)
            try {
                if (renderer.pageCount == 0) return@use null
                val page = renderer.openPage(0)
                try {
                    val h = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, width * 3)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        }
    }.getOrNull()

    private fun readInfo(file: File): Map<String, String> {
        val len = file.length()
        val chunk = 96 * 1024
        val parts = mutableListOf<ByteArray>()
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(minOf(len, chunk.toLong()).toInt())
            raf.readFully(head)
            parts += head
            if (len > chunk) {
                val tailSize = minOf(len - chunk, chunk.toLong()).toInt()
                val tail = ByteArray(tailSize)
                raf.seek(len - tailSize)
                raf.readFully(tail)
                parts += tail
            }
        }
        val out = mutableMapOf<String, String>()
        for (bytes in parts.reversed()) {
            val text = String(bytes, Charsets.ISO_8859_1)
            for (key in listOf("Title", "Author")) {
                if (out.containsKey(key)) continue
                findValue(text, key)?.let { v -> clean(v)?.let { out[key] = it } }
            }
        }
        return out
    }

    private fun findValue(text: String, key: String): String? {
        val start = Regex("/$key\\s*([(<])").find(text) ?: return null
        val open = start.groupValues[1]
        var i = start.range.last + 1
        if (open == "<") {
            val end = text.indexOf('>', i).takeIf { it > 0 } ?: return null
            val hex = text.substring(i, end).filter { it.isLetterOrDigit() }
            val bytes = ByteArray(hex.length / 2) { k -> hex.substring(k * 2, k * 2 + 2).toInt(16).toByte() }
            return decodeText(bytes)
        }
        val buf = java.io.ByteArrayOutputStream()
        var depth = 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    val n = text[i + 1]
                    when (n) {
                        'n' -> buf.write('\n'.code)
                        'r' -> buf.write('\r'.code)
                        't' -> buf.write('\t'.code)
                        'b', 'f' -> {}
                        '(', ')', '\\' -> buf.write(n.code)
                        in '0'..'7' -> {
                            var j = i + 1
                            var v = 0
                            while (j < text.length && j < i + 4 && text[j] in '0'..'7') {
                                v = v * 8 + (text[j] - '0')
                                j++
                            }
                            buf.write(v and 0xFF)
                            i = j
                            continue
                        }
                        else -> buf.write(n.code)
                    }
                    i += 2
                    continue
                }
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            buf.write(c.code and 0xFF)
            i++
        }
        return decodeText(buf.toByteArray())
    }

    fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> {
            val utf = String(bytes, Charsets.UTF_8)
            if (utf.contains('�')) String(bytes, java.nio.charset.Charset.forName("windows-1251")) else utf
        }
    }

    private fun clean(raw: String): String? {
        val s = raw.replace(Regex("[\\u0000-\\u001F]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (s.length < 2 || s.length > 200) return null
        val lower = s.lowercase()
        if (lower == "untitled" || lower.startsWith("microsoft word") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".pdf") || lower.endsWith(".indd")) return null
        if (s.count { it.isLetter() } < 2) return null
        return s
    }
}
