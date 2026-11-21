package com.cdcg.convolab.journal.input

import org.junit.Assert.*
import org.junit.Test

class JournalCompositionTest {
    @Test fun suggestionsNeverRewriteTextAndOldQueriesCannotReappearAfterRetyping() {
        val model = JournalComposition()
        type(model, "nihongo")
        val first = model.snapshot()
        val candidate = JapaneseCandidate("日本語", "にほんご", listOf(CandidatePart("日本語", "にほんご")))
        val result = model.receive(requireNotNull(first.query), listOf(candidate))
        assertEquals(first.text, result.text)
        assertEquals(first.selection, result.selection)
        model.external("")
        type(model, "nihongo")
        assertNotEquals(first.query, model.snapshot().query)
        assertFalse(model.receive(requireNotNull(first.query), listOf(candidate)).choices.contains("日本語"))
        model.receive(requireNotNull(model.snapshot().query), listOf(candidate))
        assertEquals("日本語", model.choose(0).text)
        assertEquals(listOf(ConfirmedInput("にほんご", "日本語", 0, 4)), model.takeConfirmed())
        assertTrue(model.takeConfirmed().isEmpty())
    }
    @Test fun replacingAnEmojiKeepsCompleteUtf16CodePoints() {
        assertEquals(InputEdit(1, 2, "😊"), InputChange.between("A🙂B", "A😊B"))
        val model = JournalComposition("A🙂B")
        assertEquals("A😊B", model.reconcile("A😊B", InputSelection(3)).text)
        assertNull(model.snapshot().composition)
    }
    @Test fun longWritingCommitsKanaInBoundedChunksWithoutLosingText() {
        val model = JournalComposition()
        type(model, "a".repeat(300))
        assertEquals("あ".repeat(300), model.snapshot().text)
        assertTrue(requireNotNull(model.snapshot().query).reading.length <= 120)
        assertEquals("あ".repeat(300), model.commit().text)
    }
    @Test fun slicingNeverInventsAKanjiReadingAtAnUnalignedBoundary() {
        val candidate = JapaneseCandidate("日本語", "にほんご", units = listOf(CandidatePart("日本語", "にほんご")))
        assertNull(CandidateAlignment.text(candidate, InputSelection(0, 1)))
        assertEquals("日本語", CandidateAlignment.text(candidate, InputSelection(0, 4)))
        val verb = JapaneseCandidate("食べました", "たべました", units = listOf(CandidatePart("食べました", "たべました")))
        assertEquals("食べ", CandidateAlignment.text(verb, InputSelection(0, 2)))
        assertEquals("ました", CandidateAlignment.text(verb, InputSelection(2, 5)))
    }
    private fun type(model: JournalComposition, text: String) {
        for (letter in text) {
            val selection = model.snapshot().selection
            model.edit(InputEdit(selection.end, 0, letter.toString()), selection)
        }
    }
}
