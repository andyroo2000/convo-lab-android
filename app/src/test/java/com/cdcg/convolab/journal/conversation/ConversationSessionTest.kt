package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSessionTest {
    @Test fun submittedAnswerGetsFeedbackAndMovesTheComposerToTheNewLeaf() = runTest {
        val fixture = ConversationFixture(this)
        fixture.start()
        val initial = requireNotNull(fixture.conversation.session.value?.state?.value?.composer).controller
        initial.edit("映画を見ました。")
        initial.submit().join()
        runCurrent()
        val session = requireNotNull(fixture.conversation.session.value)
        val reply = session.state.value.replies.single().controller
        assertEquals(initial, reply)
        assertEquals(WorkStatus.Ready, reply.workflow.state.value.snapshot?.analysis?.status)
        assertFalse(reply.state.value.editing)
        val next = requireNotNull(session.state.value.composer).controller
        assertNotEquals(reply.state.value.record.id, next.state.value.record.id)
        assertEquals(reply.state.value.record.id, next.state.value.record.answerOrigin?.parentId?.value)
        assertEquals(1, fixture.gateway.answers.size)
    }

    @Test fun childDraftSurvivesConversationRefreshAndRootNavigation() = runTest {
        val fixture = ConversationFixture(this)
        fixture.start()
        val first = requireNotNull(fixture.conversation.session.value?.state?.value?.composer).controller
        first.edit("大切な答え。")
        fixture.conversation.session.value!!.refresh().join()
        assertEquals("大切な答え。", fixture.conversation.session.value?.state?.value?.composer?.controller?.state?.value?.text)
        fixture.rootWorkflow.cancel()
        runCurrent()
        assertNull(fixture.conversation.session.value)
        val saved = fixture.memory.records.values.single { it.rootEntryId == fixture.root.id && it.draft?.body?.isNotBlank() == true }
        assertEquals("大切な答え。", saved.draft?.body)
        fixture.rootWorkflow.open(fixture.root)
        runCurrent()
        assertEquals("大切な答え。", fixture.conversation.session.value?.state?.value?.composer?.controller?.state?.value?.text)
    }

    @Test fun interruptedSubmissionRemainsFrozenAndCanRetryWithoutDuplicatingTheAnswer() = runTest {
        val fixture = ConversationFixture(this)
        fixture.start()
        val reply = fixture.conversation.session.value!!.state.value.composer!!.controller
        reply.edit("同じ答え。")
        fixture.gateway.failResponse = true
        reply.submit().join()
        assertNotNull(reply.state.value.record.pendingSave)
        reply.edit("Must not replace the frozen request")
        assertEquals("同じ答え。", reply.state.value.text)
        fixture.gateway.failResponse = false
        reply.submit().join()
        runCurrent()
        assertEquals(2, fixture.gateway.answers.size)
        assertEquals(fixture.gateway.answers.first(), fixture.gateway.answers.last())
        assertEquals(1, fixture.gateway.nodes.size)
    }

    @Test fun submitQueuesItsRefreshBehindAnOlderInFlightSnapshot() = runTest {
        val fixture = ConversationFixture(this)
        fixture.start()
        val session = fixture.conversation.session.value!!
        val reply = session.state.value.composer!!.controller
        reply.edit("新しい答え。")
        val gate = CompletableDeferred<Unit>()
        fixture.gateway.nextLoadGate = gate
        val oldRefresh = session.refresh()
        runCurrent()
        val submission = reply.submit()
        runCurrent()
        assertFalse(submission.isCompleted)
        gate.complete(Unit)
        oldRefresh.join()
        submission.join()
        runCurrent()
        assertEquals(reply, session.state.value.replies.single().controller)
        assertNotEquals(reply, session.state.value.composer?.controller)
    }

    @Test fun discardedComposerStopsAcceptingWritesFromItsOldController() = runTest {
        val fixture = ConversationFixture(this)
        fixture.start()
        val old = fixture.conversation.session.value!!.state.value.composer!!.controller
        old.edit("捨てる前の答え。")
        old.discardUnsent().join()
        runCurrent()
        old.edit("Must not recreate the discarded file")
        runCurrent()
        assertNull(fixture.memory.records[old.state.value.record.id])
        assertNotEquals(old, fixture.conversation.session.value?.state?.value?.composer?.controller)
    }

    @Test fun diskFailureKeepsAnswerVisibleAndBlocksSubmissionUntilRetry() = runTest {
        val fixture = ConversationFixture(this)
        fixture.start()
        val reply = fixture.conversation.session.value!!.state.value.composer!!.controller
        fixture.memory.failWrites = true
        reply.edit("まだ送っていない答え。")
        reply.submit().join()
        assertEquals(0, fixture.gateway.answers.size)
        assertEquals("まだ送っていない答え。", reply.state.value.text)
        assertFalse(reply.state.value.saved)
        fixture.memory.failWrites = false
        reply.retryLocal().join()
        assertTrue(reply.state.value.saved)
        assertEquals(reply.state.value.text, fixture.memory.records[reply.state.value.record.id]?.draft?.body)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ConversationFixture(private val test: TestScope) {
    val root = JournalEntry(JournalIds.newId(), null, "今日は晴れ。", "2026-11-20", 1, "created", "updated")
    val memory = NotebookMemory().apply { records[root.id] = JournalRecord(entry = root) }
    val feedback = ConversationFeedback()
    val tutor = ConversationTutor()
    val gateway = ConversationJournal(tutor, feedback)
    private val dispatcher = StandardTestDispatcher(test.testScheduler)
    private val repository = JournalRepository(gateway, memory, dispatcher)
    private val storage = ConversationWorkMemory()
    private fun workflow(scope: CoroutineScope) = WorkflowController(WorkflowAccess(feedback, storage, dispatcher), scope)
    val rootWorkflow = workflow(test.backgroundScope)
    val conversation = ConversationController(ConversationAccess(gateway, repository, ::workflow, TutorController(tutor, test.backgroundScope)))
    suspend fun start() {
        test.backgroundScope.launch { conversation.follow(rootWorkflow) }
        rootWorkflow.open(root)
        test.runCurrent()
    }
}

private class ConversationWorkMemory : WorkflowStorage {
    private val records = mutableMapOf<JournalId, WorkflowSnapshot>()
    override fun read(id: JournalId) = records[id]
    override fun archive(id: JournalId) { records.remove(id) }
    override fun write(snapshot: WorkflowSnapshot) { records[snapshot.entryId] = snapshot }
}

private class ConversationFeedback : FeedbackGateway {
    private val analyses = mutableMapOf<String, Analysis>()
    fun result(entry: JournalEntry) = analyses.getOrPut(entry.id) {
        Analysis(JournalIds.newId(), entry.version, WorkStatus.Ready, null, null, CorrectedDocument(entry.body, entry.body), 0, null, "updated", "{}")
    }
    override suspend fun startClarification(entry: JournalEntry) = Clarification(JournalIds.newId(), entry.version, 1, WorkStatus.Resolved, null, null, "updated", "{}")
    override suspend fun clarification(entry: JournalEntry) = startClarification(entry)
    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification = error("Unexpected clarification")
    override suspend fun submit(entry: JournalEntry, clarificationId: String) = result(entry)
    override suspend fun analysis(entry: JournalEntry) = AnalysisResponse(entry, result(entry))
    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit) = result(entry)
}

private class ConversationTutor : TutorGateway {
    private val tutor = Tutor("hana", "Hana", "F", "hana-v1", null)
    val questions = linkedMapOf<QuestionSource, TutorQuestion>()
    override suspend fun prepare(source: QuestionSource) = questions.getOrPut(source) {
        TutorQuestion(JournalIds.newId(), "何をしましたか。", "何[なに]をしましたか。", "What did you do?", tutor, "ready", null)
    }
    override suspend fun preferences() = TutorPreferences(tutor.id, listOf(tutor))
    override suspend fun choose(tutor: Tutor) = preferences()
    override suspend fun question(entryId: JournalId, questionId: JournalId) = questions.values.first { it.id == questionId.value }
    override suspend fun retryAudio(entryId: JournalId, questionId: JournalId) = question(entryId, questionId)
}

private class ConversationJournal(private val tutor: ConversationTutor, private val feedback: ConversationFeedback) : JournalGateway, ConversationGateway {
    val nodes = mutableListOf<ConversationNode>()
    val answers = mutableListOf<Pair<JournalDraft, AnswerOrigin>>()
    var failResponse = false
    var nextLoadGate: CompletableDeferred<Unit>? = null
    override suspend fun load(root: JournalId): List<ConversationNode> {
        val snapshot = nodes.toList()
        val gate = nextLoadGate
        nextLoadGate = null
        gate?.await()
        return snapshot
    }
    override suspend fun answer(draft: JournalDraft, origin: AnswerOrigin): JournalEntry {
        answers += draft to origin
        val entry = nodes.firstOrNull { it.entry.id == draft.id }?.entry ?: save(draft)
        if (nodes.none { it.entry.id == entry.id }) nodes += ConversationNode(entry, feedback.result(entry), tutor.question(origin.parentId, origin.questionId), origin.parentId.value)
        if (failResponse) throw IOException("Response lost")
        return entry
    }
    override suspend fun save(draft: JournalDraft) = JournalEntry(draft.id, null, draft.body, draft.entryDate, draft.expectedVersion + 1, "created", "updated", draft.rootEntryId)
    override suspend fun archive(cursor: String?) = JournalPage(emptyList(), null)
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String) = nodes.first { it.entry.id == id }.entry
    override suspend fun delete(id: String, version: Int) = Unit
}
