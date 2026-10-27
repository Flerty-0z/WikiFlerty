package com.flerty.wikiflerty.data.convert

import java.io.File
import java.util.zip.ZipFile

object BookConverter {
    enum class Kind { Epub, Fb2, Fb2Zip, Txt, Pdf, Unknown }

    fun detect(file: File): Kind {
        val head = ByteArray(4096)
        val read = file.inputStream().use { it.read(head) }.coerceAtLeast(0)
        if (read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return runCatching {
                ZipFile(file).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toList()
                    when {
                        names.any { it == "META-INF/container.xml" } -> Kind.Epub
                        names.any { it.lowercase().endsWith(".fb2") } -> Kind.Fb2Zip
                        else -> Kind.Unknown
                    }
                }
            }.getOrDefault(Kind.Unknown)
        }
        if (read >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF") return Kind.Pdf
        val sample = String(head, 0, read, Charsets.ISO_8859_1)
        if (sample.contains("<FictionBook")) return Kind.Fb2
        val binary = (0 until read).count { i -> val b = head[i].toInt() and 0xFF; b == 0 || (b < 0x09) || (b in 0x0E..0x1A) }
        val utf16 = read >= 2 && ((head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) || (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()))
        return if (utf16 || (read > 0 && binary <= read / 100)) Kind.Txt else Kind.Unknown
    }

    fun toEpub(source: File, kind: Kind, target: File, fallbackTitle: String, identifier: String) {
        when (kind) {
            Kind.Fb2 -> source.inputStream().buffered().use { Fb2Converter(identifier).convert(it, target, fallbackTitle) }
            Kind.Fb2Zip -> ZipFile(source).use { zip ->
                val entry = zip.entries().asSequence().first { it.name.lowercase().endsWith(".fb2") }
                zip.getInputStream(entry).buffered().use { Fb2Converter(identifier).convert(it, target, fallbackTitle) }
            }
            Kind.Txt -> TxtConverter(identifier).convert(source.readBytes(), target, fallbackTitle)
            else -> throw IllegalArgumentException("unsupported")
        }
    }
}
