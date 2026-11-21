package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.JournalIds
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TutorControllerTest {
    private val source = QuestionSource(JournalId(JournalIds.newId()), JournalId(JournalIds.newId()))

    @Test fun changingPreferencePreservesExistingQuestionAttribution() = runTest {
        val api = TutorFixture()
        val controller = TutorController(api, backgroundScope)
        controller.open(source)
        runCurrent()
        controller.showPicker()
        runCurrent()
        controller.choose(api.ren)
        runCurrent()
        assertEquals("ren", controller.state.value.preferences?.selectedId)
        assertEquals("hana", controller.state.value.question?.tutor?.id)
        assertEquals(1, api.prepared)
        assertFalse(controller.state.value.choosing)
        assertTrue(controller.state.value.notice!!.contains("future"))
    }

    @Test fun failedPickerCanRetryWithoutLosingTheQuestion() = runTest {
        val api = TutorFixture().apply { failCatalog = true }
        val controller = TutorController(api, backgroundScope)
        controller.open(source)
        runCurrent()
        controller.showPicker()
        runCurrent()
        assertTrue(controller.state.value.choosing)
        assertNotNull(controller.state.value.error)
        assertNotNull(controller.state.value.question)
        api.failCatalog = false
        controller.retry()
        runCurrent()
        assertEquals(2, controller.state.value.preferences?.tutors?.size)
        assertNull(controller.state.value.error)
    }

    @Test fun canceledOldQuestionCannotReplaceTheNewEntry() = runTest {
        val api = TutorFixture()
        val late = CompletableDeferred<TutorQuestion>()
        api.prepareResult = { withContext(NonCancellable) { late.await() } }
        val controller = TutorController(api, backgroundScope)
        controller.open(source)
        runCurrent()
        val next = source.copy(entryId = JournalId(JournalIds.newId()))
        val newQuestion = api.sample.copy(id = JournalIds.newId(), text = "New question")
        api.prepareResult = { newQuestion }
        controller.open(next)
        runCurrent()
        late.complete(api.sample)
        runCurrent()
        assertEquals(next, controller.state.value.source)
        assertEquals(newQuestion, controller.state.value.question)
        assertFalse(controller.state.value.busy)
    }

    @Test fun anUnassignedQuestionIsPreparedAgainAfterChoosing() = runTest {
        val api = TutorFixture().apply { prepareResult = { sample.copy(tutor = null) } }
        val controller = TutorController(api, backgroundScope)
        controller.open(source)
        runCurrent()
        assertTrue(controller.state.value.choosing)
        api.prepareResult = { api.sample.copy(tutor = api.ren) }
        controller.choose(api.ren)
        runCurrent()
        assertEquals(api.ren, controller.state.value.question?.tutor)
        assertEquals(2, api.prepared)
    }
}

private class TutorFixture : TutorGateway {
    val hana = Tutor("hana", "Hana", "F", "hana-v1", null)
    val ren = Tutor("ren", "Ren", "M", "ren-v1", null)
    val sample = TutorQuestion(JournalIds.newId(), "何をしましたか。", "何[なに]をしましたか。", "What did you do?", hana, "ready", null)
    var prepared = 0
    var failCatalog = false
    var prepareResult: suspend () -> TutorQuestion = { sample }
    override suspend fun prepare(source: QuestionSource): TutorQuestion { prepared++; return prepareResult() }
    override suspend fun preferences(): TutorPreferences {
        if (failCatalog) throw java.io.IOException("offline")
        return TutorPreferences("hana", listOf(hana, ren))
    }
    override suspend fun choose(tutor: Tutor) = TutorPreferences(tutor.id, listOf(hana, ren))
    override suspend fun question(entryId: JournalId, questionId: JournalId) = sample
    override suspend fun retryAudio(entryId: JournalId, questionId: JournalId) = sample
}
