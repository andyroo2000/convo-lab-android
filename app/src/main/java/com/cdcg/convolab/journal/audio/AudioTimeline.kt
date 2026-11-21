package com.cdcg.convolab.journal.audio

import com.cdcg.convolab.journal.text.RubyDocument

object AudioTimeline {
    fun validated(sentences: List<SentenceTiming>, text: String): List<SentenceTiming> {
        if (sentences.joinToString("") { it.text } != text) return emptyList()
        var previous = -1.0
        for (sentence in sentences) {
            if (!validTime(sentence) || sentence.start < previous) return emptyList()
            previous = sentence.start
        }
        return sentences
    }
    private fun validTime(sentence: SentenceTiming) = sentence.start.isFinite() && sentence.end.isFinite() &&
        sentence.start >= 0 && sentence.end > sentence.start && sentence.end <= 86_400

    fun current(sentences: List<SentenceTiming>, seconds: Double): Int? =
        sentences.indexOfLast { seconds >= it.start && seconds < it.end }.takeIf { it >= 0 }

    fun adjacent(sentences: List<SentenceTiming>, seconds: Double, direction: Int): Double? {
        if (sentences.isEmpty()) return null
        val index = sentences.indexOfLast { it.start <= seconds }.coerceAtLeast(0)
        return sentences[(index + direction).coerceIn(sentences.indices)].start
    }

    /** Only annotate exact UTF-16 spans; incomplete/mismatched alignment leaves ordinary playable text. */
    fun readings(reading: String, sentences: List<SentenceTiming>): List<String>? {
        val tokens = RubyDocument.parse(reading)
        if (tokens.joinToString("") { it.text } != sentences.joinToString("") { it.text }) return null
        var start = 0
        return sentences.map { sentence ->
            val end = start + sentence.text.length
            var offset = 0
            val slice = buildString {
                for (token in tokens) {
                    val next = offset + token.text.length
                    val from = maxOf(offset, start)
                    val to = minOf(next, end)
                    if (from < to) {
                        append(token.text.substring(from - offset, to - offset))
                        if (from == offset && to == next) token.reading?.let { append("[$it]") }
                    }
                    offset = next
                }
            }
            start = end
            slice
        }
    }
}
