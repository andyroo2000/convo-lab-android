package com.cdcg.convolab.journal

import com.cdcg.convolab.network.ApiFailure
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalAutomaticSyncTest {
    @Test fun restartAndReconnectAutomaticallyDrainAnOfflineDraft() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = IOException("offline") }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val draft = JournalDraft.blank().edited("Offline writing")
        val repository = JournalRepository(api, storage, dispatcher)
        repository.edit(draft)
        JournalAutomaticSync(repository) {}.synchronize(0)
        assertEquals(draft, storage.records[draft.id]?.pendingSave)
        api.fail = null
        JournalAutomaticSync(JournalRepository(api, storage, dispatcher)) {}.synchronize(0)
        assertNull(storage.records[draft.id]?.draft)
        assertEquals(draft.body, storage.records[draft.id]?.entry?.body)
        assertEquals(listOf(draft, draft), api.attempts)
    }

    @Test fun lostResponseRetriesOriginalPayloadBeforeSendingNewerWriting() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = IOException("lost response") }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val first = JournalDraft.blank().edited("First request")
        repository.edit(first)
        val sync = JournalAutomaticSync(repository) {}
        sync.synchronize(0)
        val newer = first.edited("Newer writing")
        repository.edit(newer)
        api.fail = null
        sync.reconnect()
        sync.synchronize(0)
        assertEquals(listOf(first, first), api.attempts)
        assertEquals(newer.body, storage.records[first.id]?.draft?.body)
        assertEquals(1, storage.records[first.id]?.draft?.expectedVersion)
        sync.synchronize(0)
        assertEquals(newer.body, api.attempts.last().body)
        assertEquals(1, api.attempts.last().expectedVersion)
        assertNull(storage.records[first.id]?.draft)
    }

    @Test fun conflictsAndDeletedEntriesStayBlockedAfterReconstruction() = runTest {
        for (status in listOf(404, 409)) {
            val storage = NotebookMemory()
            val api = SyncGateway().apply { fail = ApiFailure(status) }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = JournalRepository(api, storage, dispatcher)
            val draft = JournalDraft.blank().edited("Preserved writing")
            repository.edit(draft)
            JournalAutomaticSync(repository) {}.synchronize(0)
            assertEquals(status, storage.records[draft.id]?.syncRejection)
            api.fail = null
            JournalAutomaticSync(JournalRepository(api, storage, dispatcher)) {}.synchronize(0)
            assertEquals(1, api.attempts.size)
            assertEquals(draft.body, storage.records[draft.id]?.draft?.body)
            val copy = repository.saveAsNew(draft.key)
            assertNotEquals(draft.id, copy.id)
            assertEquals(0, copy.expectedVersion)
        }
    }

    @Test fun typingDuringAnAutomaticSaveIsPreservedAndRebased() = runTest {
        val storage = NotebookMemory()
        val response = CompletableDeferred<Unit>()
        val api = SyncGateway().apply { beforeResponse = { response.await() } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("First text")
        controller.flush().join()
        val job = launch { controller.synchronize() }
        runCurrent()
        assertEquals("Syncing…", controller.state.value.syncStatus)
        assertTrue(controller.state.value.canEdit)
        controller.edit("Typing while syncing")
        controller.flush().join()
        response.complete(Unit)
        job.join()
        assertEquals("Typing while syncing", controller.state.value.draft.body)
        assertEquals(1, controller.state.value.draft.expectedVersion)
        assertEquals("Saved on this device", controller.state.value.syncStatus)
        controller.synchronize()
        assertEquals("Synced", controller.state.value.syncStatus)
    }

    @Test fun explicitSaveWaitsForNewerWritingAfterAnInterruptedRequest() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = IOException("response lost") }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("First attempt")
        controller.synchronize()
        controller.edit("Latest writing for submission")
        api.fail = null
        var saved: JournalEntry? = null
        controller.save { saved = it }.join()
        assertEquals("Latest writing for submission", saved?.body)
        assertEquals(2, saved?.version)
        assertNull(storage.records[saved?.id]?.draft)
    }

    @Test fun blankRepliesAndPendingDeletionsAreNotAutomaticallySaved() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway()
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val blank = JournalDraft.blank()
        val reply = JournalDraft.blank().edited("Reply").copy(rootEntryId = blank.id)
        val deleted = JournalDraft.blank().edited("Do not resurrect")
        storage.records[blank.id] = JournalRecord(draft = blank)
        storage.records[reply.id] = JournalRecord(draft = reply)
        storage.records[deleted.id] = JournalRecord(draft = deleted, pendingDelete = true)
        JournalAutomaticSync(repository) {}.synchronize(0)
        assertTrue(api.attempts.isEmpty())
    }

    @Test fun correctingInvalidWritingResumesAutomaticSync() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = ApiFailure(422) }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val draft = JournalDraft.blank().edited("Invalid writing")
        repository.edit(draft)
        val sync = JournalAutomaticSync(repository) {}
        sync.synchronize(0)
        assertEquals(422, storage.records[draft.id]?.syncRejection)
        repository.edit(draft.edited("Corrected writing"))
        api.fail = null
        sync.reconnect()
        sync.synchronize(0)
        assertNull(storage.records[draft.id]?.draft)
        assertEquals("Corrected writing", storage.records[draft.id]?.entry?.body)
    }

    @Test fun backoffIsResetWhenAConnectionReturns() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = IOException("offline") }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.edit(JournalDraft.blank().edited("Retry later"))
        val sync = JournalAutomaticSync(repository) {}
        sync.synchronize(0)
        sync.synchronize(1)
        assertEquals(1, api.attempts.size)
        sync.reconnect()
        api.fail = null
        sync.synchronize(1)
        assertEquals(2, api.attempts.size)
    }
}

private class SyncGateway : JournalGateway {
    val attempts = mutableListOf<JournalDraft>()
    var fail: Exception? = null
    var beforeResponse: suspend () -> Unit = {}
    override suspend fun archive(cursor: String?) = JournalPage(emptyList(), null)
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("Unexpected read")
    override suspend fun save(draft: JournalDraft): JournalEntry {
        attempts += draft
        fail?.let { throw it }
        beforeResponse()
        return JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, draft.expectedVersion + 1, "created", "updated")
    }
    override suspend fun delete(id: String, version: Int) = Unit
}
