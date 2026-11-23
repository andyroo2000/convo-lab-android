package com.cdcg.convolab.journal

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.*
import com.cdcg.convolab.network.ApiFailure
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

    private val draft = JournalDraft.blank().edited("最初の文章。")
    private val entry = JournalEntry(draft.id, null, draft.body, draft.entryDate, 1, "created", "updated")
    private val record = JournalRecord(entry = entry, draft = JournalDraft.from(entry))
    private val storage = NotebookMemory().apply { records[entry.id] = record }
    private val api = BoundFeedbackGateway()
    private val journalApi = NotebookGateway()
    private val workflows = BoundWorkflowStorage()
    private lateinit var sessions: SessionController
    private lateinit var model: NotebookViewModel

    @Test fun recoveringMissingEntryTearsDownFeedbackAndOpensTheCopiedDraft() = runTest {
        val binding = openNotebook(missing = true)
        val feedback = requireNotNull(binding.workflow)
        assertTrue(feedback.state.value.missingEntry)
        binding.controller.recoverCopy().join()
        runCurrent()
        assertNull(feedback.state.value.entry)
        assertFalse(feedback.state.value.missingEntry)
        assertNotEquals(entry.id, binding.controller.state.value.draft.id)
        assertEquals(entry.body, binding.controller.state.value.draft.body)
        assertEquals(record, storage.records[entry.id])
        assertEquals(0, api.submissions)
    }

    @Test fun editingHidesOldFeedbackAndOnlyAcknowledgedVersionIsSubmitted() = runTest {
        val binding = openNotebook()
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

    @Test fun automaticSavesDoNotReloadFeedbackForNewOrEditedEntries() = runTest {
        val binding = openNotebook()
        val feedback = requireNotNull(binding.workflow)
        assertEquals(1, api.analysisReads)
        binding.controller.edit("書き続ける文章。")
        runCurrent()
        binding.controller.synchronize()
        runCurrent()
        assertEquals(2, binding.controller.state.value.draft.expectedVersion)
        assertNull(feedback.state.value.entry)
        assertEquals(1, api.analysisReads)
        binding.controller.newEntry().join()
        binding.controller.edit("新しい日記。")
        runCurrent()
        binding.controller.synchronize()
        runCurrent()
        assertEquals(1, binding.controller.state.value.draft.expectedVersion)
        assertNull(feedback.state.value.entry)
        assertEquals(1, api.analysisReads)
        assertEquals(0, api.submissions)
    }

    @Test fun reconnectingAnOfflineDraftDoesNotOpenFeedback() = runTest {
        val binding = openNotebook()
        val feedback = requireNotNull(binding.workflow)
        journalApi.failSaves = true
        binding.controller.edit("オフラインでも書き続ける。")
        runCurrent()
        binding.controller.synchronize()
        runCurrent()
        assertNull(feedback.state.value.entry)
        assertTrue(binding.controller.state.value.savedLocally)
        journalApi.failSaves = false
        binding.controller.startSync()
        binding.controller.reconnect().join()
        binding.controller.stopSync()
        runCurrent()
        assertEquals(2, binding.controller.state.value.draft.expectedVersion)
        assertEquals("オフラインでも書き続ける。", binding.controller.state.value.draft.body)
        assertFalse(binding.controller.state.value.savedLocally)
        assertNull(feedback.state.value.entry)
        assertEquals(1, api.analysisReads)
    }

    @Test fun selectingTheSameEntryRestoresFeedbackAfterEditingAndSyncing() = runTest {
        val binding = openNotebook()
        val feedback = requireNotNull(binding.workflow)
        binding.controller.edit("保存した文章。")
        runCurrent()
        binding.controller.synchronize()
        runCurrent()
        val saved = binding.controller.state.value.records.single { it.id == entry.id }
        binding.controller.select(saved).join()
        runCurrent()
        assertEquals(2, api.analysisReads)
        assertEquals("保存した文章。", feedback.state.value.entry?.body)
        assertEquals(0, api.submissions)
    }

    @Test fun explicitSubmissionStillWorksAfterRepeatedAutomaticSaves() = runTest {
        val binding = openNotebook()
        val feedback = requireNotNull(binding.workflow)
        repeat(2) {
            binding.controller.edit("続きの文章 $it。")
            runCurrent()
            binding.controller.synchronize()
            runCurrent()
        }
        binding.controller.save { feedback.submit(it) }.join()
        runCurrent()
        assertEquals(1, api.submissions)
        assertEquals(WorkStatus.Ready, feedback.state.value.snapshot?.analysis?.status)
        assertEquals(binding.controller.state.value.draft.body, feedback.state.value.entry?.body)
        binding.controller.edit("さらに書き直す。")
        runCurrent()
        assertNull(feedback.state.value.entry)
    }

    private suspend fun openNotebook(missing: Boolean = false): NotebookBinding {
        api.missing = missing
        val session = Session("fixture-token", Account(1, "Sample", "sample@example.invalid"))
        sessions = SessionController(FakeAuth().apply { loginResult = { session } }, MemoryStorage(), dispatcher)
        val factory = viewModelFactory { initializer {
            NotebookViewModel(sessions, workflow = { _, scope, repository -> WorkflowController(WorkflowAccess(api, workflows, dispatcher, repository::acceptFeedbackMetadata), scope) }) {
                JournalRepository(journalApi, storage, dispatcher)
            }
        } }
        model = ViewModelProvider(owner, factory)["active-journal", NotebookViewModel::class.java]
        sessions.login("sample@example.invalid", "fixture-password")
        dispatcher.scheduler.runCurrent()
        return requireNotNull(model.binding.value)
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
    var analysisReads = 0
    var missing = false
    private fun result(entry: JournalEntry) = Analysis(JournalIds.newId(), entry.version, WorkStatus.Ready,
        null, null, CorrectedDocument(entry.body, entry.body), 0, null, "updated", "{}")
    override suspend fun startClarification(entry: JournalEntry) = Clarification(JournalIds.newId(), entry.version, 1,
        WorkStatus.Resolved, null, null, "updated", "{}")
    override suspend fun clarification(entry: JournalEntry) = startClarification(entry)
    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification = error("Unexpected answer")
    override suspend fun submit(entry: JournalEntry, clarificationId: String): Analysis { submissions++; return result(entry) }
    override suspend fun analysis(entry: JournalEntry): AnalysisResponse {
        analysisReads++
        if (missing) throw ApiFailure(404)
        return AnalysisResponse(entry, result(entry))
    }
    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis = error("Unexpected edit")
}
