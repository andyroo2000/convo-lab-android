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
    val archive: JournalArchive? = null,
    val localWorkRoots: Set<String> = emptySet(),
) {
    val canEdit: Boolean get() = !busy && !loading && !pendingDelete
    val contentsRecords: List<JournalRecord> get() {
        val listing = archive ?: return records
        return records.filter { it.id in listing.entryIds || it.id in localWorkRoots }
    }
}

fun JournalRecord.displayTitle(): String = draft?.title ?: entry?.title ?: "Untitled entry"
fun JournalRecord.displayDate(): String = draft?.entryDate ?: entry!!.entryDate
fun JournalRecord.editableDraft(): JournalDraft = draft ?: JournalDraft.from(requireNotNull(entry))
