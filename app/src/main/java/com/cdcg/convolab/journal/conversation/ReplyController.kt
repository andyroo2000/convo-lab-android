package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.WorkflowController
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReplyState(val record: JournalRecord, val text: String, val editing: Boolean,
    val busy: Boolean = false, val error: String? = null, val saved: Boolean = true, val conflict: Boolean = false)

class ReplyController(initial: JournalRecord, private val repository: JournalRepository,
    val workflow: WorkflowController, private val scope: CoroutineScope, private val changed: suspend () -> Unit) {
    private val mutableState = MutableStateFlow(ReplyState(initial, initial.editableDraft().body, initial.draft != null || initial.entry == null))
    val state = mutableState.asStateFlow()
    private var draft = initial.editableDraft()
    private val writer = DraftWriter(repository) { editId, success ->
        if (editId == draft.editId) mutableState.update { it.copy(saved = success,
            error = if (success) it.error else "Your answer could not be saved on this device. Keep it open and retry.") }
    }.also { it.start(scope) }

    fun start() { if (!state.value.editing) state.value.record.entry?.let(workflow::open) }
    fun edit(text: String) {
        if (state.value.busy || state.value.record.pendingSave != null) return
        try {
            draft = draft.edited(text)
            workflow.cancel()
            mutableState.update { it.copy(text = text, editing = true, saved = false, error = null) }
            writer.enqueue(draft)
        } catch (_: IllegalArgumentException) { mutableState.update { it.copy(error = "Use up to 20,000 characters without null characters.") } }
    }
    fun beginEditing() {
        if (state.value.busy || workflow.state.value.busy) return
        workflow.cancel()
        mutableState.update { it.copy(editing = true) }
    }
    suspend fun flush() = writer.flush()
    fun retryLocal() = action { writer.flush() }

    fun submit() = action {
        writer.flush()
        if (repository.record(draft.key)?.draft == null) repository.edit(draft)
        val entry = repository.save(draft.key)
        val record = requireNotNull(repository.record(draft.key))
        draft = record.editableDraft()
        mutableState.update { it.copy(record = record, text = draft.body, editing = false, saved = true, conflict = false) }
        workflow.submit(entry)
        changed()
    }

    fun saveAsNew() = action {
        writer.flush()
        repository.saveAsNew(draft.key)
        mutableState.update { it.copy(error = "A new draft is saved in Contents. Your original answer is also kept.", conflict = false) }
    }
    fun discardUnsent() = action {
        writer.flush()
        check(state.value.record.entry == null) { "Only an unsent answer can be discarded here." }
        repository.delete(draft.key)
        workflow.cancel()
        changed()
    }

    suspend fun refreshRecord() {
        writer.flush()
        val record = repository.record(draft.key) ?: return
        draft = record.editableDraft()
        mutableState.update { it.copy(record = record, text = draft.body) }
        val entry = record.entry
        if (state.value.editing || entry == null) return
        if (workflow.state.value.entry != entry) workflow.open(entry)
    }
    private fun action(operation: suspend () -> Unit) = scope.launch {
        if (state.value.busy) return@launch
        mutableState.update { it.copy(busy = true, error = null) }
        try { operation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val record = repository.record(draft.key) ?: state.value.record
            val conflict = failure is ApiFailure && failure.status in setOf(404, 409)
            mutableState.update { it.copy(record = record, conflict = conflict,
                error = if (conflict) "This conversation changed. Your answer is kept; copy it or save it as a new entry." else failure.userMessage()) }
        } finally { mutableState.update { it.copy(busy = false) } }
    }
}
