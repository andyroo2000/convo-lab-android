package com.cdcg.convolab.journal.input

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativeJournalTypingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun learningSurvivesRestartIsAccountScopedAndCanBeReset() = runBlocking {
        val firstId = Long.MAX_VALUE - 1
        val secondId = Long.MAX_VALUE - 2
        val owners = mutableListOf<NativeJournalTyping>()
        fun owner(id: Long) = NativeJournalTyping(context, id).also { owners += it }
        try {
            val first = owner(firstId)
            first.prepare()
            val query = CompositionQuery("はし", emptyList(), 1)
            val baseline = first.candidates(query).first().text
            val choices = first.candidates(query, expanded = true)
            val alternate = requireNotNull(choices.firstOrNull { it.text != baseline && it.text in listOf("橋", "箸", "端") })
            first.learn(ConfirmedInput("はし", alternate.text, 0, 2))
            assertEquals(alternate.text, first.candidates(query).first().text)
            first.close()
            val restarted = owner(firstId)
            restarted.prepare()
            assertEquals(alternate.text, restarted.candidates(query).first().text)
            val other = owner(secondId)
            other.prepare()
            assertEquals(baseline, other.candidates(query).first().text)
            restarted.reset()
            assertEquals(baseline, restarted.candidates(query).first().text)
            assertFalse(File(context.noBackupFilesDir, "journal-typing/$firstId/confirmed.json").exists())
        } finally {
            owners.forEach { it.close() }
            for (id in listOf(firstId, secondId)) File(context.noBackupFilesDir, "journal-typing/$id").deleteRecursively()
        }
    }
}
