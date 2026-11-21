package com.cdcg.convolab.journal.feedback

import org.junit.Assert.*
import org.junit.Test

class JournalVersionCopyTest {
    @Test fun draftCopiesOriginalAndRevisedFeedbackInOrder() {
        val analysis = analysis(feedback("Original draft."), feedback("Revised draft."))
        assertEquals("Original draft.\n\nRevised draft.", analysis.copyText(false))
        assertEquals(2, analysis.draftFeedback.size)
    }
    @Test fun finalCopiesTheCorrectedDocumentAndNeverDraftAnnotations() {
        val analysis = analysis(feedback("Original draft."), feedback("Revised draft."))
        assertEquals("Final corrected text.", analysis.copyText(true))
    }
    @Test fun draftWithNoReplyDoesNotAddAnEmptyParagraph() {
        assertEquals("Original draft.", analysis(feedback("Original draft."), null).copyText(false))
    }
    private fun analysis(original: Feedback, reply: Feedback?) = Analysis("fixture", 1, WorkStatus.Ready, original, reply,
        CorrectedDocument("Final corrected text.", "Final corrected text."), 1, null, "updated", "{}")
    private fun feedback(text: String) = Feedback("Title", "Corrected", "Corrected", "", "", "",
        listOf(FeedbackSegment(text, text, SegmentKind.Plain, null, null, null, null, null, null)), null, null, null)
}
