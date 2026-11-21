package com.cdcg.convolab.journal.input

data class CandidatePart(val text: String, val reading: String)
data class JapaneseCandidate(val text: String, val reading: String, val sections: List<CandidatePart> = emptyList(), val units: List<CandidatePart> = emptyList())
data class InputSection(val start: Int, val reading: String, val text: String, val choices: List<String>, val pendingBoundary: Boolean = false)

/** Slice only dictionary boundaries or literal kana edges; never guess kanji readings. */
object CandidateAlignment {
    fun text(candidate: JapaneseCandidate, span: InputSelection): String? =
        aligned(validParts(candidate, candidate.units), span) ?: aligned(validParts(candidate, candidate.sections), span)

    fun validParts(candidate: JapaneseCandidate, parts: List<CandidatePart>): List<CandidatePart> =
        parts.takeIf { it.joinToString("") { part -> part.reading } == candidate.reading && it.joinToString("") { part -> part.text } == candidate.text }.orEmpty()

    fun remaining(candidate: JapaneseCandidate, offset: Int): JapaneseCandidate? {
        if (offset == 0) return candidate
        val span = InputSelection(offset, candidate.reading.length)
        val ending = text(candidate, span) ?: return null
        return JapaneseCandidate(ending, candidate.reading.drop(offset), trim(candidate.sections, offset), trim(candidate.units, offset))
    }
    private fun trim(parts: List<CandidatePart>, offset: Int): List<CandidatePart> {
        var cursor = 0
        return parts.mapNotNull { part ->
            val start = cursor
            cursor += part.reading.length
            val from = (offset - start).coerceAtLeast(0)
            if (from >= part.reading.length) null else slice(part, InputSelection(from, part.reading.length))?.let { CandidatePart(it, part.reading.drop(from)) }
        }
    }
    private fun aligned(parts: List<CandidatePart>, span: InputSelection): String? {
        var offset = 0
        val result = StringBuilder()
        for (part in parts) {
            val next = offset + part.reading.length
            if (offset < span.end && next > span.start) {
                val range = InputSelection(maxOf(0, span.start - offset), minOf(part.reading.length, span.end - offset))
                result.append(slice(part, range) ?: return null)
            }
            offset = next
        }
        return result.toString().takeIf { offset >= span.end && it.isNotEmpty() }
    }
    private fun slice(part: CandidatePart, span: InputSelection): String? {
        val normalized = part.text.map { if (it in 'ァ'..'ヶ') (it.code - 0x60).toChar() else it }.joinToString("")
        val bounds = mutableMapOf(0 to 0, part.reading.length to part.text.length)
        for (index in 1..edge(normalized, part.reading)) bounds[index] = index
        for (index in 1..edge(normalized.reversed(), part.reading.reversed())) bounds[part.reading.length - index] = part.text.length - index
        val from = bounds[span.start] ?: return null
        val to = bounds[span.end] ?: return null
        return part.text.substring(from, to)
    }
    private fun edge(text: String, reading: String) = text.zip(reading).takeWhile { it.first == it.second }.size
}
