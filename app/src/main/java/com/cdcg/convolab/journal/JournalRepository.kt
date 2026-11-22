package com.cdcg.convolab.journal

import com.cdcg.convolab.network.ApiFailure
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
    private var removalRevision = 0L

    suspend fun load(): JournalLoad = disk { storage.load() }
    suspend fun record(key: JournalId): JournalRecord? = disk { storage.read(key.value) }

    suspend fun accept(entry: JournalEntry): JournalRecord = disk { mergeEntry(entry) }

    /** Adopt a proven unchanged body without rewriting any in-flight save payload. */
    suspend fun acceptFeedbackMetadata(source: JournalEntry, received: JournalEntry) = disk {
        require(source.id == received.id && source.body == received.body)
        require(received.version >= source.version)
        val current = storage.read(source.id) ?: return@disk
        val known = current.entry ?: return@disk
        if (current.pendingDelete || known.version > received.version) return@disk
        if (known.body != source.body) return@disk
        val draft = current.draft?.let { it.copy(expectedVersion = maxOf(it.expectedVersion, received.version)) }
        val merged = current.copy(entry = received, draft = draft,
            acknowledgedVersion = maxOf(current.acknowledgedVersion, received.version))
        if (merged != current) storage.write(merged)
    }


    suspend fun answerDraft(root: JournalEntry, origin: AnswerOrigin): JournalRecord = disk {
        check(storage.read(root.id)?.pendingDelete != true) { "This conversation is being deleted." }
        val local = storage.load()
        check(local.recoveryFiles.isEmpty()) { "Saved writing needs recovery before starting another answer." }
        local.records.firstOrNull { it.rootEntryId == root.id && it.answerOrigin == origin }?.let { return@disk it }
        val draft = JournalDraft.blank().copy(rootEntryId = root.id, entryDate = root.entryDate)
        JournalRecord(draft = draft, answerOrigin = origin).also(storage::write)
    }

    suspend fun edit(draft: JournalDraft) = disk {
        val previous = storage.read(draft.id)
        check(previous?.pendingDelete != true) { "Deletion is awaiting confirmation. Retry deleting this entry first." }
        val parent = draft.rootEntryId?.let(storage::read)
        check(parent?.pendingDelete != true) { "This conversation is being deleted. Your writing has been kept in the editor." }
        val version = maxOf(draft.expectedVersion, previous?.acknowledgedVersion ?: 0)
        val rebased = draft.copy(expectedVersion = version)
        val record = previous?.copy(draft = rebased) ?: JournalRecord(draft = rebased)
        storage.write(record)
        record
    }

    suspend fun refresh(cursor: String? = null): JournalPage {
        val revision = disk { removalRevision }
        val page = api.archive(cursor)
        disk { if (revision == removalRevision) mergePage(page) }
        return page
    }

    suspend fun fetch(key: JournalId): JournalRecord {
        val id = key.value
        val revision = disk { removalRevision }
        val entry = api.entry(id)
        return disk {
            check(revision == removalRevision) { "An entry was deleted while loading. Please refresh." }
            mergeEntry(entry)
        }
    }

    suspend fun save(key: JournalId): JournalEntry = saves.withLock {
        val id = key.value
        val sent = disk { prepareSave(requireNotNull(storage.read(id))) } ?: return@withLock disk { requireNotNull(storage.read(id)?.entry) }
        val origin = disk { storage.read(id)?.takeIf { sent.expectedVersion == 0 }?.answerOrigin }
        val received = saveRequest(sent, origin)
        disk {
            val current = requireNotNull(storage.read(id)) { "Draft disappeared while saving" }
            storage.write(current.acknowledge(sent, received))
        }
        received
    }

    suspend fun saveAsNew(key: JournalId): JournalDraft = disk {
        val id = key.value
        val old = requireNotNull(storage.read(id)?.draft)
        val copy = old.copy(id = JournalIds.newId(), expectedVersion = 0, rootEntryId = null)
            .edited(old.body, old.title)
        storage.write(JournalRecord(draft = copy))
        copy
    }

    suspend fun delete(key: JournalId) = saves.withLock {
        val id = key.value
        val current = disk { prepareDelete(requireNotNull(storage.read(id))) }
        deleteRequest(current)
        disk {
            val children = storage.load().records.filter { it.rootEntryId == id }
            children.forEach { storage.remove(it.id) }
            storage.remove(id)
            removalRevision++
        }
    }

    private suspend fun saveRequest(sent: JournalDraft, origin: AnswerOrigin?): JournalEntry {
        try { return if (origin == null) api.save(sent) else api.answer(sent, origin) }
        catch (failure: ApiFailure) {
            if (failure.isDefinitiveRejection()) disk {
                val current = requireNotNull(storage.read(sent.id))
                storage.write(current.copy(pendingSave = null))
            }
            throw failure
        }
    }

    private suspend fun deleteRequest(current: JournalRecord) {
        val entry = current.entry ?: return
        try { api.delete(entry.id, entry.version) }
        catch (failure: ApiFailure) {
            if (failure.status == 404) return
            if (failure.isDefinitiveRejection()) disk {
                val latest = requireNotNull(storage.read(current.id))
                storage.write(latest.copy(pendingDelete = false))
            }
            throw failure
        }
    }

    private fun prepareDelete(current: JournalRecord): JournalRecord {
        val id = current.id
        check(current.pendingSave == null) { "A save has not been confirmed. Retry it before deleting this entry." }
        val local = storage.load()
        check(local.recoveryFiles.isEmpty()) { "Saved records need recovery before deleting a conversation." }
        val children = local.records.filter { it.rootEntryId == id }
        check(children.none { it.draft?.body?.isNotBlank() == true || it.pendingSave != null }) {
            "This conversation has unsaved replies. Save or copy them before deleting it."
        }
        storage.write(current.copy(pendingDelete = true))
        return current
    }

    private fun mergePage(page: JournalPage) {
        val unreadable = storage.load().recoveryFiles.toSet()
        page.entries.filterNot { "${it.id}.json" in unreadable }.forEach(::mergeEntry)
    }

    private fun mergeEntry(entry: JournalEntry): JournalRecord {
        val current = storage.read(entry.id)
        if (current?.pendingDelete == true) return current
        if (current?.entry == entry) return current
        if ((current?.entry?.version ?: -1) > entry.version) return current!!
        val merged = current?.copy(entry = entry) ?: JournalRecord(entry = entry)
        storage.write(merged)
        return merged
    }

    private fun prepareSave(current: JournalRecord): JournalDraft? {
        check(!current.pendingDelete) { "Deletion is awaiting confirmation. Retry deleting this entry first." }
        val sent = current.pendingSave ?: current.draft ?: return null
        require(sent.body.isNotBlank()) { "Write an entry before saving it." }
        storage.write(current.copy(pendingSave = sent))
        return sent
    }

    private suspend fun <T> disk(action: () -> T): T = withContext(NonCancellable) {
        mutex.withLock { withContext(io) { action() } }
    }
}

private fun ApiFailure.isDefinitiveRejection() = status in setOf(400, 403, 404, 409, 422)
