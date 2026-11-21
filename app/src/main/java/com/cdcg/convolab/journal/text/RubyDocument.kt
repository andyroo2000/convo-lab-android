package com.cdcg.convolab.journal.text

data class RubyToken(val text: String, val reading: String? = null)

/** Matches the bracket/parenthesis reading convention shared with the iOS Journal. */
object RubyDocument {
    private val annotation = Regex("([\\p{IsHan}々〆\\p{IsHiragana}\\p{IsKatakana}]+)(?:\\[([^\\]]+)\\]|\\(([^)]+)\\))")
    private val particles = setOf("は", "が", "を", "に", "へ", "で", "と", "も", "の", "や", "か", "ね", "よ")

    fun parse(value: String, knownKanji: Set<Int> = emptySet()): List<RubyToken> {
        val tokens = mutableListOf<RubyToken>()
        var cursor = 0
        annotation.findAll(value).forEach { match ->
            val base = match.groupValues[1]
            val reading = match.groupValues[2].ifEmpty { match.groupValues[3] }.filterNot(Char::isWhitespace)
            if (base.codePoints().noneMatch(::isKanji) || !reading.all(::isKana)) return@forEach
            tokens += RubyToken(value.substring(cursor, match.range.first))
            tokens += normalize(base, reading, knownKanji)
            cursor = match.range.last + 1
        }
        tokens += RubyToken(value.substring(cursor))
        return tokens.filter { it.text.isNotEmpty() }
    }

    private fun normalize(base: String, reading: String, known: Set<Int>): List<RubyToken> {
        val prefix = base.takeWhile(::isKana)
        val suffix = base.takeLastWhile(::isKana)
        val word = base.substring(prefix.length, base.length - suffix.length)
        var adjusted = reading
        if (prefix.isNotEmpty() && prefix !in particles) adjusted = adjusted.removePrefix(prefix)
        if (suffix.isNotEmpty()) adjusted = adjusted.removeSuffix(suffix)
        val kanji = word.codePoints().filter(::isKanji).toArray()
        val hide = kanji.isNotEmpty() && kanji.all { it in known }
        return listOf(RubyToken(prefix), RubyToken(word, if (hide) null else adjusted.ifEmpty { reading }), RubyToken(suffix))
    }

    private fun isKana(character: Char) = character in '\u3040'..'\u30ff' || character == 'ー'
    private fun isKanji(point: Int) = Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN || point == '々'.code
}
