package com.cdcg.convolab.journal

data class NotebookState(
    val draft: JournalDraft = JournalDraft.blank(),
    val records: List<JournalRecord> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val savedLocally: Boolean = false,
    val nextCursor: String? = null,
    val recoveryCount: Int = 0,
    val contents: Boolean = false,
    val closePrompt: Boolean = false,
    val pendingDelete: Boolean = false,
    val canRecoverCopy: Boolean = false,
    val syncingId: String? = null,
    val archive: JournalArchive? = null,
    val localWorkRoots: Set<String> = emptySet(),
) {
    val syncStatus: String? get() = when {
        syncingId == draft.id -> "Syncing…"
        savedLocally -> "Saved on this device"
        records.any { it.id == draft.id && it.entry?.body == draft.body && it.entry.version == draft.expectedVersion } -> "Synced"
        else -> null
    }
    val canEdit: Boolean get() = !busy && !loading && !pendingDelete
    val contentsRecords: List<JournalRecord> get() {
        val listing = archive ?: return records
        return records.filter { it.id in listing.entryIds || it.id in localWorkRoots }
    }
}

fun JournalRecord.displayTitle(): String = draft?.title ?: entry?.title ?: "Untitled entry"
fun JournalRecord.displayDate(): String = draft?.entryDate ?: entry!!.entryDate
fun JournalRecord.editableDraft(): JournalDraft = draft ?: pendingSave ?: JournalDraft.from(requireNotNull(entry))
