package com.flerty.wikiflerty.data.convert

import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

internal class XEl(@JvmField val name: String, @JvmField val attrs: Array<String>?) {
    @JvmField var children: ArrayList<XEl>? = null
    @JvmField var chars: String? = null
}

internal object Lite {
    private val skipped = hashSetOf(
        "rFonts", "lang", "sz", "szCs", "color", "kern", "spacing", "noProof", "proofErr", "shd", "highlight", "w", "position",
        "bCs", "iCs", "rtl", "cs", "ind", "keepNext", "keepLines", "widowControl", "contextualSpacing", "snapToGrid",
        "tblPr", "tblGrid", "trPr", "tcPr", "sectPr", "Fallback", "effectExtent", "docPr", "cNvGraphicFramePr", "spPr", "nvPicPr", "srcRect", "stretch", "extLst",
    )
    private val keptAttrs = hashSetOf("val", "id", "type", "styleId", "embed", "Id", "Target", "TargetMode", "name", "anchor")

    fun parse(input: InputStream): XEl? = runCatching {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(input, null)
        val stack = ArrayList<XEl>()
        var root: XEl? = null
        var skip = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (skip > 0 || name in skipped) {
                        skip++
                    } else {
                        var attrs: ArrayList<String>? = null
                        for (i in 0 until parser.attributeCount) {
                            val n = parser.getAttributeName(i)
                            if (n in keptAttrs) {
                                if (attrs == null) attrs = ArrayList(4)
                                attrs += n
                                attrs += parser.getAttributeValue(i)
                            }
                        }
                        val el = XEl(name, attrs?.toTypedArray())
                        val parent = stack.lastOrNull()
                        if (parent == null) root = el else (parent.children ?: ArrayList<XEl>(4).also { parent.children = it }).add(el)
                        stack += el
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (skip > 0) skip-- else if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (skip == 0) {
                        val el = stack.lastOrNull()
                        val t = parser.text
                        if (el != null && !t.isNullOrEmpty() && (el.name == "t" || el.name == "instrText" || t.isNotBlank())) {
                            el.chars = (el.chars ?: "") + t
                        }
                    }
                }
            }
            event = parser.next()
        }
        root
    }.getOrNull()

    fun XEl.local(): String = name

    fun XEl.kids(): List<XEl> = children ?: emptyList()

    fun XEl.kid(name: String): XEl? = children?.firstOrNull { it.name == name }

    fun XEl.attr(name: String): String? {
        val a = attrs ?: return null
        var i = 0
        while (i < a.size) {
            if (a[i] == name) return a[i + 1]
            i += 2
        }
        return null
    }

    fun XEl.all(name: String): List<XEl> {
        val out = ArrayList<XEl>()
        fun walk(e: XEl) {
            e.children?.forEach { k ->
                if (k.name == name) out += k
                walk(k)
            }
        }
        walk(this)
        return out
    }

    fun XEl.text(): String {
        val k = children ?: return chars ?: ""
        val sb = StringBuilder()
        chars?.let { sb.append(it) }
        fun walk(e: XEl) {
            e.chars?.let { sb.append(it) }
            e.children?.forEach { walk(it) }
        }
        k.forEach { walk(it) }
        return sb.toString()
    }
}
