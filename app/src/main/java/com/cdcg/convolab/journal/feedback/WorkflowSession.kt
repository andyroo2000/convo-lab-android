package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalEntry
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.network.ApiFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class WorkflowAccess(val api: FeedbackGateway, val storage: WorkflowStorage, val io: CoroutineDispatcher = Dispatchers.IO)

/** One entry/version, with durable answer and submission state before every retryable mutation. */
class WorkflowSession(
    val entry: JournalEntry,
    private val access: WorkflowAccess,
    private val publish: (WorkflowSnapshot) -> Unit,
) {
    var snapshot = WorkflowSnapshot(JournalId(entry.id), entry.version)
        private set

    suspend fun load() {
        val saved = withContext(access.io) { access.storage.read(snapshot.entryId) }
        if (saved.requiresRecovery(entry.version)) {
            throw WorkflowRecoveryFailure(WorkflowRecovery(saved))
        }
        snapshot = saved?.takeIf { it.sourceVersion == entry.version } ?: snapshot
        publish(snapshot)
    }

    suspend fun archiveOldWork() {
        withContext(access.io + NonCancellable) { access.storage.archive(snapshot.entryId) }
        snapshot = WorkflowSnapshot(snapshot.entryId, entry.version)
        publish(snapshot)
    }

    suspend fun begin() {
        checkpoint(snapshot.copy(submissionRequested = true))
        resume()
    }

    suspend fun resume() {
        val analysis = snapshot.analysis
        if (analysis?.status == WorkStatus.Ready) { checkpoint(snapshot.copy(submissionRequested = false)); return }
        if (analysis?.status?.pending == true) { receiveAnalysis(analysis); return }
        receiveClarification(clarificationForResume())
    }

    private suspend fun clarificationForResume(): Clarification {
        snapshot.pendingAnswer?.let { return access.api.answer(entry, it) }
        snapshot.clarification?.takeIf { it.status == WorkStatus.Resolved }?.let { return it }
        return access.api.startClarification(entry)
    }

    suspend fun answer(text: String) {
        val value = text.trim()
        require(value.isNotBlank() && value.codePointCount(0, value.length) <= 2_000 && !value.contains('\u0000'))
        val clarification = requireNotNull(snapshot.clarification)
        val question = requireNotNull(clarification.question)
        check(clarification.status == WorkStatus.Waiting)
        val pending = ClarificationAnswer(clarification.id, question.id, value)
        checkpoint(snapshot.copy(pendingAnswer = pending, submissionRequested = true))
        receiveClarification(access.api.answer(entry, pending))
    }

    suspend fun refresh() {
        if (snapshot.submissionRequested) { resume(); return }
        val response = access.api.analysis(entry)
        checkVersion(response.entry.version)
        val analysis = response.analysis ?: return
        checkVersion(analysis.sourceVersion)
        checkpoint(snapshot.copy(analysis = analysis))
        if (analysis.status.pending) receiveAnalysis(analysis)
    }

    suspend fun editFinal(edit: FinalEdit) {
        checkpoint(snapshot.copy(pendingFinalEdit = edit))
        val analysis = try { access.api.editFinal(entry, edit) }
        catch (failure: ApiFailure) { reconcileFinalEdit(edit, failure) }
        checkVersion(analysis.sourceVersion)
        checkpoint(snapshot.copy(analysis = analysis, pendingFinalEdit = null))
    }

    suspend fun discardFinalEdit() { checkpoint(snapshot.copy(pendingFinalEdit = null)) }

    private suspend fun reconcileFinalEdit(edit: FinalEdit, failure: ApiFailure): Analysis {
        if (failure.status != 409) throw failure
        val response = access.api.analysis(entry)
        checkVersion(response.entry.version)
        val analysis = response.analysis ?: throw failure
        val alreadyApplied = analysis.id == edit.analysisId && analysis.finalText == edit.text
        if (!alreadyApplied) throw failure
        return analysis
    }

    private suspend fun receiveClarification(first: Clarification) {
        var current = first
        val budget = JournalPollBudget()
        while (true) {
            checkVersion(current.sourceVersion)
            checkpoint(snapshot.copy(clarification = current, pendingAnswer = null))
            if (finishClarification(current)) return
            budget.pause(1_500)
            current = JournalPolling.read { access.api.clarification(entry) }
                ?: throw ApiFailure(409)
        }
    }

    private suspend fun finishClarification(current: Clarification): Boolean {
        if (current.status == WorkStatus.Failed) throw WorkflowFailure(current.error ?: "Could not check your meaning. Please retry.")
        if (current.status == WorkStatus.Resolved) receiveAnalysis(access.api.submit(entry, current.id))
        return !current.status.pending
    }

    private suspend fun receiveAnalysis(first: Analysis) {
        var current = first
        val budget = JournalPollBudget()
        while (true) {
            checkVersion(current.sourceVersion)
            checkpoint(snapshot.copy(analysis = current, submissionRequested = current.status != WorkStatus.Ready))
            if (current.status == WorkStatus.Failed) throw WorkflowFailure(current.error ?: "Could not prepare feedback. Please retry.")
            if (!current.status.pending) return
            budget.pause(2_000)
            val response = JournalPolling.read { access.api.analysis(entry) }
            checkVersion(response.entry.version)
            current = response.analysis ?: throw WorkflowFailure("Feedback is not available yet. Please retry.")
        }
    }

    private fun checkVersion(version: Int) { if (version != entry.version) throw ApiFailure(409) }

    private suspend fun checkpoint(next: WorkflowSnapshot) = withContext(NonCancellable) {
        withContext(access.io) { access.storage.write(next) }
        snapshot = next
        publish(next)
    }
}

private fun WorkflowSnapshot.hasPendingWriting() = pendingAnswer != null || pendingFinalEdit != null

private fun WorkflowSnapshot?.requiresRecovery(version: Int): Boolean {
    if (this == null) return false
    return sourceVersion != version && hasPendingWriting()
}
