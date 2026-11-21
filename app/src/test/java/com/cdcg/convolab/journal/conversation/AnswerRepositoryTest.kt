package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.*
import com.cdcg.convolab.network.ApiFailure
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerRepositoryTest {
    private val root = JournalEntry(JournalIds.newId(), null, "Root", "2026-11-20", 1, "created", "updated")
    private val origin = AnswerOrigin(JournalId(root.id), JournalId(JournalIds.newId()))

    @Test fun interruptedAnswerReusesTheFrozenBodyAndQuestionAfterReopening() = runTest {
        val storage = NotebookMemory()
        val api = AnswerGateway().apply { fail = true }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val first = repository.answerDraft(root, origin).editableDraft().edited("私の答え。")
        repository.edit(first)
        try { repository.save(first.key); fail("Expected interruption") } catch (_: IOException) { }
        val newer = first.edited("後で編集した文章。")
        repository.edit(newer)
        // A list refresh may reveal the acknowledged server child before the retry response.
        repository.accept(api.saved(first))
        val reopened = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        api.fail = false
        reopened.save(first.key)
        assertEquals(2, api.answers.size)
        assertEquals(api.answers[0], api.answers[1])
        assertEquals(origin, api.answers.last().second)
        assertEquals(newer.body, storage.records[first.id]?.draft?.body)
        assertEquals(1, storage.records[first.id]?.draft?.expectedVersion)
        assertNull(storage.records[first.id]?.pendingSave)
        assertEquals(first.body, storage.records[first.id]?.entry?.body)
    }

    @Test fun theSameQuestionRestoresItsDraftAndDifferentQuestionsStaySeparate() = runTest {
        val storage = NotebookMemory()
        val repository = JournalRepository(AnswerGateway(), storage, StandardTestDispatcher(testScheduler))
        val first = repository.answerDraft(root, origin).editableDraft().edited("続けたい答え。")
        repository.edit(first)
        assertEquals(first, repository.answerDraft(root, origin).draft)
        val next = repository.answerDraft(root, origin.copy(questionId = JournalId(JournalIds.newId())))
        assertNotEquals(first.id, next.id)
        assertEquals(2, storage.records.size)
    }

    @Test fun unsentAnswerBlocksRootDeletionButAnUnusedComposerDoesNot() = runTest {
        val storage = NotebookMemory().apply { records[root.id] = JournalRecord(entry = root) }
        val api = AnswerGateway()
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val answer = repository.answerDraft(root, origin).editableDraft().edited("残してください。")
        repository.edit(answer)
        try { repository.delete(JournalId(root.id)); fail("Must preserve the unsent answer") } catch (_: IllegalStateException) { }
        assertEquals(0, api.deletions)
        repository.edit(answer.edited(""))
        repository.delete(JournalId(root.id))
        assertEquals(1, api.deletions)
        assertTrue(storage.records.isEmpty())
    }

    @Test fun pendingRootDeletionCannotHandOutEvenAnExistingComposer() = runTest {
        val storage = NotebookMemory().apply { records[root.id] = JournalRecord(entry = root) }
        val repository = JournalRepository(AnswerGateway(), storage, StandardTestDispatcher(testScheduler))
        val draft = repository.answerDraft(root, origin)
        storage.records[root.id] = storage.records.getValue(root.id).copy(pendingDelete = true)
        try { repository.answerDraft(root, origin); fail("Deletion must block the composer") }
        catch (_: IllegalStateException) { }
        assertEquals(draft, storage.records[draft.id])
    }

    @Test fun answerConflictKeepsTheOriginalAndCanCreateAnIndependentDraft() = runTest {
        val storage = NotebookMemory()
        val api = AnswerGateway().apply { conflict = true }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val answer = repository.answerDraft(root, origin).editableDraft().edited("消さないで。")
        repository.edit(answer)
        try { repository.save(answer.key); fail("Expected conflict") } catch (_: ApiFailure) { }
        assertEquals(answer, storage.records[answer.id]?.draft)
        assertNull(storage.records[answer.id]?.pendingSave)
        val copy = repository.saveAsNew(answer.key)
        assertNull(copy.rootEntryId)
        assertNull(storage.records[copy.id]?.answerOrigin)
        assertEquals(answer.body, copy.body)
    }
}

internal class AnswerGateway : JournalGateway {
    val answers = mutableListOf<Pair<JournalDraft, AnswerOrigin>>()
    var fail = false
    var conflict = false
    var deletions = 0
    fun saved(draft: JournalDraft) = JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, 1, "created", "updated", draft.rootEntryId)
    override suspend fun archive(cursor: String?) = JournalPage(emptyList(), null)
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("Unexpected fetch")
    override suspend fun save(draft: JournalDraft) = saved(draft).copy(version = draft.expectedVersion + 1)
    override suspend fun answer(draft: JournalDraft, origin: AnswerOrigin): JournalEntry {
        answers += draft to origin
        if (fail) throw IOException("response lost")
        if (conflict) throw ApiFailure(409)
        return saved(draft)
    }
    override suspend fun delete(id: String, version: Int) { deletions++ }
}
