package com.cdcg.convolab.journal.input

object PhraseSections {
    fun build(reading: String, candidates: List<JapaneseCandidate>): List<InputSection> {
        val first = candidates.firstOrNull() ?: return emptyList()
        val parts = CandidateAlignment.validParts(first, first.sections).ifEmpty { listOf(CandidatePart(first.text, reading)) }
        var start = 0
        return parts.map { part ->
            val choices = alternatives(candidates, InputSelection(start, start + part.reading.length))
            InputSection(start, part.reading, part.text, spellings(part.reading, part.text, choices)).also { start += part.reading.length }
        }
    }
    fun expand(sections: List<InputSection>, candidates: List<JapaneseCandidate>) = sections.map { section ->
        val choices = alternatives(candidates, InputSelection(section.start, section.start + section.reading.length))
        if (section.pendingBoundary) section.copy(text = choices.firstOrNull() ?: section.reading,
            choices = spellings(section.reading, choices.firstOrNull(), choices), pendingBoundary = false)
        else section.copy(choices = (section.choices + choices).distinct())
    }
    fun boundary(reading: String, span: InputSelection, candidates: List<JapaneseCandidate>): InputSection {
        val kana = reading.substring(span.start, span.end)
        val choices = alternatives(candidates, span)
        val text = choices.firstOrNull() ?: kana
        return InputSection(span.start, kana, text, spellings(kana, text, choices), pendingBoundary = true)
    }
    fun alternatives(candidates: List<JapaneseCandidate>, span: InputSelection) = candidates.mapNotNull { CandidateAlignment.text(it, span) }.distinct()
    private fun spellings(reading: String, top: String?, alternatives: List<String>) =
        (listOfNotNull(reading, top) + alternatives + Romaji.katakana(reading)).filter { it.isNotEmpty() }.distinct()
}
