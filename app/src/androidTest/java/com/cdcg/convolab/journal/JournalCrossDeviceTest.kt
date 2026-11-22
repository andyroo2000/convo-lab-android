package com.cdcg.convolab.journal

import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.network.ApiFailure
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

/** Exercises real Android disk recovery against remote writes; the API auth matrix lives in learning-os. */
class JournalCrossDeviceTest {
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sync-test-${UUID.randomUUID()}")
    private val server = SyncServer()
    private val gateway = SyncGateway(server)
    private fun repository() = JournalRepository(gateway, JournalDiskStore(root, 17),
        archive = JournalArchiveDiskStore(root, 17))

    @After fun cleanup() { root.deleteRecursively() }

    @Test fun refreshedContentsReflectRemoteCreatesEditsAndDeletesAfterRestart() = runBlocking {
        val original = server.save(JournalDraft.blank().edited("最初の文章。"))
        val repository = repository()
        repository.refresh()
        gateway.offline = true
        failsOffline { repository.refresh() }
        server.save(JournalRecord(entry = original).editableDraft().edited("別の端末で編集した。"))
        val created = server.save(JournalDraft.blank().edited("新しく作成した。"))
        gateway.offline = false
        val restarted = repository()
        restarted.refresh()
        assertEquals("別の端末で編集した。", restarted.record(JournalId(original.id))?.entry?.body)
        assertEquals(setOf(original.id, created.id), restarted.load().archive?.entryIds?.toSet())
        server.delete(original.id, 2)
        restarted.refresh()
        assertEquals(setOf(created.id), restarted.load().archive?.entryIds)
    }

    @Test fun offlineCreateAndEditSurviveRestartAndLostAcknowledgementWithoutDuplicates() = runBlocking {
        val draft = JournalDraft.blank().edited("オフラインで作成した。")
        val repository = repository()
        repository.edit(draft)
        gateway.offline = true
        failsOffline { repository.save(draft.key) }
        val restarted = repository()
        assertEquals(draft, restarted.record(draft.key)?.pendingSave)
        gateway.offline = false
        gateway.loseAcknowledgement = true
        failsOffline { restarted.save(draft.key) }
        val saved = repository().save(draft.key)
        assertEquals(1, server.entries.size)
        assertEquals(1, saved.version)
        val edited = JournalRecord(entry = saved).editableDraft().edited("オフラインで編集した。")
        restarted.edit(edited)
        gateway.offline = true
        failsOffline { restarted.save(draft.key) }
        gateway.offline = false
        val updated = repository().save(draft.key)
        assertEquals(edited.body, updated.body)
        assertEquals(2, updated.version)
    }

    @Test fun remoteChangesAndDeletionNeverOverwriteOfflineWritingOrResurrectDeletedIds() = runBlocking {
        val original = server.save(JournalDraft.blank().edited("最初の文章。"))
        val repository = repository()
        repository.refresh()
        val draft = JournalRecord(entry = original).editableDraft().edited("オフラインの編集。")
        repository.edit(draft)
        gateway.offline = true
        failsOffline { repository.save(draft.key) }
        server.save(JournalRecord(entry = original).editableDraft().edited("サーバーの編集。"))
        gateway.offline = false
        failsWith(409) { repository().save(draft.key) }
        assertEquals(draft.body, repository().record(draft.key)?.draft?.body)
        assertEquals("サーバーの編集。", server.entries[original.id]?.body)
        server.delete(original.id, 2)
        failsWith(404) { repository().save(draft.key) }
        repository.refresh()
        assertTrue(repository.load().localWorkRoots.contains(original.id))
        val copy = repository.saveAsNew(draft.key)
        assertNotEquals(draft.id, copy.id)
        assertEquals(draft.body, copy.body)
        assertEquals(0, copy.expectedVersion)
        repository.save(copy.key)
        assertFalse(server.entries.containsKey(original.id))
        assertEquals(setOf(copy.id), server.entries.keys)
    }

    @Test fun offlineDeleteSurvivesRestartAndOnlyRemovesLocalCopyAfterServerConfirmation() = runBlocking {
        val original = server.save(JournalDraft.blank().edited("削除のテスト。"))
        val repository = repository()
        repository.refresh()
        gateway.offline = true
        failsOffline { repository.delete(JournalId(original.id)) }
        assertTrue(repository().record(JournalId(original.id))!!.pendingDelete)
        gateway.offline = false
        repository().delete(JournalId(original.id))
        assertNull(repository().record(JournalId(original.id)))
        assertTrue(server.entries.isEmpty())
    }

    private suspend fun failsOffline(action: suspend () -> Unit) {
        try { action(); fail("Expected offline failure") } catch (_: IOException) { }
    }
    private suspend fun failsWith(status: Int, action: suspend () -> Unit) {
        try { action(); fail("Expected conflict or deletion") }
        catch (failure: ApiFailure) { assertEquals(status, failure.status) }
    }
}

private class SyncGateway(private val server: SyncServer) : JournalGateway {
    var offline = false
    var loseAcknowledgement = false
    private fun checkConnection() { if (offline) throw IOException("Offline fixture") }
    override suspend fun unfinished() = archive(null)
    override suspend fun archive(cursor: String?): JournalPage {
        checkConnection()
        return JournalPage(server.entries.values.toList(), null)
    }
    override suspend fun entry(id: String): JournalEntry {
        checkConnection()
        return server.entries[id] ?: throw ApiFailure(404)
    }
    override suspend fun save(draft: JournalDraft): JournalEntry {
        checkConnection()
        val saved = server.save(draft)
        if (loseAcknowledgement) { loseAcknowledgement = false; throw IOException("Lost acknowledgement") }
        return saved
    }
    override suspend fun delete(id: String, version: Int) { checkConnection(); server.delete(id, version) }
}

private class SyncServer {
    val entries = linkedMapOf<String, JournalEntry>()
    private val deleted = mutableSetOf<String>()
    fun save(draft: JournalDraft): JournalEntry {
        if (draft.id in deleted) throw ApiFailure(404)
        val current = entries[draft.id]
        if (current != null && matches(current, draft)) return current
        if ((current?.version ?: 0) != draft.expectedVersion) throw ApiFailure(409)
        val saved = JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, draft.expectedVersion + 1, "now", "now")
        entries[draft.id] = saved
        return saved
    }
    private fun matches(entry: JournalEntry, draft: JournalDraft) =
        Triple(entry.body, entry.title, entry.entryDate) == Triple(draft.body, draft.title, draft.entryDate)

    fun delete(id: String, version: Int) {
        if (id in deleted) return
        val current = entries[id] ?: throw ApiFailure(404)
        if (current.version != version) throw ApiFailure(409)
        entries.remove(id)
        deleted.add(id)
    }
}
