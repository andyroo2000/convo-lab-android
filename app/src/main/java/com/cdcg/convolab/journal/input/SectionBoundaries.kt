package com.cdcg.convolab.journal.input

import java.text.BreakIterator
import java.util.Locale

object SectionBoundaries {
    fun resize(sections: List<InputSection>, selected: Int, direction: Int, candidates: List<JapaneseCandidate>): List<InputSection> {
        val part = sections.getOrNull(selected) ?: return sections
        val next = sections.getOrNull(selected + 1)
        val step = step(part.reading, next?.reading.orEmpty(), direction)
        if (step == 0) return sections
        val reading = sections.joinToString("") { it.reading }
        val boundary = part.start + part.reading.length + direction * step
        val pairEnd = next?.let { it.start + it.reading.length } ?: (part.start + part.reading.length)
        val replacements = listOf(InputSelection(part.start, boundary), InputSelection(boundary, pairEnd))
            .filter { it.start < it.end }.map { PhraseSections.boundary(reading, it, candidates) }
        return sections.take(selected) + replacements + sections.drop(selected + if (next == null) 1 else 2)
    }
    private fun step(reading: String, next: String, direction: Int): Int {
        val iterator = BreakIterator.getCharacterInstance(Locale.JAPANESE)
        if (direction > 0) {
            if (next.isEmpty()) return 0
            iterator.setText(next)
            return iterator.following(0)
        }
        iterator.setText(reading)
        val start = iterator.preceding(reading.length)
        return if (start > 0) reading.length - start else 0
    }
}
