package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.feedback.WorkStatus
import com.cdcg.convolab.journal.feedback.WorkflowController
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TutorState(val source: QuestionSource? = null, val question: TutorQuestion? = null,
    val preferences: TutorPreferences? = null, val choosing: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val notice: String? = null)

/** The question owns its historical tutor. Preference changes affect future questions. */
class TutorController(private val api: TutorGateway, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(TutorState())
    val state = mutableState.asStateFlow()
    private val mutableWaitingTutor = MutableStateFlow<Tutor?>(null)
    val waitingTutor = mutableWaitingTutor.asStateFlow()
    private var preferenceRevision = 0L
    private var work: Job? = null

    fun loadWaitingTutor() {
        val revision = preferenceRevision
        scope.launch {
            try {
                val preferences = api.preferences()
                if (preferenceRevision == revision) rememberTutor(preferences)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Artwork is optional; submission remains available. */ }
        }
    }

    private fun rememberTutor(preferences: TutorPreferences) {
        preferenceRevision++
        mutableWaitingTutor.value = preferences.tutors.firstOrNull { it.id == preferences.selectedId } ?: preferences.tutors.firstOrNull()
    }

    private var generation = 0L

    suspend fun follow(workflow: WorkflowController) {
        workflow.state.map { current ->
            val entry = current.entry
            val analysis = current.snapshot?.analysis
            if (entry == null || analysis?.status != WorkStatus.Ready) null
            else QuestionSource(JournalId(entry.id), JournalId(analysis.id), analysis.currentReplyId?.let(::JournalId))
        }.distinctUntilChanged().collect { source -> if (source == null) close() else open(source) }
    }

    fun open(source: QuestionSource) {
        work?.cancel()
        generation++
        mutableState.value = TutorState(source = source)
        reload()
    }
    fun close() { generation++; work?.cancel(); mutableState.value = TutorState() }
    fun reload() = action { source, epoch ->
        val question = source?.let { api.prepare(it) } ?: return@action
        publish(epoch) { it.copy(question = question) }
        if (question.tutor == null) loadPicker(epoch)
    }
    fun showPicker() = action { _, epoch -> loadPicker(epoch) }
    fun dismissPicker() {
        generation++
        work?.cancel()
        mutableState.update { it.copy(choosing = false, busy = false, error = null) }
    }
    fun retry() { if (state.value.choosing) showPicker() else reload() }

    fun choose(tutor: Tutor) = action { source, epoch ->
        val preferences = api.choose(tutor)
        rememberTutor(preferences)
        publish(epoch) { it.copy(preferences = preferences, choosing = false,
            notice = "${tutor.name} will teach future questions. Earlier questions keep their original tutor.") }
        if (source != null && state.value.question?.tutor == null) {
            val question = api.prepare(source)
            publish(epoch) { it.copy(question = question) }
        }
    }

    private suspend fun loadPicker(epoch: Long) {
        publish(epoch) { it.copy(choosing = true) }
        val preferences = api.preferences()
        rememberTutor(preferences)
        publish(epoch) { it.copy(preferences = preferences) }
    }

    private fun action(operation: suspend (QuestionSource?, Long) -> Unit) {
        val source = state.value.source
        if (state.value.busy) return
        val epoch = generation
        mutableState.update { it.copy(busy = true, error = null, notice = null) }
        work = scope.launch {
            try { operation(source, epoch) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { publish(epoch) { it.copy(error = failure.userMessage()) } }
            finally { publish(epoch) { it.copy(busy = false) } }
        }
    }
    private fun publish(epoch: Long, change: (TutorState) -> TutorState) {
        if (epoch == generation) mutableState.update(change)
    }
}
