package com.cdcg.convolab.journal

/** Server list membership is separate from the durable copies used for offline writing. */
data class JournalArchive(val entryIds: Set<String>, val nextCursor: String?) {
    fun append(page: JournalPage) = JournalArchive(entryIds + page.entries.map { it.id }, page.nextCursor)
    fun including(entry: JournalEntry): JournalArchive =
        if (entry.rootEntryId == null) copy(entryIds = entryIds + entry.id) else this

    companion object {
        fun from(page: JournalPage) = JournalArchive(page.entries.map { it.id }.toSet(), page.nextCursor)
    }
}

interface JournalArchiveStorage {
    fun read(): JournalArchive?
    fun write(archive: JournalArchive)
}

class MemoryJournalArchive : JournalArchiveStorage {
    private var archive: JournalArchive? = null
    override fun read() = archive
    override fun write(archive: JournalArchive) { this.archive = archive }
}

class JournalLocalWork(private val feedbackPending: (JournalId) -> Boolean = { false }) {
    fun roots(local: JournalLoad): Set<String> {
        // A damaged child record may belong to any conversation; keep its parent reachable.
        val retained = if (local.recoveryFiles.isEmpty()) local.records.filter(::needsRecovery) else local.records
        return retained.map { it.rootEntryId ?: it.id }.toSet()
    }

    private fun needsRecovery(record: JournalRecord): Boolean {
        if (record.draft != null) return true
        if (record.pendingSave != null || record.pendingDelete) return true
        // An unreadable workflow must remain reachable for recovery, too.
        return try { feedbackPending(JournalId(record.id)) } catch (_: Exception) { true }
    }
}
