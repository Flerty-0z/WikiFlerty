package com.flerty.wikiflerty.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * A tiny Markdown renderer for AI answers: **bold**, *italic*, `code`,
 * "- " lists and "#" headings (shown as bold lines).
 */
fun markdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.replace("\r", "").split("\n")
    lines.forEachIndexed { index, raw ->
        var line = raw
        val heading = Regex("^\\s{0,3}#{1,6}\\s+").find(line)
        val bullet = Regex("^\\s*([-*•])\\s+").find(line)
        val numbered = Regex("^\\s*(\\d+)[.)]\\s+").find(line)
        when {
            heading != null -> {
                line = line.substring(heading.range.last + 1)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(line) }
            }
            bullet != null -> {
                append("  •  ")
                inline(line.substring(bullet.range.last + 1))
            }
            numbered != null -> {
                append("  ${numbered.groupValues[1]}.  ")
                inline(line.substring(numbered.range.last + 1))
            }
            else -> inline(line)
        }
        if (index < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.inline(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text.substring(i))
                    return
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            (text[i] == '*' || text[i] == '_') && i + 1 < text.length && text[i + 1] != ' ' -> {
                val mark = text[i]
                val end = text.indexOf(mark, i + 1)
                if (end > i + 1 && text[end - 1] != ' ') {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
