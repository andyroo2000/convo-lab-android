package com.cdcg.convolab.journal.input

data class CompositionQuery(val reading: String, val boundaries: List<Int>, val revision: Long)
data class ConfirmedInput(val reading: String, val text: String, val start: Int, val end: Int)
data class InputSnapshot(val text: String, val selection: InputSelection, val composition: InputSelection?,
    val section: InputSelection?, val choices: List<String>, val query: CompositionQuery?, val highlighted: Int)

/** Inference never changes the document. Only typing or an explicit choice does. */
class JournalComposition(initial: String = "") {
    private val document = CompositionDocument(initial)
    private val menu = CompositionChoices()
    private var contextReading = ""
    private var revision = 0L
    private val confirmations = mutableListOf<ConfirmedInput>()

    fun external(text: String): InputSnapshot {
        document.external(text)
        contextReading = ""
        menu.reset()
        revision++
        return snapshot()
    }
    fun edit(change: InputEdit, selection: InputSelection, literal: Boolean = false): InputSnapshot {
        document.select(selection)
        if (!document.contains(change.start, change.start + change.length)) finishOutside()
        if (!literal && command(change.replacement)) return snapshot()
        menu.reset()
        if (literal) document.literal(change) else replaceInput(change)
        if (document.active?.raw == "" || document.reading().length > 120) document.finish()
        if (document.active == null) contextReading = ""
        revision++
        return snapshot()
    }
    fun reconcile(text: String, selection: InputSelection): InputSnapshot {
        if (text == document.text) return moveSelection(selection)
        val change = InputChange.between(document.text, text)
        edit(change, InputSelection(change.start, change.start + change.length), literal = change.replacement.length > 1)
        return snapshot()
    }
    fun moveSelection(selection: InputSelection): InputSnapshot {
        document.select(selection)
        if (!document.contains(selection.first, selection.last)) finishOutside()
        return snapshot()
    }
    fun receive(query: CompositionQuery, candidates: List<JapaneseCandidate>): InputSnapshot {
        if (snapshot().query != query) return snapshot()
        val highlighted = options().getOrNull(menu.highlighted)
        val remaining = candidates.mapNotNull { CandidateAlignment.remaining(it, contextReading.length) }
        menu.receive(document.reading(), remaining)
        menu.restoreHighlight(highlighted, options())
        return snapshot()
    }
    fun focusSection(direction: Int): InputSnapshot { menu.focus(direction); return snapshot() }
    fun resize(direction: Int): InputSnapshot { menu.resize(direction); revision++; return snapshot() }
    fun cycle(): InputSnapshot { menu.cycle(options()); return snapshot() }
    fun accept(): InputSnapshot = if (menu.highlighted < 0) commit() else choose(menu.highlighted)
    fun commit(): InputSnapshot {
        document.finish()
        contextReading = ""
        menu.reset()
        revision++
        return snapshot()
    }
    fun choose(index: Int): InputSnapshot {
        val choice = options().getOrNull(index) ?: return snapshot()
        val active = document.active ?: return snapshot()
        document.select(InputSelection(active.end))
        val part = menu.part
        val reading = document.reading()
        val start = contextReading.length + (part?.start ?: 0)
        confirmations += ConfirmedInput(contextReading + reading, choice, start, start + (part?.reading ?: reading).length)
        if (part == null) { document.commit(choice); contextReading = ""; menu.reset() }
        else commitSection(part, choice)
        revision++
        return snapshot()
    }
    fun takeConfirmed(): List<ConfirmedInput> = confirmations.toList().also { confirmations.clear() }

    fun snapshot(): InputSnapshot {
        val active = document.active
        val part = menu.part
        return InputSnapshot(document.text, document.selection, active?.let { InputSelection(it.start, it.end) },
            part?.takeIf { active != null }?.let { InputSelection(active!!.start + it.start, active.start + it.start + it.reading.length) },
            options(), query(), menu.highlighted)
    }
    private fun query(): CompositionQuery? {
        if (document.active == null) return null
        val reading = contextReading + document.reading()
        if (reading.length !in 1..120) return null
        val boundaries = if (contextReading.isEmpty()) menu.boundaries else listOf(contextReading.length) + menu.boundaries.map { it + contextReading.length }
        return CompositionQuery(reading, boundaries, revision)
    }
    private fun options() = document.active?.let { menu.options(document.reading(), it.raw) }.orEmpty()
    private fun finishOutside() {
        document.finish(preserveSelection = true)
        contextReading = ""
        menu.reset()
        revision++
    }
    private fun command(replacement: String): Boolean {
        if (document.active == null) return false
        return when (replacement) { " " -> { cycle(); true }; "\n" -> { accept(); true }; else -> false }
    }
    private fun replaceInput(change: InputEdit) {
        val input = punctuation[change.replacement] ?: change.replacement
        if (roman.matches(input)) { romanInput(change.copy(replacement = input)); return }
        if (input.isEmpty() && document.active != null) { deleteInput(change); return }
        document.literal(change.copy(replacement = literalPunctuation[input] ?: input))
    }
    private fun romanInput(change: InputEdit) {
        val active = document.active
        if (editsInside(active, change)) document.replaceInside(change)
        else document.append(change.replacement)
    }
    private fun editsInside(active: LiveComposition?, change: InputEdit) = active != null && (change.start != active.end || change.length != 0)

    private fun deleteInput(change: InputEdit) {
        val active = document.active ?: return
        if (change.start + change.length == active.end && change.length == 1) document.backspace()
        else document.replaceInside(change)
    }
    private fun commitSection(part: InputSection, choice: String) {
        val reading = document.reading()
        val end = part.start + part.reading.length
        val rest = reading.drop(end)
        document.commit(reading.take(part.start) + choice + rest)
        contextReading += reading.take(end)
        menu.reset()
        if (rest.isEmpty()) { contextReading = ""; return }
        val caret = document.selection.end
        document.select(InputSelection(caret - rest.length, caret))
        document.begin(rest)
        document.select(InputSelection(caret))
    }
    companion object {
        private val roman = Regex("^[a-z0-9'.,?!/-]+$", RegexOption.IGNORE_CASE)
        private val punctuation = mapOf("。" to ".", "、" to ",", "？" to "?", "！" to "!", "・" to "/", "ー" to "-")
        private val literalPunctuation = mapOf("." to "。", "," to "、", "?" to "？", "!" to "！", "/" to "・")
    }
}
