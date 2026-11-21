package com.cdcg.convolab.journal.input

import com.cdcg.convolab.typing.TestOutput
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

class CompositionContractTest {
    @Test fun packagedKanaTablesWorkWithoutTheNativeModel() {
        assertEquals("にほんご", Romaji.hiragana("nihongo"))
        assertEquals("こんにちは", Romaji.hiragana("konnichiha"))
        assertEquals("しんよう", Romaji.hiragana("shin'you"))
        val model = JournalComposition("昨日。")
        append(model, 'n'); append(model, 'n')
        assertEquals("昨日。ん", model.snapshot().text)
        assertNull(model.commit().composition)
    }
    @Test fun immediateKanaCompositionStaysWithinTheTypingBudget() {
        val warmStart = System.nanoTime()
        Romaji.hiragana("konnichiha")
        val preload = (System.nanoTime() - warmStart) / 1e6
        val samples = mutableListOf<Double>()
        val phrases = listOf("kyouhaiitenkidesu", "kinoueigawomimashita", "nihongonobennkyouwozuttotsuzukemasu")
        repeat(5) {
            for (phrase in phrases) {
                val model = JournalComposition()
                for (letter in phrase) {
                    val start = System.nanoTime()
                    append(model, letter)
                    model.snapshot()
                    samples += (System.nanoTime() - start) / 1e6
                }
                assertEquals(Romaji.hiragana(phrase, final = true), model.commit().text)
            }
        }
        val sorted = samples.sorted()
        val p95 = sorted[ceil(sorted.size * 0.95).toInt() - 1]
        TestOutput.file("composition-timing.json").writeText(JSONObject().put("samplesMs", JSONArray(samples))
            .put("p95Ms", p95).put("maxMs", sorted.last()).put("preloadMs", preload)
            .put("scope", "Public fixtures; synchronous kana state only, excludes rendering and inference").toString(2))
        assertTrue("Immediate composition p95 $p95 ms exceeded 16 ms", p95 < 16)
    }
    private fun append(model: JournalComposition, letter: Char) {
        val selection = model.snapshot().selection
        model.edit(InputEdit(selection.end, 0, letter.toString()), selection)
    }
}
