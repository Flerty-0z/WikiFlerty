package com.flerty.wikiflerty.data.convert

object ChapterHeading {
    private const val ORDINALS = "перв[аыо][ейя]|втор[аыо][ейя]|трет[ьи][ейя]|четв[её]рт[аыо][ейя]|пят[аыо][ейя]|шест[аыо][ейя]|седьм[аыо][ейя]|восьм[аыо][ейя]|девят[аыо][ейя]|десят[аыо][ейя]|" +
        "одиннадцат[аыо][ейя]|двенадцат[аыо][ейя]|тринадцат[аыо][ейя]|четырнадцат[аыо][ейя]|пятнадцат[аыо][ейя]|шестнадцат[аыо][ейя]|семнадцат[аыо][ейя]|восемнадцат[аыо][ейя]|девятнадцат[аыо][ейя]|двадцат[аыо][ейя]|последн[яи][яй]|" +
        "один|два|три|четыре|пять|шесть|семь|восемь|девять|десять|" +
        "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|last"

    private val numbered = Regex(
        "^(глава|часть|книга|том|chapter|part|book)\\s*[№#]?\\s*(\\d{1,3}|[ivxlc]{1,7}|$ORDINALS)(?![\\p{L}\\d])([\\s.:\\-—–)]+.{0,70})?$",
        RegexOption.IGNORE_CASE,
    )
    private val named = Regex(
        "^(пролог|эпилог|предисловие|послесловие|вступление|интерлюдия|prologue|epilogue|preface|afterword|interlude|introduction)([\\s.:\\-—–]+.{0,60})?$",
        RegexOption.IGNORE_CASE,
    )
    private val bare = Regex("^(\\d{1,3}|[IVXLC]{1,7})[.)]?$")

    fun isHeading(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 80) return false
        if (t.last() in ",;—–-") return false
        if (numbered.matches(t)) return true
        if (named.matches(t)) return t.split(Regex("\\s+")).size <= 8 && !t.trimEnd('.', '!', '?', '…').contains(Regex("[,;]"))
        return bare.matches(t)
    }

    fun number(text: String): Int? {
        val t = text.trim().trimEnd('.', ')')
        t.toIntOrNull()?.let { return it }
        if (!Regex("^[IVXLC]{1,7}$").matches(t)) return null
        val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
        var total = 0
        t.forEachIndexed { i, c ->
            val cur = v.getValue(c)
            val next = t.getOrNull(i + 1)?.let { v.getValue(it) } ?: 0
            total += if (cur < next) -cur else cur
        }
        return total
    }
}
