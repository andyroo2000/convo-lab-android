package com.cdcg.convolab.journal

import com.cdcg.convolab.network.ApiFailure
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
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

    @Test fun reconnectDoesNotSendWhenTheJournalLifecycleIsStopped() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway()
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Offline writing")
        controller.flush().join()
        controller.reconnect().join()
        assertTrue(api.attempts.isEmpty())
        controller.startSync()
        runCurrent()
        assertEquals(1, api.attempts.size)
        controller.stopSync()
        controller.edit("More writing while stopped")
        controller.reconnect().join()
        assertEquals(1, api.attempts.size)
    }

    @Test fun cancellingASaveClearsTheSpinnerButKeepsTheDurableRequest() = runTest {
        val storage = NotebookMemory()
        val response = CompletableDeferred<Unit>()
        val api = SyncGateway().apply { beforeResponse = { response.await() } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Keep after cancellation")
        val saving = launch { controller.synchronize() }
        runCurrent()
        assertNotNull(controller.state.value.syncingId)
        saving.cancelAndJoin()
        assertNull(controller.state.value.syncingId)
        assertNotNull(storage.records[controller.state.value.draft.id]?.pendingSave)
        assertEquals("Saved on this device", controller.state.value.syncStatus)
    }

    @Test fun pendingPayloadWithoutANewerDraftRemainsVisibleAndSavedLocally() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway()
        val draft = JournalDraft.blank().edited("Pending payload")
        val original = JournalEntry(draft.id, null, "Original", draft.entryDate, 1, "now", "now")
        storage.records[draft.id] = JournalRecord(entry = original, pendingSave = draft.copy(expectedVersion = 1))
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        assertEquals("Pending payload", controller.state.value.draft.body)
        assertEquals("Saved on this device", controller.state.value.syncStatus)
    }

    @Test fun finishingAnOlderSaveDoesNotHideAFailedNewerLocalWrite() = runTest {
        val storage = NotebookMemory()
        val response = CompletableDeferred<Unit>()
        val api = SyncGateway().apply { beforeResponse = { response.await() } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Old durable text")
        val saving = launch { controller.synchronize() }
        runCurrent()
        storage.failWrites = true
        controller.edit("New text still only in memory")
        runCurrent()
        response.complete(Unit)
        saving.join()
        assertFalse(controller.state.value.savedLocally)
        assertEquals("New text still only in memory", controller.state.value.draft.body)
        assertTrue(controller.state.value.error.orEmpty().contains("could not be saved on this device"))
    }

    @Test fun completedSyncPreservesAnUnrelatedValidationError() = runTest {
        val response = CompletableDeferred<Unit>()
        val api = SyncGateway().apply { beforeResponse = { response.await() } }
        val repository = JournalRepository(api, NotebookMemory(), StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Valid durable writing")
        val saving = launch { controller.synchronize() }
        runCurrent()
        controller.edit("a".repeat(20_001))
        val validationError = controller.state.value.error
        assertNotNull(validationError)
        response.complete(Unit)
        saving.join()
        assertEquals("Synced", controller.state.value.syncStatus)
        assertEquals(validationError, controller.state.value.error)
    }

    @Test fun restartOffersRecoveryForAConflictAndNewEntryClearsIt() = runTest {
        val storage = NotebookMemory()
        val draft = JournalDraft.blank().edited("Conflicting writing")
        storage.records[draft.id] = JournalRecord(draft = draft, syncRejection = 409)
        val repository = JournalRepository(SyncGateway(), storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        assertTrue(controller.state.value.canRecoverCopy)
        controller.newEntry().join()
        assertFalse(controller.state.value.canRecoverCopy)
    }

    @Test fun rejectedWritingShowsWhyAutomaticSyncPaused() = runTest {
        val api = SyncGateway().apply { fail = ApiFailure(422) }
        val repository = JournalRepository(api, NotebookMemory(), StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Rejected writing")
        controller.synchronize()
        assertTrue(controller.state.value.error.orEmpty().contains("could not sync"))
        assertTrue(controller.state.value.savedLocally)
    }

    @Test fun temporaryServerFailureAllowsKeepAndCloseWithDurableWriting() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = ApiFailure(500) }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Keep through server outage")
        controller.requestClose()
        controller.keepAndClose().join()
        assertFalse(controller.state.value.closePrompt)
        assertEquals("", controller.state.value.draft.body)
        assertEquals("Keep through server outage", storage.records.values.single().draft?.body)
    }

    @Test fun keepAndCloseDoesNotHideAConflictingDraft() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = ApiFailure(409) }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.edit("Preserve conflicting writing")
        controller.requestClose()
        controller.keepAndClose().join()
        assertTrue(controller.state.value.closePrompt)
        assertEquals("Preserve conflicting writing", controller.state.value.draft.body)
        assertEquals("Preserve conflicting writing", storage.records.values.single().draft?.body)
        assertNotNull(controller.state.value.error)
    }

    @Test fun pendingOnlyWritingCanCloseDuringAnOutage() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = IOException("offline") }
        val draft = JournalDraft.blank().edited("Pending payload").copy(expectedVersion = 1)
        val original = JournalEntry(draft.id, null, "Original", draft.entryDate, 1, "now", "now")
        storage.records[draft.id] = JournalRecord(entry = original, pendingSave = draft)
        val controller = NotebookController(JournalRepository(api, storage, StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.start().join()
        controller.requestClose()
        controller.keepAndClose().join()
        assertFalse(controller.state.value.closePrompt)
        assertEquals(draft, storage.records[draft.id]?.pendingSave)
    }

    @Test fun restoredPermissionRetriesTheSameWritingAutomatically() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = ApiFailure(403) }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val draft = JournalDraft.blank().edited("Kept for the same account")
        repository.edit(draft)
        val sync = JournalAutomaticSync(repository) {}
        sync.synchronize(0)
        api.fail = null
        sync.reconnect()
        sync.synchronize(0)
        assertEquals(draft.body, storage.records[draft.id]?.entry?.body)
        assertNull(storage.records[draft.id]?.syncRejection)
    }

    @Test fun typingDoesNotBypassNetworkFailureBackoff() = runTest {
        val storage = NotebookMemory()
        val api = SyncGateway().apply { fail = IOException("offline") }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val draft = JournalDraft.blank().edited("First offline writing")
        repository.edit(draft)
        val sync = JournalAutomaticSync(repository) {}
        sync.synchronize(0)
        repository.edit(draft.edited("More offline writing"))
        sync.synchronize(1)
        assertEquals(1, api.attempts.size)
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
