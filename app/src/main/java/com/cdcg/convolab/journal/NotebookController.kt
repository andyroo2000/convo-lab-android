package com.cdcg.convolab.journal

import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One controller belongs to one account; network callbacks never share another account's state. */
class NotebookController(private val repository: JournalRepository, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(NotebookState())
    val state = mutableState.asStateFlow()
    private val writer = DraftWriter(repository, ::writeResult).also { it.start(scope) }

    fun start() = scope.launch {
        try {
            val local = repository.load()
            val records = sorted(local.records)
            val resume = records.firstOrNull { it.draft != null }
            mutableState.update { it.copy(records = records, draft = resume?.draft ?: it.draft,
                pendingDelete = resume?.pendingDelete ?: false,
                loading = false, savedLocally = resume != null, recoveryCount = local.recoveryFiles.size) }
            refresh()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure); mutableState.update { it.copy(loading = false) } }
    }

    fun edit(text: String) {
        if (!state.value.canEdit) return
        val previous = state.value.draft
        if (text == previous.body) return
        try {
            val draft = previous.edited(text)
            mutableState.update { it.copy(draft = draft, savedLocally = false, error = null) }
            writer.enqueue(draft)
        } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(error = "An entry can contain up to 20,000 characters.") }
        }
    }

    fun showContents(show: Boolean) {
        mutableState.update { it.copy(contents = show) }
        if (show) action { writer.flush(); reload(); refresh() }
    }

    fun requestClose() { mutableState.update { it.copy(closePrompt = true) } }
    fun dismissClose() { mutableState.update { it.copy(closePrompt = false) } }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun flush() = scope.launch {
        try { writer.flush() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure) }
    }
    fun refreshContents() = action { writer.flush(); refresh() }
    fun more() = action { refresh(state.value.nextCursor) }

    fun newEntry() = action {
        writer.flush()
        mutableState.update { it.copy(draft = JournalDraft.blank(), contents = false, closePrompt = false, savedLocally = false, pendingDelete = false, canRecoverCopy = false) }
    }

    fun select(selected: JournalRecord) = action {
        val id = selected.id
        writer.flush()
        val record = requireNotNull(repository.record(JournalId(id)))
        mutableState.update { it.copy(draft = record.editableDraft(), contents = false, closePrompt = false, savedLocally = true, pendingDelete = record.pendingDelete, canRecoverCopy = false) }
    }

    fun keepAndClose() = action {
        writer.flush()
        val draft = state.value.draft
        persistForClose(draft)
        reload()
        mutableState.update { it.copy(draft = JournalDraft.blank(), closePrompt = false, savedLocally = false, pendingDelete = false, canRecoverCopy = false) }
    }

    fun save(onSaved: (JournalEntry) -> Unit = {}) = action {
        writer.flush()
        val sent = state.value.draft
        repository.save(sent.key)
        reloadCurrent(sent)
        val saved = requireNotNull(repository.record(sent.key)?.entry)
        onSaved(saved)
    }

    fun rename(selected: JournalRecord, title: String) = action {
        val id = selected.id
        writer.flush()
        val current = requireNotNull(repository.record(JournalId(id))).editableDraft()
        val renamed = current.edited(current.body, title)
        repository.edit(renamed)
        if (state.value.draft.id == id) mutableState.update { it.copy(draft = renamed, savedLocally = true) }
        reload()
        if (renamed.body.isNotBlank()) repository.save(JournalId(id))
        reloadCurrent(renamed)
    }

    fun delete(selected: JournalDraft) = action {
        val id = selected.id
        writer.flush()
        val record = repository.record(JournalId(id))
        if (record != null) {
            try { repository.delete(JournalId(id)) }
            finally {
                val pending = repository.record(JournalId(id))?.pendingDelete ?: false
                if (state.value.draft.id == id) mutableState.update { it.copy(pendingDelete = pending) }
            }
        }
        reload()
        if (state.value.draft.id == id) mutableState.update {
            it.copy(draft = JournalDraft.blank(), closePrompt = false, savedLocally = false, pendingDelete = false, canRecoverCopy = false)
        }
    }

    fun recoverCopy() = action {
        writer.flush()
        val copy = repository.saveAsNew(state.value.draft.key)
        mutableState.update { it.copy(draft = copy, error = null, savedLocally = true, pendingDelete = false, canRecoverCopy = false) }
        reload()
    }

    private suspend fun persistForClose(draft: JournalDraft) {
        if (draft.body.isNotBlank()) { repository.save(draft.key); return }
        val current = repository.record(draft.key)
        check(current?.entry == null) { "A saved entry cannot be empty. Keep writing or delete the entry." }
        if (current != null) repository.delete(draft.key)
    }

    private suspend fun refresh(cursor: String? = null) {
        val page = repository.refresh(cursor)
        reload()
        mutableState.update { it.copy(nextCursor = page.nextCursor) }
    }

    private suspend fun reloadCurrent(source: JournalDraft) {
        val id = source.id
        reload()
        val record = requireNotNull(repository.record(JournalId(id)))
        if (state.value.draft.id == id) mutableState.update { it.copy(draft = record.editableDraft(), savedLocally = true) }
    }

    private suspend fun reload() {
        val local = repository.load()
        mutableState.update { it.copy(records = sorted(local.records), recoveryCount = local.recoveryFiles.size) }
    }

    private fun action(block: suspend () -> Unit) = scope.launch {
        if (state.value.busy) return@launch
        mutableState.update { it.copy(busy = true, error = null) }
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure) }
        finally { mutableState.update { it.copy(busy = false) } }
    }

    private fun writeResult(editId: String, success: Boolean) {
        mutableState.update {
            if (it.draft.editId != editId) it else it.copy(savedLocally = success,
                error = if (success) it.error else "Your writing could not be saved on this device. Keep this screen open and try again.")
        }
    }

    private fun report(failure: Exception) {
        val message = when {
            failure is ApiFailure && failure.status in setOf(404, 409) -> "This entry changed elsewhere. Your writing is kept here. You can save it as a new entry."
            failure is IllegalStateException -> failure.message ?: "Your draft is kept. Please try again."
            else -> failure.userMessage()
        }
        mutableState.update { it.copy(error = message, canRecoverCopy = failure is ApiFailure && failure.status in setOf(404, 409)) }
    }

    private fun sorted(records: List<JournalRecord>) = records.filter { it.rootEntryId == null }
        .sortedWith(compareByDescending<JournalRecord> { it.displayDate() }.thenByDescending { it.id })
}
