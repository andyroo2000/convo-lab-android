package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalId

data class WorkflowSnapshot(
    val entryId: JournalId, val sourceVersion: Int, val clarification: Clarification? = null,
    val analysis: Analysis? = null, val pendingAnswer: ClarificationAnswer? = null,
    val submissionRequested: Boolean = false, val pendingFinalEdit: FinalEdit? = null,
)

interface WorkflowStorage {
    fun read(id: JournalId): WorkflowSnapshot?
    fun write(snapshot: WorkflowSnapshot)
}
