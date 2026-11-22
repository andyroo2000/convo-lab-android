package com.cdcg.convolab.journal.text

import com.cdcg.convolab.journal.feedback.FeedbackSegment
import com.cdcg.convolab.journal.feedback.SegmentKind
import org.junit.Assert.*
import org.junit.Test

class CorrectionExplanationContentTest {
    @Test fun japaneseKeepsBoldSerifWhileLatinKeepsItsOwnEmphasis() {
        val runs = CorrectionExplanationContent.runs(segment("***見ました*** is the ***polite*** past form。"))
        assertEquals(CorrectionExplanationRun("見ました", true, bold = true, replacement = true), runs.first())
        assertEquals(CorrectionExplanationRun("polite", false, bold = true, italic = true), runs[2])
        assertEquals(CorrectionExplanationRun("。", true, bold = true), runs.last())
    }

    @Test fun repeatsOfHeaderReadingsAreRemovedButNewReadingsSurvive() {
        val value = segment("映画[えいが]を見[み]ました。友達[ともだち]と。")
        assertEquals("映画を見ました。友達[ともだち]と。", CorrectionExplanationContent.text(value))
        assertTrue(CorrectionExplanationContent.runs(value).any { it.text.contains("友達[ともだち]") && it.japanese })
        assertEquals(value.explanationReading, CorrectionExplanationContent.text(value.copy(replacement = null)))
    }

    @Test fun sentenceGapsPreserveParagraphsAndOrdinarySpaces() {
        val value = segment("Use **見ました**. Next sentence. **映画[えいが]** means movie.\n\nKeep ordinary spaces.")
        assertEquals("Use **見ました**.\u2002Next sentence.\u2002**映画** means movie.\n\nKeep ordinary spaces.", CorrectionExplanationContent.text(value))
    }

    @Test fun rubyAndUnsupportedMarkupRemainLiteralAndComplete() {
        val value = segment("**友達[ともだち]** <b>text</b> [link](https://example.invalid) ****literal****")
        assertEquals("友達[ともだち] <b>text</b> [link](https://example.invalid) ****literal****",
            CorrectionExplanationContent.runs(value).joinToString("") { it.text })
    }

    private fun segment(explanation: String) = FeedbackSegment("映画を見た。", "映画[えいが]を見[み]た。", SegmentKind.Optional,
        null, null, null, explanation, "見ました", "映画[えいが]を見[み]ました。")
}
