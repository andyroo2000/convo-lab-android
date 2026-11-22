package com.cdcg.convolab.journal

import java.time.LocalDate
import java.util.UUID

data class JournalEntry(
    val id: String, val title: String?, val body: String, val entryDate: String,
    val version: Int, val createdAt: String, val updatedAt: String, val rootEntryId: String? = null,
)

data class JournalDraft(
    val id: String, val title: String?, val body: String, val entryDate: String,
    val expectedVersion: Int, val editId: String, val rootEntryId: String? = null,
) {
    fun edited(text: String, heading: String? = title): JournalDraft {
        require(!text.contains('\u0000'))
        require(heading?.contains('\u0000') != true)
        require(text.codePointCount(0, text.length) <= 20_000)
        require(heading == null || heading.codePointCount(0, heading.length) <= 255)
        return copy(body = text, title = heading?.trim()?.takeIf { it.isNotBlank() }, editId = UUID.randomUUID().toString())
    }

    companion object {
        fun blank() = JournalDraft(JournalIds.newId(), null, "", LocalDate.now().toString(), 0, UUID.randomUUID().toString())
        fun from(entry: JournalEntry) = JournalDraft(entry.id, entry.title, entry.body, entry.entryDate,
            entry.version, UUID.randomUUID().toString(), entry.rootEntryId)
    }
}

data class AnswerOrigin(val parentId: JournalId, val questionId: JournalId)

/** Acknowledged server content and its optional newer local draft share one atomic record. */
data class JournalRecord(val entry: JournalEntry? = null, val draft: JournalDraft? = null, val pendingSave: JournalDraft? = null, val pendingDelete: Boolean = false, val acknowledgedVersion: Int = 0, val answerOrigin: AnswerOrigin? = null) {
    init {
        require(entry != null || draft != null)
        require(entry == null || draft == null || entry.id == draft.id)
        require(pendingSave == null || pendingSave.id == (draft?.id ?: entry?.id))
    }
    val id: String get() = draft?.id ?: entry!!.id
    val rootEntryId: String? get() = draft?.rootEntryId ?: entry?.rootEntryId

    fun acknowledge(sent: JournalDraft, received: JournalEntry): JournalRecord {
        require(received.id == id && sent.id == id)
        require(received.version > 0 && received.version >= sent.expectedVersion)
        val remaining = draft?.takeUnless { it.editId == sent.editId }?.copy(expectedVersion = received.version)
        val latest = entry?.takeIf { it.version > received.version } ?: received
        return copy(entry = latest, draft = remaining, pendingSave = null, acknowledgedVersion = received.version)
    }
}

data class JournalPage(val entries: List<JournalEntry>, val nextCursor: String?)
data class JournalLoad(val records: List<JournalRecord>, val recoveryFiles: List<String>,
    val archive: JournalArchive? = null, val localWorkRoots: Set<String> = emptySet())

interface JournalGateway {
    suspend fun archive(cursor: String? = null): JournalPage
    suspend fun unfinished(): JournalPage
    suspend fun entry(id: String): JournalEntry
    suspend fun save(draft: JournalDraft): JournalEntry
    suspend fun answer(draft: JournalDraft, origin: AnswerOrigin): JournalEntry = error("Conversation answers are not supported by this gateway")
    suspend fun delete(id: String, version: Int)
}

interface JournalStorage {
    fun load(): JournalLoad
    fun read(id: String): JournalRecord?
    fun write(record: JournalRecord)
    fun remove(id: String)
}
