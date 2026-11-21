package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalDraft
import com.cdcg.convolab.journal.JournalEntry
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.JournalIds
import com.cdcg.convolab.network.ApiFailure
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowSessionTest {
    private val draft = JournalDraft.blank().edited("今日は晴れです。")
    private val entry = JournalEntry(draft.id, null, draft.body, draft.entryDate, 1, "created", "updated")
    private val clarificationId = JournalIds.newId()
    private val questionId = JournalIds.newId()
    private val analysisId = JournalIds.newId()
    private fun clarification(status: WorkStatus) = Clarification(clarificationId, 1, 1, status,
        if (status == WorkStatus.Waiting) ClarificationQuestion(questionId, "What did you mean?", listOf("A", "B")) else null,
        null, "updated", "{}")
    private fun analysis(text: String = "今日は晴れでした。") = Analysis(analysisId, 1, WorkStatus.Ready,
        null, null, CorrectedDocument(text, text), 1, null, "updated", "{}")

    @Test fun lostAnswerResponseRetriesTheExactQuestionAndTextAfterReopen() = runTest {
        val storage = MemoryWorkflow()
        val api = FakeFeedback().apply {
            startResult = { clarification(WorkStatus.Waiting) }
            answerResult = { throw IOException("response lost") }
            submitResult = { analysis() }
        }
        val access = WorkflowAccess(api, storage, StandardTestDispatcher(testScheduler))
        val first = WorkflowSession(entry, access) {}
        first.begin()
        try { first.answer("  A  "); fail("Expected lost response") } catch (_: IOException) { }
        val pending = storage.record!!.pendingAnswer!!
        assertEquals(questionId, pending.questionId)
        assertEquals("A", pending.text)
        api.answerResult = { clarification(WorkStatus.Resolved) }
        val reopened = WorkflowSession(entry, access) {}
        reopened.load()
        reopened.resume()
        assertEquals(listOf(pending, pending), api.answers)
        assertNull(storage.record?.pendingAnswer)
        assertFalse(storage.record!!.submissionRequested)
        assertEquals(WorkStatus.Ready, storage.record?.analysis?.status)
    }

    @Test fun cancellationDuringAnswerKeepsItsDurablePayload() = runTest {
        val response = CompletableDeferred<Clarification>()
        val storage = MemoryWorkflow()
        val api = FakeFeedback().apply {
            startResult = { clarification(WorkStatus.Waiting) }
            answerResult = { response.await() }
        }
        val session = WorkflowSession(entry, WorkflowAccess(api, storage, StandardTestDispatcher(testScheduler))) {}
        session.begin()
        val job = launch { session.answer("A") }
        runCurrent()
        job.cancelAndJoin()
        assertEquals("A", storage.record?.pendingAnswer?.text)
        assertTrue(storage.record!!.submissionRequested)
    }

    @Test fun staleClarificationVersionIsRejectedBeforeReplacingTheCheckpoint() = runTest {
        val storage = MemoryWorkflow()
        val api = FakeFeedback().apply { startResult = { clarification(WorkStatus.Waiting).copy(sourceVersion = 2) } }
        val session = WorkflowSession(entry, WorkflowAccess(api, storage, StandardTestDispatcher(testScheduler))) {}
        try { session.begin(); fail("Expected stale response rejection") }
        catch (failure: ApiFailure) { assertEquals(409, failure.status) }
        assertEquals(1, storage.record?.sourceVersion)
        assertNull(storage.record?.clarification)
    }

    @Test fun finalEditLostResponseReconcilesMatchingServerTextWithoutRebasing() = runTest {
        val storage = MemoryWorkflow()
        val edit = FinalEdit(analysisId, 0, "自分で直した文章。")
        val remote = analysis(edit.text)
        val api = FakeFeedback().apply {
            editResult = { throw ApiFailure(409) }
            analysisResult = { AnalysisResponse(entry, remote) }
        }
        val session = WorkflowSession(entry, WorkflowAccess(api, storage, StandardTestDispatcher(testScheduler))) {}
        session.editFinal(edit)
        assertEquals(edit, api.edits.single())
        assertNull(storage.record?.pendingFinalEdit)
        assertEquals(remote, storage.record?.analysis)
    }

    @Test fun genuineFinalEditConflictPreservesTheOpeningRevisionAndText() = runTest {
        val storage = MemoryWorkflow()
        val edit = FinalEdit(analysisId, 0, "こちらの文章。")
        val api = FakeFeedback().apply {
            editResult = { throw ApiFailure(409) }
            analysisResult = { AnalysisResponse(entry, analysis("別の端末の文章。")) }
        }
        val session = WorkflowSession(entry, WorkflowAccess(api, storage, StandardTestDispatcher(testScheduler))) {}
        try { session.editFinal(edit); fail("Expected conflict") }
        catch (failure: ApiFailure) { assertEquals(409, failure.status) }
        assertEquals(edit, storage.record?.pendingFinalEdit)
    }

    @Test fun retryingAReadFailureDoesNotStartAChargedSubmission() = runTest {
        val api = FakeFeedback().apply { analysisResult = { throw IOException("offline") } }
        val controller = WorkflowController(WorkflowAccess(api, MemoryWorkflow(), StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.open(entry)
        runCurrent()
        assertNotNull(controller.state.value.error)
        controller.retry()
        runCurrent()
        assertEquals(0, api.starts)
        assertEquals(2, api.analysisReads)
    }

    @Test fun cancelledOldEntryResponseCannotPublishIntoTheNewEntry() = runTest {
        val response = CompletableDeferred<AnalysisResponse>()
        val next = entry.copy(id = JournalIds.newId(), body = "次の文章。")
        val api = FakeFeedback().apply {
            analysisResult = { requested ->
                if (requested.id == entry.id) withContext(NonCancellable) { response.await() }
                else AnalysisResponse(next, analysis("次のフィードバック。"))
            }
        }
        val controller = WorkflowController(WorkflowAccess(api, MemoryWorkflow(), StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.open(entry)
        runCurrent()
        controller.open(next)
        response.complete(AnalysisResponse(entry, analysis("古いフィードバック。")))
        runCurrent()
        assertEquals(next.id, controller.state.value.entry?.id)
        assertEquals("次のフィードバック。", controller.state.value.snapshot?.analysis?.finalText)
    }

    @Test fun pollingStopsAfterThreeTransportFailures() = runTest {
        var calls = 0
        try { JournalPolling.read { calls++; throw IOException("offline") }; fail("Expected bounded failure") }
        catch (_: IOException) { assertEquals(3, calls) }
    }
}

private class MemoryWorkflow : WorkflowStorage {
    var record: WorkflowSnapshot? = null
    override fun read(id: JournalId) = record?.takeIf { it.entryId == id }
    override fun write(snapshot: WorkflowSnapshot) { record = snapshot }
}

private class FakeFeedback : FeedbackGateway {
    var starts = 0
    var analysisReads = 0
    val answers = mutableListOf<ClarificationAnswer>()
    val edits = mutableListOf<FinalEdit>()
    var startResult: suspend () -> Clarification = { error("Unexpected start") }
    var answerResult: suspend () -> Clarification = { error("Unexpected answer") }
    var submitResult: suspend () -> Analysis = { error("Unexpected submit") }
    var analysisResult: suspend (JournalEntry) -> AnalysisResponse = { error("Unexpected analysis read") }
    var editResult: suspend () -> Analysis = { error("Unexpected final edit") }
    override suspend fun startClarification(entry: JournalEntry): Clarification { starts++; return startResult() }
    override suspend fun clarification(entry: JournalEntry) = startResult()
    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification { answers += answer; return answerResult() }
    override suspend fun submit(entry: JournalEntry, clarificationId: String) = submitResult()
    override suspend fun analysis(entry: JournalEntry): AnalysisResponse { analysisReads++; return analysisResult(entry) }
    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis { edits += edit; return editResult() }
}
