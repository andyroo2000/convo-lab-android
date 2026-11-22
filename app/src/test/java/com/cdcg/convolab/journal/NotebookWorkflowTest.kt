package com.cdcg.convolab.journal

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.*
import com.cdcg.convolab.journal.feedback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookWorkflowTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun stop() { store.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun editingHidesOldFeedbackAndOnlyAcknowledgedVersionIsSubmitted() = runTest {
        val session = Session("fixture-token", Account(1, "Sample", "sample@example.invalid"))
        val sessions = SessionController(FakeAuth().apply { loginResult = { session } }, MemoryStorage(), dispatcher)
        val draft = JournalDraft.blank().edited("最初の文章。")
        val entry = JournalEntry(draft.id, null, draft.body, draft.entryDate, 1, "created", "updated")
        val record = JournalRecord(entry = entry, draft = JournalDraft.from(entry))
        val storage = NotebookMemory().apply { records[entry.id] = record }
        val api = BoundFeedbackGateway()
        val workflows = BoundWorkflowStorage()
        val factory = viewModelFactory { initializer {
            NotebookViewModel(sessions, workflow = { _, scope, repository -> WorkflowController(WorkflowAccess(api, workflows, dispatcher, repository::acceptFeedbackMetadata), scope) }) {
                JournalRepository(NotebookGateway(), storage, dispatcher)
            }
        } }
        val model = ViewModelProvider(owner, factory)["active-journal", NotebookViewModel::class.java]
        sessions.login("sample@example.invalid", "fixture-password")
        runCurrent()
        val binding = requireNotNull(model.binding.value)
        val feedback = requireNotNull(binding.workflow)
        assertEquals(1, feedback.state.value.snapshot?.sourceVersion)
        binding.controller.edit("次の文章。")
        runCurrent()
        assertNull(feedback.state.value.snapshot)
        binding.controller.save { feedback.submit(it) }.join()
        runCurrent()
        assertEquals(1, api.submissions)
        assertEquals(2, feedback.state.value.snapshot?.analysis?.sourceVersion)
        assertEquals("次の文章。", feedback.state.value.entry?.body)
        sessions.signOut()
        runCurrent()
        assertNull(model.binding.value)
    }
}

private class BoundWorkflowStorage : WorkflowStorage {
    private var value: WorkflowSnapshot? = null
    override fun read(id: JournalId) = value?.takeIf { it.entryId == id }
    override fun archive(id: JournalId) { value = null }
    override fun write(snapshot: WorkflowSnapshot) { value = snapshot }
}

private class BoundFeedbackGateway : FeedbackGateway {
    var submissions = 0
    private fun result(entry: JournalEntry) = Analysis(JournalIds.newId(), entry.version, WorkStatus.Ready,
        null, null, CorrectedDocument(entry.body, entry.body), 0, null, "updated", "{}")
    override suspend fun startClarification(entry: JournalEntry) = Clarification(JournalIds.newId(), entry.version, 1,
        WorkStatus.Resolved, null, null, "updated", "{}")
    override suspend fun clarification(entry: JournalEntry) = startClarification(entry)
    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification = error("Unexpected answer")
    override suspend fun submit(entry: JournalEntry, clarificationId: String): Analysis { submissions++; return result(entry) }
    override suspend fun analysis(entry: JournalEntry) = AnalysisResponse(entry, result(entry))
    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis = error("Unexpected edit")
}
