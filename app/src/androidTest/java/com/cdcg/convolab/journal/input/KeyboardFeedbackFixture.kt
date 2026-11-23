package com.cdcg.convolab.journal.input

import com.cdcg.convolab.journal.JournalEntry
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.feedback.*
import kotlinx.coroutines.CompletableDeferred

/** Holds a feedback lookup open so even a brief accidental editor replacement is observable. */
internal class KeyboardFeedbackFixture : FeedbackGateway, WorkflowStorage {
    var reads = 0
    val response = CompletableDeferred<Unit>()
    override suspend fun analysis(entry: JournalEntry): AnalysisResponse {
        reads++
        response.await()
        return AnalysisResponse(entry, null)
    }
    override suspend fun startClarification(entry: JournalEntry): Clarification = error("Unexpected submission")
    override suspend fun clarification(entry: JournalEntry): Clarification = error("Unexpected poll")
    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification = error("Unexpected answer")
    override suspend fun submit(entry: JournalEntry, clarificationId: String): Analysis = error("Unexpected submission")
    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis = error("Unexpected edit")
    override fun read(id: JournalId): WorkflowSnapshot? = null
    override fun write(snapshot: WorkflowSnapshot) = Unit
    override fun archive(id: JournalId) = Unit
}
