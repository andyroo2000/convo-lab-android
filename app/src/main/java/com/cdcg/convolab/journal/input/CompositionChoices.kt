package com.cdcg.convolab.journal.input

internal class CompositionChoices {
    var candidates: List<JapaneseCandidate> = emptyList()
        private set
    var sections: List<InputSection> = emptyList()
        private set
    var selected = -1
        private set
    var highlighted = -1
        private set
    private var manual = false
    val part get() = sections.getOrNull(selected)
    val boundaries get() = if (manual) sections.drop(1).map { it.start } else emptyList()

    fun reset() { candidates = emptyList(); sections = emptyList(); selected = -1; highlighted = -1; manual = false }
    fun receive(reading: String, result: List<JapaneseCandidate>) {
        candidates = result
        sections = if (manual) PhraseSections.expand(sections, result) else PhraseSections.build(reading, result)
        selected = selected.coerceAtMost(sections.lastIndex)
    }
    fun focus(direction: Int) { if (sections.isNotEmpty()) selected = (selected + direction).coerceIn(0, sections.lastIndex) }
    fun resize(direction: Int) {
        if (sections.isEmpty() || direction !in listOf(-1, 1)) return
        selected = selected.coerceAtLeast(0)
        manual = true
        sections = SectionBoundaries.resize(sections, selected, direction, candidates)
    }
    fun cycle(choices: List<String>) { highlighted = if (choices.isEmpty()) -1 else (highlighted + 1) % choices.size }
    fun restoreHighlight(text: String?, choices: List<String>) { highlighted = choices.indexOf(text) }
    fun options(reading: String, raw: String): List<String> {
        val selectedPart = part
        val kana = selectedPart?.reading ?: reading
        val ranked = selectedPart?.let { listOf(it.text) + it.choices } ?: candidates.map { it.text }
        return (listOfNotNull(ranked.firstOrNull(), kana, Romaji.katakana(kana), raw.takeIf { selectedPart == null }) + ranked.drop(1))
            .filter { it.isNotEmpty() }.distinct()
    }
}
