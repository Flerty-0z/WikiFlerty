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

    private class Frame(val tag: String, val heading: Int, val align: Align?, val quote: Boolean, val bold: Boolean, val italic: Boolean, val underline: Boolean, val href: String? = null, val big: Int = 0)

    class Look(val align: Align?, val bold: Boolean, val italic: Boolean, val big: Int)

    fun css(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val out = HashMap<String, String>()
        val body = text.replace(Regex("(?s)/\\*.*?\\*/"), " ").replace(Regex("(?s)@media[^{]*\\{((?:[^{}]*\\{[^}]*\\})*)\\s*\\}"), "$1")
        Regex("([^{}]+)\\{([^}]*)\\}").findAll(body).forEach { m ->
            val decl = m.groupValues[2].lowercase().replace(Regex("\\s+"), "")
            m.groupValues[1].split(',').forEach { sel ->
                val cls = Regex("\\.([A-Za-z_][\\w-]*)\\s*$").find(sel.trim())?.groupValues?.get(1) ?: return@forEach
                out[cls] = (out[cls]?.let { "$it;" } ?: "") + decl
            }
        }
        return out
    }

    private fun bigOf(tag: String, a: Map<String, String>, style: String): Int {
        if (tag == "font") {
            val v = a["size"]?.trim().orEmpty()
            when {
                v.startsWith("+") -> v.drop(1).toIntOrNull()?.let { return if (it >= 3) 1 else if (it == 2) 2 else 0 }
                else -> v.toIntOrNull()?.let { return if (it >= 6) 1 else if (it == 5) 2 else 0 }
            }
        }
        val size = Regex("font-size:([^;]+)").findAll(style).lastOrNull()?.groupValues?.get(1)?.trim() ?: return 0
        val n = Regex("^([0-9.]+)(em|rem|%)$").find(size)
        if (n != null) {
            val v = n.groupValues[1].toDoubleOrNull() ?: return 0
            val em = if (n.groupValues[2] == "%") v / 100 else v
            return if (em >= 1.6) 1 else if (em >= 1.3) 2 else 0
        }
        return when (size) {
            "xx-large", "x-large", "xxx-large" -> 1
            "large", "larger" -> 2
            else -> 0
        }
    }

    fun attrs(raw: String): Map<String, String> {
        val out = HashMap<String, String>()
        attr.findAll(raw).forEach { m ->
            val v = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: ""
            out[m.groupValues[1].lowercase()] = Entities.unescape(v)
        }
        return out
    }

    private fun styleOf(tag: String, a: Map<String, String>, css: Map<String, String>): Look {
        val own = (a["style"] ?: "").lowercase().replace(" ", "")
        val cls = a["class"]?.split(' ', '\t')?.mapNotNull { css[it.trim()] }.orEmpty().joinToString(";")
        val style = if (cls.isEmpty()) own else "$cls;$own"
        val align = when {
            a["align"]?.lowercase() == "center" || style.contains("text-align:center") -> Align.Center
            a["align"]?.lowercase() == "right" || style.contains("text-align:right") || style.contains("text-align:end") -> Align.End
            else -> null
        }
        val weight = Regex("font-weight:([a-z0-9]+)").findAll(style).lastOrNull()?.groupValues?.get(1)
        val bold = weight == "bold" || weight == "bolder" || (weight?.toIntOrNull() ?: 0) >= 600
        val italic = Regex("font-style:([a-z]+)").findAll(style).lastOrNull()?.groupValues?.get(1).let { it == "italic" || it == "oblique" }
        return Look(align, bold, italic, bigOf(tag, a, style))
    }

    private fun linkOf(a: Map<String, String>): String? {
        a["filepos"]?.trim()?.toIntOrNull()?.let { return "#fp$it" }
        val h = a["href"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            h.startsWith("#") -> h
            h.startsWith("http://", true) || h.startsWith("https://", true) || h.startsWith("mailto:", true) -> h
            h.contains('#') -> "#" + h.substringAfter('#')
            else -> null
        }
    }

    fun parse(html: String, doc: RichDoc, css: Map<String, String> = emptyMap(), image: (String, Map<String, String>) -> String?) {
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
        fun link() = stack.lastOrNull { it.href != null }?.href
        fun big() = stack.filter { it.big > 0 }.minOfOrNull { it.big } ?: 0
        var total = 0
        var bigChars = 0
        var bigLevel = 9
        val inline = ArrayList<String>()

        fun anchor(a: Map<String, String>, tag: String) {
            val id = a["id"] ?: (if (tag == "a") a["name"] else null) ?: return
            if (id.isBlank()) return
            if (line.isEmpty) doc.blocks += Block.Anchor(id) else inline += id
        }

        fun flush() {
            val chars = total
            val bigs = bigChars
            val level = bigLevel
            total = 0
            bigChars = 0
            bigLevel = 9
            if (line.isEmpty) {
                line.runs.clear()
                inline.forEach { doc.blocks += Block.Anchor(it) }
                inline.clear()
                return
            }
            val runs = line.take()
            inline.forEach { doc.blocks += Block.Anchor(it) }
            inline.clear()
            if (runs.isEmpty() || runs.plain.trim() == "•") return
            val h = heading().takeIf { it > 0 } ?: if (chars in 1..160 && bigs * 10 >= chars * 9 && runs.filter { it.href != null }.sumOf { it.text.trim().length } <= 6 && !quote()) level.takeIf { it < 9 } ?: 0 else 0
            doc.blocks += if (h > 0) Block.Heading(h, runs) else Block.Para(runs, align(), quote())
        }

        fun count(s: String) {
            if (link() != null) return
            val n = s.count { !it.isWhitespace() }
            if (n == 0) return
            total += n
            val b = big()
            if (b > 0) {
                bigChars += n
                if (b < bigLevel) bigLevel = b
            }
        }

        fun text(t: String) {
            val s = Entities.unescape(t)
            if (pre > 0) {
                val parts = s.split('\n')
                parts.forEachIndexed { i, p ->
                    if (i > 0) flush()
                    count(p)
                    line.add(p, bold > 0 || frameBold(), italic > 0 || frameItalic(), underline > 0 || frameUnderline(), sup > 0, sub > 0, link())
                }
                return
            }
            count(s)
            line.add(s, bold > 0 || frameBold(), italic > 0 || frameItalic(), underline > 0 || frameUnderline(), sup > 0, sub > 0, link())
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
                "br" -> if (closing) Unit else if (heading() > 0 || (total in 1..120 && bigChars == total)) line.add(" ") else flush()
                "hr" -> if (!closing) {
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
                "b", "strong" -> if (closing) bold = (bold - 1).coerceAtLeast(0) else {
                    anchor(attrs(rest), name)
                    if (!selfClosing) bold++
                }
                "i", "em", "dfn", "var" -> if (closing) italic = (italic - 1).coerceAtLeast(0) else if (!selfClosing) italic++
                "u", "ins" -> if (closing) underline = (underline - 1).coerceAtLeast(0) else if (!selfClosing) underline++
                "sup" -> if (closing) sup = (sup - 1).coerceAtLeast(0) else if (!selfClosing) sup++
                "sub" -> if (closing) sub = (sub - 1).coerceAtLeast(0) else if (!selfClosing) sub++
                "span", "font", "a", "small", "big", "s", "strike", "del", "abbr", "q", "code", "tt", "kbd", "samp", "mark", "label" -> {
                    if (closing) {
                        val i = stack.indexOfLast { it.tag == name }
                        if (i >= 0) stack.removeAt(i)
                    } else {
                        val a = attrs(rest)
                        anchor(a, name)
                        if (!selfClosing) {
                            val look = styleOf(name, a, css)
                            stack += Frame(name, 0, null, false, look.bold, look.italic, false, if (name == "a") linkOf(a) else null, look.big)
                        }
                    }
                }
                "td", "th" -> if (!closing) line.add(" ")
                else -> if (name in blockTags || name == "pagebreak") {
                    flush()
                    if (closing) {
                        val i = stack.indexOfLast { it.tag == name }
                        if (i >= 0) while (stack.size > i) stack.removeAt(stack.lastIndex)
                        if (name == "pre") pre = (pre - 1).coerceAtLeast(0)
                    } else {
                        val a = attrs(rest)
                        anchor(a, name)
                        if (selfClosing) return@forEach
                        val look = styleOf(name, a, css)
                        val al = look.align
                        val b = look.bold
                        val it = look.italic
                        val h = if (name.length == 2 && name[0] == 'h' && name[1].isDigit()) name[1].digitToInt() else 0
                        val cls = (a["class"] ?: "").lowercase()
                        val isTitle = h == 0 && (cls.contains("title") || cls.contains("chapter") || cls.contains("heading")) && name in setOf("p", "div")
                        val q = name == "blockquote" || name == "epigraph" || cls.contains("epigraph") || cls.contains("cite")
                        stack += Frame(name, if (isTitle) 2 else h, if (name == "center") Align.Center else al, q, b, it, false, null, look.big)
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
