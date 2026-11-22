package com.cdcg.convolab.journal.text

import com.cdcg.convolab.journal.feedback.FeedbackSegment

internal data class CorrectionExplanationRun(val text: String, val japanese: Boolean, val bold: Boolean = false, val italic: Boolean = false, val replacement: Boolean = false)

/** Keeps the iOS explanation's script sizing, emphasis, and redundant-reading rules together. */
internal object CorrectionExplanationContent {
    private const val words = "[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}々〆ー]"
    private val japanese = Regex("(?:$words+(?:\\[[^\\]]+\\])?)+|[\\u3000-\\u303F\\uFF01-\\uFF0F\\uFF1A-\\uFF20\\uFF3B-\\uFF40\\uFF5B-\\uFF60]+")
    private val sentenceGaps = listOf(
        Regex("(($words|\\[[ぁ-ゖァ-ヺー]+\\])\\*{0,3}[.!?][\"'’”)]?\\*{0,3})[ \\t]+(?=\\S)"),
        Regex("([.!?][\"'’”)]?\\*{0,3})[ \\t]+(?=\\*{0,3}[\"'‘“(「『]?$words)"),
    )

    fun runs(segment: FeedbackSegment): List<CorrectionExplanationRun> = ExplanationMarkup.parse(text(segment)).flatMap { run ->
        buildList {
            var cursor = 0
            japanese.findAll(run.text).forEach { match ->
                if (cursor < match.range.first) add(CorrectionExplanationRun(run.text.substring(cursor, match.range.first), false, run.bold, run.italic))
                val plain = RubyDocument.parse(match.value).joinToString("") { it.text }
                add(CorrectionExplanationRun(match.value, true, bold = true, replacement = plain == segment.replacement))
                cursor = match.range.last + 1
            }
            if (cursor < run.text.length) add(CorrectionExplanationRun(run.text.substring(cursor), false, run.bold, run.italic))
        }
    }

    fun text(segment: FeedbackSegment): String {
        var result = segment.explanationReading ?: segment.explanation.orEmpty()
        sentenceGaps.forEach { result = it.replace(result) { match -> match.groupValues[1] + "\u2002" } }
        if (segment.replacement == null) return result
        RubyDocument.parse(segment.reading + " " + segment.replacementReading.orEmpty()).filter { it.reading != null }.forEach {
            result = result.replace("${it.text}[${it.reading}]", it.text)
        }
        return result
    }
}
