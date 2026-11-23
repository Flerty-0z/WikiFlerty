package com.flerty.wikiflerty.data

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

object EpubMetaWriter {
    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val OPF = "http://www.idpf.org/2007/opf"

    fun write(source: File, target: File, book: Book, description: String?): Boolean = runCatching {
        ZipFile(source).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return false
            val opfPath = parse(zip.getInputStream(container).readBytes()).getElementsByTagNameNS("*", "rootfile")
                .let { if (it.length > 0) (it.item(0) as Element).getAttribute("full-path") else null }
                ?.takeIf { it.isNotBlank() } ?: return false
            val opfEntry = zip.getEntry(opfPath) ?: return false
            val doc = parse(zip.getInputStream(opfEntry).readBytes())
            val metadata = doc.getElementsByTagNameNS(OPF, "metadata").item(0) as? Element
                ?: doc.getElementsByTagNameNS("*", "metadata").item(0) as? Element
                ?: return false
            apply(doc, metadata, book, description)
            val opfBytes = serialize(doc)
            ZipOutputStream(target.outputStream().buffered()).use { out ->
                zip.getEntry("mimetype")?.let { m ->
                    val bytes = zip.getInputStream(m).readBytes()
                    val crc = CRC32().apply { update(bytes) }
                    out.putNextEntry(ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        this.crc = crc.value
                    })
                    out.write(bytes)
                    out.closeEntry()
                }
                for (e in zip.entries()) {
                    if (e.name == "mimetype" || e.isDirectory) continue
                    out.putNextEntry(ZipEntry(e.name))
                    if (e.name == opfPath) out.write(opfBytes) else zip.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        true
    }.getOrElse {
        target.delete()
        false
    }

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(bytes.inputStream())

    private fun serialize(doc: Document): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun apply(doc: Document, metadata: Element, book: Book, description: String?) {
        fun set(name: String, value: String?) {
            val v = value?.trim().orEmpty()
            val existing = metadata.getElementsByTagNameNS(DC, name)
            if (v.isEmpty()) return
            if (existing.length > 0) {
                existing.item(0).textContent = v
                for (i in existing.length - 1 downTo 1) existing.item(i).let { it.parentNode.removeChild(it) }
            } else {
                val prefix = metadata.lookupPrefix(DC) ?: doc.documentElement.lookupPrefix(DC) ?: "dc"
                metadata.appendChild(doc.createElementNS(DC, "$prefix:$name").apply { textContent = v })
            }
        }
        set("title", book.title)
        set("creator", book.author)
        set("publisher", book.fact(BookFacts.Publisher))
        set("date", book.fact(BookFacts.Year))
        set("description", description)
        book.fact(BookFacts.Isbn).takeIf { it.isNotBlank() }?.let { isbn ->
            val ids = metadata.getElementsByTagNameNS(DC, "identifier")
            val has = (0 until ids.length).any { ids.item(it).textContent.replace("-", "").contains(isbn.replace("-", "")) }
            if (!has) {
                val prefix = metadata.lookupPrefix(DC) ?: "dc"
                metadata.appendChild(doc.createElementNS(DC, "$prefix:identifier").apply { textContent = "urn:isbn:$isbn" })
            }
        }
        val series = book.series.firstOrNull() ?: return
        val metas = metadata.getElementsByTagNameNS("*", "meta")
        for (i in metas.length - 1 downTo 0) {
            val m = metas.item(i) as Element
            val n = m.getAttribute("name")
            val prop = m.getAttribute("property")
            if (n == "calibre:series" || n == "calibre:series_index" || prop == "belongs-to-collection" || prop == "group-position" || prop == "collection-type") m.parentNode.removeChild(m)
        }
        val ns = metadata.namespaceURI ?: OPF
        val prefix = metadata.prefix
        fun meta(): Element = doc.createElementNS(ns, if (prefix.isNullOrBlank()) "meta" else "$prefix:meta")
        metadata.appendChild(meta().apply { setAttribute("name", "calibre:series"); setAttribute("content", series.name) })
        series.numberLabel?.let { n -> metadata.appendChild(meta().apply { setAttribute("name", "calibre:series_index"); setAttribute("content", n) }) }
        if (doc.documentElement.getAttribute("version").startsWith("3")) {
            metadata.appendChild(meta().apply { setAttribute("property", "belongs-to-collection"); setAttribute("id", "wf-series"); textContent = series.name })
            metadata.appendChild(meta().apply { setAttribute("refines", "#wf-series"); setAttribute("property", "collection-type"); textContent = "series" })
            series.numberLabel?.let { n -> metadata.appendChild(meta().apply { setAttribute("refines", "#wf-series"); setAttribute("property", "group-position"); textContent = n }) }
        }
    }
}
