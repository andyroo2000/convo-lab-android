package com.cdcg.convolab.journal.input

/** Keep original romaji only where an edit aligns with complete kana tokens. */
internal object CompositionEditing {
    data class Replacement(val raw: String, val caret: Int)
    fun replace(current: LiveComposition, edit: InputEdit): Replacement {
        val offset = edit.start - current.start
        val prefix = rawPrefix(current.raw, current.visible.take(offset))
        val suffix = rawSuffix(current.raw, current.visible.drop(offset + edit.length))
        return Replacement(prefix + edit.replacement + suffix,
            current.start + Romaji.hiragana(prefix + edit.replacement).length)
    }
    private fun rawPrefix(raw: String, visible: String): String {
        for (end in raw.length downTo 0) if (Romaji.hiragana(raw.take(end)) == visible) return raw.take(end)
        return visible
    }
    private fun rawSuffix(raw: String, visible: String): String {
        for (start in 0..raw.length) if (Romaji.hiragana(raw.drop(start)) == visible) return raw.drop(start)
        return visible
    }
}
