package com.cdcg.convolab.journal.input

/** UTF-16 offsets match Android text selection and the native engine contract. */
data class InputSelection(val start: Int, val end: Int = start) {
    val first get() = minOf(start, end)
    val last get() = maxOf(start, end)
}
data class InputEdit(val start: Int, val length: Int, val replacement: String)
data class LiveComposition(val start: Int, var end: Int, var raw: String, var visible: String)

class CompositionDocument(initial: String = "") {
    var text = initial
        private set
    var selection = InputSelection(initial.length)
        private set
    var active: LiveComposition? = null
        private set

    fun external(value: String) {
        text = value
        selection = InputSelection(selection.start.coerceIn(0, value.length), selection.end.coerceIn(0, value.length))
        active = null
    }
    fun select(value: InputSelection) { selection = InputSelection(value.start.coerceIn(0, text.length), value.end.coerceIn(0, text.length)) }
    fun contains(start: Int, end: Int): Boolean = active?.let { start >= it.start && end <= it.end } == true
    fun reading() = active?.let { Romaji.hiragana(it.raw, final = true) }.orEmpty()

    fun append(input: String) {
        val current = active ?: begin()
        current.raw += input
        replace(Romaji.hiragana(current.raw))
    }
    fun begin(raw: String = ""): LiveComposition {
        return LiveComposition(selection.first, selection.last, raw, text.substring(selection.first, selection.last)).also { active = it }
    }
    fun replace(value: String, caret: Int? = null) {
        val current = active ?: return
        if (text.substring(current.start, current.end) != current.visible) { active = null; return }
        replaceRange(InputEdit(current.start, current.end - current.start, value))
        current.end = current.start + value.length
        current.visible = value
        select(InputSelection(caret?.coerceIn(current.start, current.end) ?: current.end))
    }
    fun finish(preserveSelection: Boolean = false) {
        if (!preserveSelection) replace(reading())
        active = null
    }
    fun commit(value: String) { replace(value); active = null }
    fun literal(edit: InputEdit) {
        val atEnd = active?.end == selection.start && selection.start == selection.end
        finish(preserveSelection = !atEnd)
        replaceRange(edit)
    }
    fun replaceInside(edit: InputEdit) {
        val current = active ?: return
        val changed = CompositionEditing.replace(current, edit)
        current.raw = changed.raw
        replace(Romaji.hiragana(current.raw), changed.caret)
    }

    fun backspace() {
        val current = active?.takeIf { it.visible.isNotEmpty() } ?: return
        val goal = current.visible.dropLast(Character.charCount(current.visible.codePointBefore(current.visible.length)))
        var raw = current.raw
        while (raw.isNotEmpty() && Romaji.hiragana(raw) != goal) raw = raw.dropLast(1)
        current.raw = raw.ifEmpty { goal }
        replace(goal)
    }
    private fun replaceRange(edit: InputEdit) {
        require(edit.start in 0..text.length && edit.length in 0..(text.length - edit.start))
        text = text.replaceRange(edit.start, edit.start + edit.length, edit.replacement)
        selection = InputSelection(edit.start + edit.replacement.length)
    }
}
