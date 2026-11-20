package com.cdcg.convolab.journal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes durable edits; network work stays outside the disk lock so newer edits remain possible. */
class JournalRepository(
    private val api: JournalGateway,
    private val storage: JournalStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val saves = Mutex()

    suspend fun load(): JournalLoad = disk { storage.load() }
    suspend fun record(id: String): JournalRecord? = disk { storage.read(id) }

    suspend fun edit(draft: JournalDraft) = disk {
        val previous = storage.read(draft.id)
        val record = JournalRecord(previous?.entry, draft, previous?.pendingSave)
        storage.write(record)
        record
    }

    suspend fun refresh(cursor: String? = null): JournalPage {
        val page = api.archive(cursor)
        disk { page.entries.forEach(::mergeEntry) }
        return page
    }

    suspend fun fetch(id: String): JournalRecord {
        val entry = api.entry(id)
        return disk { mergeEntry(entry) }
    }

    suspend fun save(id: String): JournalEntry = saves.withLock {
        val sent = disk { prepareSave(id) } ?: return@withLock disk { requireNotNull(storage.read(id)?.entry) }
        val received = api.save(sent)
        disk {
            val current = requireNotNull(storage.read(id)) { "Draft disappeared while saving" }
            storage.write(current.acknowledge(sent, received))
        }
        received
    }

    suspend fun saveAsNew(id: String): JournalDraft = disk {
        val old = requireNotNull(storage.read(id)?.draft)
        val copy = old.copy(id = JournalIds.newId(), expectedVersion = 0, rootEntryId = null)
            .edited(old.body, old.title)
        storage.write(JournalRecord(draft = copy))
        copy
    }

    suspend fun delete(id: String) = saves.withLock {
        val current = disk { requireNotNull(storage.read(id)) }
        check(current.pendingSave == null) { "A save has not been confirmed. Retry it before deleting this entry." }
        current.entry?.let { api.delete(it.id, it.version) }
        disk {
            val latest = storage.read(id)
            check(latest == current) { "This draft changed while deletion was pending. The local copy was kept." }
            val children = storage.load().records.filter { it.rootEntryId == id }
            children.forEach { storage.remove(it.id) }
            storage.remove(id)
        }
    }

    private fun mergeEntry(entry: JournalEntry): JournalRecord {
        val current = storage.read(entry.id)
        if ((current?.entry?.version ?: -1) > entry.version) return current!!
        val merged = JournalRecord(entry, current?.draft, current?.pendingSave)
        storage.write(merged)
        return merged
    }

    private fun prepareSave(id: String): JournalDraft? {
        val current = requireNotNull(storage.read(id))
        val sent = current.pendingSave ?: current.draft ?: return null
        require(sent.body.isNotBlank()) { "Write an entry before saving it." }
        storage.write(current.copy(pendingSave = sent))
        return sent
    }

    private suspend fun <T> disk(action: () -> T): T = withContext(NonCancellable) {
        mutex.withLock { withContext(io) { action() } }
    }
}
