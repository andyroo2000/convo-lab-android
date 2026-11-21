package com.cdcg.convolab.journal.text

data class ExplanationRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/** The same emphasis-only syntax as iOS. Links and HTML remain inert text. */
object ExplanationMarkup {
    private val emphasis = Regex("""(?<!\*)(\*{1,3})([^*]+)\1(?!\*)""")

    fun parse(value: String): List<ExplanationRun> {
        val runs = mutableListOf<ExplanationRun>()
        var cursor = 0
        emphasis.findAll(value).forEach { match ->
            runs += ExplanationRun(value.substring(cursor, match.range.first))
            val count = match.groupValues[1].length
            runs += ExplanationRun(match.groupValues[2], bold = count >= 2, italic = count != 2)
            cursor = match.range.last + 1
        }
        runs += ExplanationRun(value.substring(cursor))
        return runs.filter { it.text.isNotEmpty() }
    }
}
