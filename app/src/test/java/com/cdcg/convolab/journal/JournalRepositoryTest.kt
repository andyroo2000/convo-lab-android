package com.cdcg.convolab.journal

import java.io.IOException
import com.cdcg.convolab.network.ApiFailure
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalRepositoryTest {
    private val draft = JournalDraft.blank().edited("今日は映画を見ました。")
    private fun saved(source: JournalDraft) = JournalEntry(source.id, source.title, source.body, source.entryDate,
        source.expectedVersion + 1, "created", "updated", source.rootEntryId)

    @Test fun acknowledgementKeepsNewerLocalWritingAndAdvancesItsVersion() = runTest {
        val response = CompletableDeferred<JournalEntry>()
        val storage = MemoryJournal()
        val api = FakeJournal().apply { saveResult = { response.await() } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.edit(draft)
        val save = launch { repository.save(draft.key) }
        runCurrent()
        val newer = draft.edited(draft.body + "楽しかったです。")
        repository.edit(newer)
        response.complete(saved(draft))
        save.join()
        val record = repository.record(draft.key)!!
        assertEquals(newer.body, record.draft?.body)
        assertEquals(newer.editId, record.draft?.editId)
        assertEquals(1, record.draft?.expectedVersion)
        assertNull(record.pendingSave)
        assertEquals(draft.body, record.entry?.body)
    }

    @Test fun unchangedSaveAcknowledgementMayKeepTheSameServerVersion() = runTest {
        val entry = saved(draft)
        val unchanged = JournalDraft.from(entry)
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(entry, unchanged) }
        val api = FakeJournal().apply { saveResult = { entry } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        assertEquals(entry, repository.save(draft.key))
        assertNull(repository.record(draft.key)?.draft)
        assertNull(repository.record(draft.key)?.pendingSave)
    }

    @Test fun acknowledgementAtomicallyReplacesMatchingDraftWithServerCopy() = runTest {
        val storage = MemoryJournal()
        val api = FakeJournal().apply { saveResult = ::saved }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.edit(draft)
        repository.save(draft.key)
        val record = repository.record(draft.key)!!
        assertNull(record.draft)
        assertNull(record.pendingSave)
        assertEquals(draft.body, record.entry?.body)
    }

    @Test fun unknownSaveOutcomeRetriesTheSameFrozenRequestAfterReopen() = runTest {
        val storage = MemoryJournal()
        val api = FakeJournal().apply { saveResult = { throw IOException("fixture timeout") } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.edit(draft)
        try { repository.save(draft.key); fail("Expected network failure") } catch (_: IOException) { }
        val newer = draft.edited("追加した文章。")
        repository.edit(newer)
        val reopened = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        api.saveResult = ::saved
        reopened.save(draft.key)
        assertEquals(listOf(draft, draft), api.saved)
        assertEquals(newer.body, reopened.record(draft.key)?.draft?.body)
        assertEquals(1, reopened.record(draft.key)?.draft?.expectedVersion)
    }

    @Test fun deleteDoesNotDiscardAnUnconfirmedSave() = runTest {
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(draft = draft, pendingSave = draft) }
        val repository = JournalRepository(FakeJournal(), storage, StandardTestDispatcher(testScheduler))
        try { repository.delete(draft.key); fail("Pending save must be reconciled") } catch (_: IllegalStateException) { }
        assertNotNull(storage.records[draft.id])
    }

    @Test fun failedServerDeletionKeepsLocalEntryAndDraft() = runTest {
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(saved(draft), draft) }
        val api = FakeJournal().apply { deleteResult = { throw IOException("fixture offline") } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        try { repository.delete(draft.key); fail("Expected offline failure") } catch (_: IOException) { }
        assertEquals(draft, storage.records[draft.id]?.draft)
    }

    @Test fun pendingDeletionRejectsEditsAndRemainsRetryableAfterFailure() = runTest {
        val response = CompletableDeferred<Unit>()
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(saved(draft)) }
        val api = FakeJournal().apply { deleteResult = { response.await() } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        val deletion = launch { try { repository.delete(draft.key) } catch (_: IOException) { } }
        runCurrent()
        try { repository.edit(draft.edited("new")); fail("Delete must block edits") } catch (_: IllegalStateException) { }
        response.completeExceptionally(IOException("response lost"))
        deletion.join()
        assertTrue(storage.records[draft.id]!!.pendingDelete)
        api.deleteResult = {}
        repository.delete(draft.key)
        assertNull(storage.records[draft.id])
    }

    @Test fun unsavedConversationChildrenBlockDeletionBeforeNetwork() = runTest {
        val child = JournalDraft.blank().edited("返事。").copy(rootEntryId = draft.id)
        val storage = MemoryJournal().apply {
            records[draft.id] = JournalRecord(saved(draft))
            records[child.id] = JournalRecord(draft = child)
        }
        val api = FakeJournal().apply { deleteResult = { fail("Must not delete server conversation") } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        try { repository.delete(draft.key); fail("Unsaved replies must be preserved") } catch (_: IllegalStateException) { }
        assertFalse(storage.records[draft.id]!!.pendingDelete)
        assertEquals(child, storage.records[child.id]?.draft)
    }

    @Test fun newerServerCachePreservesFrozenSaveAndLocalEdit() = runTest {
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(draft = draft, pendingSave = draft) }
        val api = FakeJournal().apply { page = JournalPage(listOf(saved(draft).copy(version = 4)), null) }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.refresh()
        assertEquals(4, storage.records[draft.id]?.entry?.version)
        assertEquals(draft, storage.records[draft.id]?.pendingSave)
        assertEquals(draft, storage.records[draft.id]?.draft)
    }

    @Test fun definitiveRejectionClearsOnlyThePendingOperation() = runTest {
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(saved(draft), draft) }
        val api = FakeJournal().apply {
            saveResult = { throw ApiFailure(409) }
            deleteResult = { throw ApiFailure(409) }
        }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        try { repository.save(draft.key); fail("Expected conflict") } catch (_: ApiFailure) { }
        assertNull(storage.records[draft.id]?.pendingSave)
        assertEquals(draft, storage.records[draft.id]?.draft)
        try { repository.delete(draft.key); fail("Expected conflict") } catch (_: ApiFailure) { }
        assertFalse(storage.records[draft.id]!!.pendingDelete)
        assertEquals(draft, storage.records[draft.id]?.draft)
    }

    @Test fun alreadyMissingServerEntryCompletesLocalDeletion() = runTest {
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(saved(draft), pendingDelete = true) }
        val api = FakeJournal().apply { deleteResult = { throw ApiFailure(404) } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.delete(draft.key)
        assertNull(storage.records[draft.id])
    }

    @Test fun cancellingAnInFlightSaveKeepsItsExactRetryPayload() = runTest {
        val response = CompletableDeferred<JournalEntry>()
        val storage = MemoryJournal()
        val api = FakeJournal().apply { saveResult = { response.await() } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.edit(draft)
        val request = launch { repository.save(draft.key) }
        runCurrent()
        request.cancelAndJoin()
        assertEquals(draft, storage.records[draft.id]?.pendingSave)
        assertEquals(draft, storage.records[draft.id]?.draft)
    }

    @Test fun serverRefreshCannotOverwriteLocalDraftOrNewerCachedVersion() = runTest {
        val entry = saved(draft).copy(version = 3)
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(entry, draft) }
        val api = FakeJournal().apply { page = JournalPage(listOf(saved(draft)), "next") }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        assertEquals("next", repository.refresh().nextCursor)
        assertEquals(entry, storage.records[draft.id]?.entry)
        assertEquals(draft, storage.records[draft.id]?.draft)
    }

    @Test fun failedLocalAcknowledgementRetainsRetryPayload() = runTest {
        val storage = MemoryJournal()
        val api = FakeJournal().apply { saveResult = { storage.failWrites = true; saved(it) } }
        val repository = JournalRepository(api, storage, StandardTestDispatcher(testScheduler))
        repository.edit(draft)
        try { repository.save(draft.key); fail("Expected storage failure") } catch (_: IOException) { }
        assertEquals(draft, storage.records[draft.id]?.draft)
        assertEquals(draft, storage.records[draft.id]?.pendingSave)
    }

    @Test fun saveAsNewRetainsOriginalRecoveryCopy() = runTest {
        val storage = MemoryJournal().apply { records[draft.id] = JournalRecord(draft = draft, pendingSave = draft) }
        val repository = JournalRepository(FakeJournal(), storage, StandardTestDispatcher(testScheduler))
        val copy = repository.saveAsNew(draft.key)
        assertNotEquals(draft.id, copy.id)
        assertEquals(draft.body, copy.body)
        assertEquals(0, copy.expectedVersion)
        assertEquals(2, storage.records.size)
        assertEquals(draft, storage.records[draft.id]?.pendingSave)
    }
}

private class MemoryJournal : JournalStorage {
    val records = mutableMapOf<String, JournalRecord>()
    var failWrites = false
    override fun load() = JournalLoad(records.values.toList(), emptyList())
    override fun read(id: String) = records[id]
    override fun write(record: JournalRecord) {
        if (failWrites) throw IOException("fixture disk failure")
        records[record.id] = record
    }
    override fun remove(id: String) { records.remove(id) }
}

private class FakeJournal : JournalGateway {
    val saved = mutableListOf<JournalDraft>()
    var page = JournalPage(emptyList(), null)
    var saveResult: suspend (JournalDraft) -> JournalEntry = { error("Unexpected save") }
    var deleteResult: suspend () -> Unit = {}
    override suspend fun archive(cursor: String?) = page
    override suspend fun unfinished() = page
    override suspend fun entry(id: String) = page.entries.first { it.id == id }
    override suspend fun save(draft: JournalDraft): JournalEntry { saved += draft; return saveResult(draft) }
    override suspend fun delete(id: String, version: Int) = deleteResult()
}
