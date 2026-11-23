package com.flerty.wikiflerty.data.convert

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TortureTest {
    private val dir = kotlin.io.path.createTempDirectory("torture").toFile()

    private fun run(file: File, name: String): File {
        val kind = BookConverter.detect(file, name)
        val out = File(dir, "$name.epub")
        val started = System.currentTimeMillis()
        BookConverter.toEpub(file, kind, out, "Проверка", "id-" + name.hashCode(), name)
        val rt = Runtime.getRuntime()
        println("torture $name kind=$kind in=${file.length() / 1024}KB out=${out.length() / 1024}KB ms=${System.currentTimeMillis() - started} heap=${(rt.totalMemory() - rt.freeMemory()) / 1048576}MB")
        ZipFile(out).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertTrue("$name: mimetype", names.contains("mimetype"))
            assertTrue("$name: no pages", names.count { it.endsWith(".xhtml") || it.endsWith(".html") } > 0)
            val biggest = zip.entries().asSequence().filter { it.name.endsWith(".xhtml") }.maxOfOrNull { it.size } ?: 0
            assertTrue("$name: chapter too big $biggest", biggest < 2_500_000)
        }
        return out
    }

    private fun zip(name: String, entries: Map<String, ByteArray>): File {
        val f = File(dir, name)
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (k, v) ->
                z.putNextEntry(ZipEntry(k))
                z.write(v)
                z.closeEntry()
            }
        }
        return f
    }

    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 13, 73, 68, 65, 84, 120, -38, 99, -8, -1, -1, 63, 0, 5, -2, 2, -2, -89, 53, -127, -124, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)

    private fun docx(paragraphs: Int, images: Int): File {
        val body = StringBuilder()
        val rels = StringBuilder("<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        val media = HashMap<String, ByteArray>()
        for (i in 0 until paragraphs) {
            if (i % 400 == 0) body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Глава ${i / 400 + 1}</w:t></w:r></w:p>")
            body.append("<w:p w:rsidR=\"00A1B2C3\" w:rsidRDefault=\"00D4E5F6\"><w:pPr><w:spacing w:after=\"120\"/><w:ind w:firstLine=\"709\"/><w:jc w:val=\"both\"/></w:pPr>")
            repeat(3) { r ->
                body.append("<w:r w:rsidRPr=\"00112233\"><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:cs=\"Times New Roman\"/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/><w:lang w:val=\"ru-RU\"/>${if (r == 1) "<w:b/>" else ""}</w:rPr><w:t xml:space=\"preserve\">Строка $i часть $r — длинный текст абзаца для проверки памяти и скорости разбора документа. </w:t></w:r>")
            }
            body.append("</w:p>")
            if (images > 0 && i % (paragraphs / images).coerceAtLeast(1) == 0 && media.size < images) {
                val id = "rId${100 + media.size}"
                media["word/media/i${media.size}.png"] = png
                rels.append("<Relationship Id=\"$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/i${media.size - 1}.png\"/>")
                body.append("<w:p><w:r><w:drawing><a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:blipFill><a:blip r:embed=\"$id\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></w:drawing></w:r></w:p>")
            }
        }
        rels.append("</Relationships>")
        val doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>"
        return zip("big-$paragraphs-$images.docx", mapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".toByteArray(),
            "word/document.xml" to doc.toByteArray(),
            "word/_rels/document.xml.rels" to rels.toString().toByteArray(),
        ) + media)
    }

    @Test
    fun hugeDocx() {
        run(docx(60_000, 0), "huge.docx")
    }

    @Test
    fun docxWithImages() {
        run(docx(3_000, 40), "images.docx")
    }

    @Test
    fun brokenFb2() {
        val text = "<?xml version=\"1.0\" encoding=\"utf-8\"?><FictionBook><description><title-info><book-title>Битая</book-title></title-info></description><body><section><title><p>Глава 1</p></title>" +
            (1..3000).joinToString("") { "<p>Абзац $it <emphasis>без закрытия " } + "</body>"
        val f = File(dir, "broken.fb2").apply { writeText(text) }
        val e = runCatching { run(f, "broken.fb2") }.exceptionOrNull()
        println("torture broken.fb2 result=${e?.let { it::class.simpleName + ": " + it.message } ?: "ok"}")
        if (e != null) throw e
    }

    @Test
    fun hugeTxtCp1251() {
        val line = "Это длинная строка текста в кодировке Windows-1251 для проверки большого файла.\r\n"
        val f = File(dir, "huge.txt")
        f.outputStream().buffered().use { out -> repeat(180_000) { i -> if (i % 5_000 == 0) out.write("\r\nГлава ${i / 5_000 + 1}\r\n\r\n".toByteArray(charset("windows-1251"))); out.write(line.toByteArray(charset("windows-1251"))) } }
        run(f, "huge.txt")
    }

    @Test
    fun htmlWithoutHeadings() {
        val f = File(dir, "flat.html").apply { writeText("<html><body>" + (1..40_000).joinToString("") { "<p>Абзац номер $it без глав и заголовков.</p>" } + "</body></html>") }
        run(f, "flat.html")
    }

    @Test
    fun rtfCyrillic() {
        val f = File(dir, "doc.rtf").apply { writeText("{\\rtf1\\ansi\\ansicpg1251\\deff0{\\fonttbl{\\f0 Times New Roman;}}" + (1..2000).joinToString("") { "\\par Абзац $it \\'e0\\'e1\\'e2 текст" } + "}", charset("windows-1251")) }
        run(f, "doc.rtf")
    }

    @Test
    fun manyTinyChapters() {
        val text = "<?xml version=\"1.0\" encoding=\"utf-8\"?><FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"><description><title-info><book-title>Много глав</book-title></title-info></description><body>" +
            (1..3000).joinToString("") { "<section><title><p>Глава $it</p></title><p>Короткий текст главы $it.</p></section>" } + "</body></FictionBook>"
        run(File(dir, "many.fb2").apply { writeText(text) }, "many.fb2")
    }
}
