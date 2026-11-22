package com.cdcg.convolab.journal

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalArchiveRefreshTest {
    private val first = entry("First")
    private val second = entry("Second")
    private val memory = NotebookMemory()
    private val index = MemoryJournalArchive()
    private val api = ArchiveGateway()

    @Test fun unreadableChildRecordsKeepPotentialParentsReachable() {
        val local = JournalLoad(listOf(JournalRecord(entry = first), JournalRecord(entry = second)), listOf("unreadable.json"))
        assertEquals(setOf(first.id, second.id), JournalLocalWork().roots(local))
    }

    @Test fun automaticEmptyReplyDoesNotKeepARemotelyDeletedParentListed() = runTest {
        cache(first)
        val reply = JournalDraft.blank().copy(rootEntryId = first.id)
        memory.records[reply.id] = JournalRecord(draft = reply)
        val controller = NotebookController(JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index), backgroundScope)
        controller.start().join()
        assertTrue(controller.state.value.contentsRecords.isEmpty())
        assertEquals(reply, memory.records[reply.id]?.draft)
        assertNotNull(memory.records[first.id])
    }

    @Test fun cancellationIsNotMistakenForUnreadableFeedback() {
        val local = JournalLoad(listOf(JournalRecord(entry = first)), emptyList())
        val work = JournalLocalWork { throw CancellationException("Cancelled") }
        assertThrows(CancellationException::class.java) { work.roots(local) }
    }

    @Test fun localDeletionRemovesMembershipBeforeOfflineReopen() = runTest {
        cache(first)
        api.page = JournalPage(listOf(first), null)
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        repository.refresh()
        repository.delete(JournalId(first.id))
        assertTrue(index.read()!!.entryIds.isEmpty())
        assertNull(repository.record(JournalId(first.id)))
        api.offline = true
        val controller = NotebookController(JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index), backgroundScope)
        controller.start().join()
        assertTrue(controller.state.value.contentsRecords.isEmpty())
    }

    @Test fun loadMoreWithoutAnIndexRequiresAFirstPageRefresh() = runTest {
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        api.page = JournalPage(listOf(second), null)
        repository.refresh("obsolete-page")
        assertNull(repository.load().archive)
        assertTrue(memory.records.isEmpty())
        repository.refresh()
        assertEquals(setOf(second.id), repository.load().archive?.entryIds)
    }

    @Test fun serverDeletionDisappearsFromContentsWithoutDeletingCachedWriting() = runTest {
        cache(first, second)
        api.page = JournalPage(listOf(first, second), null)
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        controller.select(JournalRecord(entry = second)).join()
        api.page = JournalPage(listOf(first), null)
        controller.refreshContents().join()
        assertEquals(listOf(first.id), controller.state.value.contentsRecords.map { it.id })
        assertEquals(second.body, controller.state.value.draft.body)
        assertEquals(second, repository.record(JournalId(second.id))?.entry)
        assertEquals(2, controller.state.value.records.size)
        api.offline = true
        val reopened = NotebookController(JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index), backgroundScope)
        reopened.start().join()
        assertEquals(listOf(first.id), reopened.state.value.contentsRecords.map { it.id })
        assertNotNull(reopened.state.value.error)
    }

    @Test fun pagesAppendButRefreshReplacesMembershipAndRetainsOlderCache() = runTest {
        cache(first, second)
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        api.page = JournalPage(listOf(first), "page-two")
        repository.refresh()
        assertEquals(setOf(first.id), repository.load().archive?.entryIds)
        api.page = JournalPage(listOf(second), null)
        repository.refresh("page-two")
        assertEquals(setOf(first.id, second.id), repository.load().archive?.entryIds)
        api.page = JournalPage(listOf(first), "new-page-two")
        repository.refresh()
        assertEquals(setOf(first.id), repository.load().archive?.entryIds)
        assertNotNull(repository.record(JournalId(second.id)))
        api.page = JournalPage(listOf(second), null)
        repository.refresh("page-two") // A cursor from the replaced listing cannot append stale results.
        assertEquals(setOf(first.id), repository.load().archive?.entryIds)
        assertEquals("new-page-two", repository.load().archive?.nextCursor)
    }

    @Test fun successfulEmptyArchiveIsDistinctFromAnOfflineRefresh() = runTest {
        cache(first)
        api.offline = true
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        assertEquals(1, controller.state.value.contentsRecords.size)
        assertNull(repository.load().archive)
        api.offline = false
        controller.refreshContents().join()
        assertTrue(controller.state.value.contentsRecords.isEmpty())
        api.offline = true
        controller.refreshContents().join()
        assertTrue(controller.state.value.contentsRecords.isEmpty())
        assertNotNull(repository.record(JournalId(first.id)))
    }

    @Test fun emptyServerListPreservesDraftsFrozenSavesChildWritingAndFeedbackRecovery() = runTest {
        val draft = JournalDraft.blank().edited("Unsaved")
        val child = JournalDraft.blank().edited("Unsent reply").copy(rootEntryId = first.id)
        val feedback = entry("Pending feedback")
        val unreadable = entry("Unreadable feedback")
        cache(first, second, feedback, unreadable)
        memory.records[draft.id] = JournalRecord(draft = draft, pendingSave = draft)
        memory.records[child.id] = JournalRecord(draft = child)
        val work = JournalLocalWork {
            if (it.value == unreadable.id) throw IOException("Unreadable workflow")
            it.value == feedback.id
        }
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index, work)
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        assertEquals(setOf(draft.id, first.id, feedback.id, unreadable.id), controller.state.value.contentsRecords.map { it.id }.toSet())
        assertEquals(draft, memory.records[draft.id]?.pendingSave)
        assertEquals(child, memory.records[child.id]?.draft)
        assertEquals(6, memory.records.size)
    }

    @Test fun anOlderRefreshCannotRestoreAnEntryRemovedByANewerRefresh() = runTest {
        val old = CompletableDeferred<JournalPage>()
        api.response = { old.await() }
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        val request = launch { repository.refresh() }
        runCurrent()
        api.response = null
        repository.refresh()
        old.complete(JournalPage(listOf(first), null))
        request.join()
        assertTrue(repository.load().archive!!.entryIds.isEmpty())
        assertNull(repository.record(JournalId(first.id)))
    }

    @Test fun aSaveAcknowledgedAfterLoadMoreStartedKeepsTheCursorForRetry() = runTest {
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index)
        api.page = JournalPage(listOf(first), "page-two")
        repository.refresh()
        val old = CompletableDeferred<JournalPage>()
        api.response = { old.await() }
        val request = launch { repository.refresh("page-two") }
        runCurrent()
        val draft = JournalDraft.blank().edited("Saved while list was loading")
        repository.edit(draft)
        repository.save(draft.key)
        old.complete(JournalPage(listOf(second), null))
        request.join()
        assertEquals(setOf(first.id, draft.id), repository.load().archive?.entryIds)
        assertEquals("page-two", repository.load().archive?.nextCursor)
        assertNull(repository.record(draft.key)?.pendingSave)
        api.response = null
        api.page = JournalPage(listOf(second), null)
        repository.refresh("page-two")
        assertEquals(setOf(first.id, second.id, draft.id), repository.load().archive?.entryIds)
    }

    @Test fun failedIndexWriteKeepsTheSaveRetryPayload() = runTest {
        val archive = object : JournalArchiveStorage {
            override fun read() = JournalArchive(emptySet(), null)
            override fun write(archive: JournalArchive) { throw IOException("Disk full") }
        }
        val repository = JournalRepository(api, memory, StandardTestDispatcher(testScheduler), archive)
        val draft = JournalDraft.blank().edited("Keep this")
        repository.edit(draft)
        try { repository.save(draft.key); fail("Expected failed index write") } catch (_: IOException) { }
        assertEquals(draft, repository.record(draft.key)?.pendingSave)
        assertEquals(draft, repository.record(draft.key)?.draft)
    }

    @Test fun visibleContentsRefreshesWhenReturningToTheApp() = runTest {
        cache(first)
        api.page = JournalPage(listOf(first), null)
        val controller = NotebookController(JournalRepository(api, memory, StandardTestDispatcher(testScheduler), index), backgroundScope)
        controller.start().join()
        controller.showContents(true)
        runCurrent()
        api.page = JournalPage(emptyList(), null)
        controller.refreshVisibleContents()
        runCurrent()
        assertTrue(controller.state.value.contentsRecords.isEmpty())
        controller.showContents(false)
        api.page = JournalPage(listOf(first), null)
        controller.refreshVisibleContents()
        runCurrent()
        assertTrue(controller.state.value.contentsRecords.isEmpty())
    }

    private fun cache(vararg entries: JournalEntry) { entries.forEach { memory.records[it.id] = JournalRecord(entry = it) } }
    private fun entry(title: String): JournalEntry = JournalDraft.blank().edited("Fixture writing", title).let {
        JournalEntry(it.id, it.title, it.body, it.entryDate, 1, "created", "updated")
    }
}

private class ArchiveGateway : JournalGateway {
    var page = JournalPage(emptyList(), null)
    var offline = false
    var response: (suspend () -> JournalPage)? = null
    override suspend fun archive(cursor: String?): JournalPage {
        if (offline) throw IOException("Offline")
        return response?.invoke() ?: page
    }
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String) = error("No entry fetch expected")
    override suspend fun save(draft: JournalDraft) = JournalEntry(draft.id, draft.title, draft.body, draft.entryDate,
        draft.expectedVersion + 1, "created", "updated", draft.rootEntryId)
    override suspend fun delete(id: String, version: Int) = Unit
}
