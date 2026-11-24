package com.flerty.wikiflerty.reader

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

class InjectedXhtmlTest {
    private fun parse(head: String) {
        val doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>" + head + "</head><body><p>Текст</p></body></html>"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(doc.byteInputStream(Charsets.UTF_8))
    }

    @Test
    fun bootIsWellFormedXml() = parse(BOOT)

    @Test
    fun comicIsWellFormedXml() = parse(BOOT + COMIC)
}
