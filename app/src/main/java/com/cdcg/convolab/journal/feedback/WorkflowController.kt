package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalEntry
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Final editing keeps the revision captured when the editor opens, including across refresh/failure.
data class WorkflowState(
    val entry: JournalEntry? = null, val snapshot: WorkflowSnapshot? = null, val busy: Boolean = false,
    val error: String? = null, val canRetry: Boolean = true, val finalEdit: FinalEdit? = null,
    val recovery: WorkflowRecovery? = null,
)

class WorkflowController(private val access: WorkflowAccess, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(WorkflowState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var requestedSubmission = false
    private var work: Job? = null
    private var session: WorkflowSession? = null

    fun open(entry: JournalEntry) = replace(entry, submit = false)
    fun submit(entry: JournalEntry) = replace(entry, submit = true)

    fun cancel() {
        generation++
        work?.cancel()
        session = null
        mutableState.value = WorkflowState()
    }

    fun retry() = request {
        if (it.snapshot.submissionRequested) it.resume() else it.refresh()
    }
    fun recoverArchivedWork() {
        val epoch = generation
        val submit = requestedSubmission
        request(requireRetry = false) {
            it.archiveOldWork()
            if (generation != epoch) return@request
            mutableState.update { current -> current.copy(recovery = null, canRetry = true, error = null) }
            if (submit) it.begin() else it.refresh()
        }
    }

    fun answer(text: String) = request { it.answer(text) }

    fun beginFinalEdit() {
        val analysis = state.value.snapshot?.analysis ?: return
        val text = analysis.finalText ?: return
        mutableState.update { it.copy(finalEdit = FinalEdit(analysis.id, analysis.replyRevision, text), error = null) }
    }

    fun changeFinalText(text: String) {
        if (state.value.busy) return
        if (text.length > 30_000 || text.contains('\u0000')) return
        mutableState.update { it.copy(finalEdit = it.finalEdit?.copy(text = text)) }
    }

    fun dismissFinalEdit() {
        val epoch = generation
        request(requireRetry = false) {
            it.discardFinalEdit()
            if (generation == epoch) mutableState.update { current -> current.copy(finalEdit = null, error = null, canRetry = true) }
        }
    }

    fun saveFinalEdit() {
        val edit = state.value.finalEdit ?: return
        if (edit.text.isBlank()) return
        val epoch = generation
        request {
            it.editFinal(edit)
            if (generation == epoch) mutableState.update { current -> current.copy(finalEdit = null) }
        }
    }

    private fun replace(entry: JournalEntry, submit: Boolean) {
        requestedSubmission = submit
        val previous = work
        previous?.cancel()
        val epoch = ++generation
        mutableState.value = WorkflowState(entry = entry, busy = true)
        work = scope.launch {
            previous?.join()
            val current = WorkflowSession(entry, access) { snapshot ->
                if (generation == epoch) mutableState.update { it.copy(snapshot = snapshot, finalEdit = it.finalEdit ?: snapshot.pendingFinalEdit) }
            }
            session = current
            run(epoch) { current.load(); if (submit) current.begin() else current.refresh() }
        }
    }

    private fun request(requireRetry: Boolean = true, action: suspend (WorkflowSession) -> Unit) {
        if (state.value.busy) return
        if (requireRetry && !state.value.canRetry) return
        val current = session ?: return
        val epoch = generation
        mutableState.update { it.copy(busy = true, error = null) }
        work = scope.launch { run(epoch) { action(current) } }
    }

    private suspend fun run(epoch: Long, action: suspend () -> Unit) {
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (generation == epoch) report(failure)
        } finally {
            if (generation == epoch) mutableState.update { it.copy(busy = false) }
        }
    }

    private fun report(failure: Exception) {
        if (failure is WorkflowRecoveryFailure) {
            mutableState.update { it.copy(recovery = failure.recovery, error = null, canRetry = false) }
            return
        }
        val retry = failure !is ApiFailure || failure.status !in setOf(401, 409)
        val message = when {
            failure is WorkflowFailure -> failure.message.orEmpty()
            failure is ApiFailure && failure.status == 409 -> "This entry changed. Your writing is kept; return to the draft and submit again."
            failure is IllegalArgumentException -> "Please check the text and try again."
            else -> failure.userMessage()
        }
        mutableState.update { it.copy(error = message, canRetry = retry) }
    }
}
