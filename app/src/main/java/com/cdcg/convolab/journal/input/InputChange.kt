package com.cdcg.convolab.journal.input

object InputChange {
    fun between(before: String, after: String): InputEdit {
        var start = prefix(before, after)
        val suffixLength = suffix(before, after, start)
        var end = before.length - suffixLength
        var nextEnd = after.length - suffixLength
        if (splitSurrogate(before, start) || splitSurrogate(after, start)) start--
        if (splitSurrogate(before, end) || splitSurrogate(after, nextEnd)) { end++; nextEnd++ }
        return InputEdit(start, end - start, after.substring(start, nextEnd))
    }
    private fun prefix(before: String, after: String): Int {
        var count = 0
        while (count < minOf(before.length, after.length) && before[count] == after[count]) count++
        return count
    }
    private fun suffix(before: String, after: String, start: Int): Int {
        var count = 0
        while (count < minOf(before.length, after.length) - start && before[before.length - count - 1] == after[after.length - count - 1]) count++
        return count
    }
    private fun splitSurrogate(text: String, position: Int) = position > 0 && position < text.length &&
        text[position - 1].isHighSurrogate() && text[position].isLowSurrogate()
}
