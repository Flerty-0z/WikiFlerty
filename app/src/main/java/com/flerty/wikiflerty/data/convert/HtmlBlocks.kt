package com.flerty.wikiflerty.data.convert

object HtmlBlocks {
    private val drop = Regex("(?is)<!--.*?-->|<\\?.*?\\?>|<!\\[CDATA\\[.*?]]>|<(script|style|head|title|noscript|svg:svg|svg|template|iframe|object|button|select|form|nav)\\b[^>]*?(?:/>|>.*?</\\1\\s*>)")
    private val token = Regex("<(/?)([a-zA-Z][\\w:.-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>|([^<]+)|(<)")
    private val attr = Regex("([a-zA-Z_:][\\w:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")

    private val blockTags = setOf(
        "p", "div", "li", "ul", "ol", "dl", "dd", "dt", "blockquote", "section", "article", "main", "aside", "header", "footer",
        "address", "center", "pre", "table", "tr", "thead", "tbody", "tfoot", "caption", "figure", "figcaption", "body", "html",
        "h1", "h2", "h3", "h4", "h5", "h6", "hr", "br", "td", "th", "poem", "stanza", "v", "epigraph", "cite",
    )

    private class Frame(val tag: String, val heading: Int, val align: Align?, val quote: Boolean, val bold: Boolean, val italic: Boolean, val underline: Boolean)

    fun attrs(raw: String): Map<String, String> {
        val out = HashMap<String, String>()
        attr.findAll(raw).forEach { m ->
            val v = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: ""
            out[m.groupValues[1].lowercase()] = Entities.unescape(v)
        }
        return out
    }

    private fun styleOf(a: Map<String, String>): Triple<Align?, Boolean, Boolean> {
        val style = (a["style"] ?: "").lowercase().replace(" ", "")
        val align = when {
            a["align"]?.lowercase() == "center" || style.contains("text-align:center") -> Align.Center
            a["align"]?.lowercase() == "right" || style.contains("text-align:right") || style.contains("text-align:end") -> Align.End
            else -> null
        }
        val bold = style.contains("font-weight:bold") || Regex("font-weight:(6|7|8|9)00").containsMatchIn(style)
        val italic = style.contains("font-style:italic")
        return Triple(align, bold, italic)
    }

    fun parse(html: String, doc: RichDoc, image: (String, Map<String, String>) -> String?) {
        val bodyAt = Regex("(?i)<body\\b[^>]*>").find(html)
        val start = bodyAt?.range?.last?.plus(1) ?: 0
        val clean = drop.replace(html.substring(start), " ")
        val stack = ArrayList<Frame>()
        val line = LineBuilder()
        var bold = 0
        var italic = 0
        var underline = 0
        var sup = 0
        var sub = 0
        var pre = 0

        fun heading() = stack.lastOrNull { it.heading > 0 }?.heading ?: 0
        fun align() = stack.lastOrNull { it.align != null }?.align ?: Align.Start
        fun quote() = stack.any { it.quote }
        fun frameBold() = stack.any { it.bold }
        fun frameItalic() = stack.any { it.italic }
        fun frameUnderline() = stack.any { it.underline }

        fun flush() {
            if (line.isEmpty) {
                line.runs.clear()
                return
            }
            val runs = line.take()
            if (runs.isEmpty()) return
            val h = heading()
            doc.blocks += if (h > 0) Block.Heading(h, runs) else Block.Para(runs, align(), quote())
        }

        fun text(t: String) {
            val s = Entities.unescape(t)
            if (pre > 0) {
                val parts = s.split('\n')
                parts.forEachIndexed { i, p ->
                    if (i > 0) flush()
                    line.add(p, bold > 0 || frameBold(), italic > 0 || frameItalic(), underline > 0 || frameUnderline(), sup > 0, sub > 0)
                }
                return
            }
            line.add(s, bold > 0 || frameBold(), italic > 0 || frameItalic(), underline > 0 || frameUnderline(), sup > 0, sub > 0)
        }

        token.findAll(clean).forEach { m ->
            val raw = m.groups[4]?.value
            if (raw != null) {
                text(raw)
                return@forEach
            }
            if (m.groups[5] != null) {
                text("<")
                return@forEach
            }
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase().substringAfter(':').let { if (m.groupValues[2].lowercase() == "mbp:pagebreak") "pagebreak" else it }
            val rest = m.groupValues[3]
            val selfClosing = rest.trimEnd().endsWith("/")
            when (name) {
                "pagebreak" -> {
                    flush()
                    doc.blocks += Block.Break
                }
                "br" -> if (heading() > 0) line.add(" ") else flush()
                "hr" -> {
                    flush()
                    doc.blocks += Block.Rule
                }
                "img", "image" -> {
                    if (closing) return@forEach
                    val a = attrs(rest)
                    val src = a["src"] ?: a["xlink:href"] ?: a["href"] ?: a["recindex"]?.let { "recindex:$it" } ?: ""
                    val href = image(src, a)
                    if (href != null) {
                        flush()
                        doc.blocks += Block.Image(href)
                    }
                }
                "b", "strong" -> if (closing) bold = (bold - 1).coerceAtLeast(0) else if (!selfClosing) bold++
                "i", "em", "dfn", "var" -> if (closing) italic = (italic - 1).coerceAtLeast(0) else if (!selfClosing) italic++
                "u", "ins" -> if (closing) underline = (underline - 1).coerceAtLeast(0) else if (!selfClosing) underline++
                "sup" -> if (closing) sup = (sup - 1).coerceAtLeast(0) else if (!selfClosing) sup++
                "sub" -> if (closing) sub = (sub - 1).coerceAtLeast(0) else if (!selfClosing) sub++
                "span", "font", "a", "small", "big", "s", "strike", "del", "abbr", "q", "code", "tt", "kbd", "samp", "mark", "label" -> {
                    if (closing) {
                        val i = stack.indexOfLast { it.tag == name }
                        if (i >= 0) stack.removeAt(i)
                    } else if (!selfClosing) {
                        val (_, b, it) = styleOf(attrs(rest))
                        stack += Frame(name, 0, null, false, b, it, false)
                    }
                }
                "td", "th" -> if (!closing) line.add(" ")
                else -> if (name in blockTags || name == "pagebreak") {
                    flush()
                    if (closing) {
                        val i = stack.indexOfLast { it.tag == name }
                        if (i >= 0) while (stack.size > i) stack.removeAt(stack.lastIndex)
                        if (name == "pre") pre = (pre - 1).coerceAtLeast(0)
                    } else if (!selfClosing) {
                        val a = attrs(rest)
                        val (al, b, it) = styleOf(a)
                        val h = if (name.length == 2 && name[0] == 'h' && name[1].isDigit()) name[1].digitToInt() else 0
                        val cls = (a["class"] ?: "").lowercase()
                        val isTitle = h == 0 && (cls.contains("title") || cls.contains("chapter") || cls.contains("heading")) && name in setOf("p", "div")
                        val q = name == "blockquote" || name == "epigraph" || cls.contains("epigraph") || cls.contains("cite")
                        stack += Frame(name, if (isTitle) 2 else h, if (name == "center") Align.Center else al, q, b, it, false)
                        if (name == "li") line.add("• ")
                        if (name == "pre") pre++
                        if (a["style"]?.lowercase()?.replace(" ", "")?.contains("page-break-before:always") == true) doc.blocks += Block.Break
                    }
                }
            }
        }
        flush()
    }
}
