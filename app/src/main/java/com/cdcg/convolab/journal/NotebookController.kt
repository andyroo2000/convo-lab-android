package com.cdcg.convolab.journal

import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** One controller belongs to one account; network callbacks never share another account's state. */
class NotebookController(private val repository: JournalRepository, private val scope: CoroutineScope,
    private val beforeDelete: suspend (JournalId) -> Unit = {}) {
    private val mutableState = MutableStateFlow(NotebookState())
    val state = mutableState.asStateFlow()
    private val writer = DraftWriter(repository, ::writeResult).also { it.start(scope) }

    private var syncJob: Job? = null
    private val automaticSync = JournalAutomaticSync(repository, ::syncChanged)

    fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                if (!state.value.loading && !state.value.busy) synchronize()
                delay(3000)
            }
        }
    }

    fun stopSync() { syncJob?.cancel(); syncJob = null }
    fun reconnect() = scope.launch {
        if (syncJob?.isActive != true) return@launch
        automaticSync.reconnect()
        if (!state.value.loading && !state.value.busy) synchronize()
    }

    suspend fun synchronize() {
        try {
            writer.flush()
            val snapshot = state.value
            if (snapshot.savedLocally || snapshot.records.any { it.hasPendingWriting }) {
                automaticSync.synchronize()
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A failed local write must never be sent or discarded. */ }
    }

    fun start() = scope.launch {
        try {
            val local = repository.load()
            val records = sorted(local.records)
            val resume = records.firstOrNull { it.hasPendingWriting }
            mutableState.update { it.copy(records = records, draft = resume?.editableDraft() ?: it.draft,
                pendingDelete = resume?.pendingDelete ?: false,
                loading = false, savedLocally = resume != null, recoveryCount = local.recoveryFiles.size,
                archive = local.archive, nextCursor = local.archive?.nextCursor, localWorkRoots = local.localWorkRoots) }
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
    fun refreshVisibleContents() { if (state.value.contents && !state.value.loading) refreshContents() }
    fun more() = action { refresh(state.value.nextCursor) }

    fun newEntry() = action {
        writer.flush()
        mutableState.update { it.copy(draft = JournalDraft.blank(), contents = false, closePrompt = false, savedLocally = false, pendingDelete = false, canRecoverCopy = false) }
    }

    fun select(selected: JournalRecord) = action {
        val id = selected.id
        writer.flush()
        val record = requireNotNull(repository.record(JournalId(id)))
        mutableState.update { it.copy(draft = record.editableDraft(), contents = false, closePrompt = false, savedLocally = record.hasPendingWriting, pendingDelete = record.pendingDelete, canRecoverCopy = record.syncRejection in setOf(404, 409)) }
    }

    fun keepAndClose() = action(recoveryFor = state.value.draft.id) {
        writer.flush()
        val draft = state.value.draft
        persistForClose(draft)
        reload()
        mutableState.update { it.copy(draft = JournalDraft.blank(), closePrompt = false, savedLocally = false, pendingDelete = false, canRecoverCopy = false) }
    }

    fun save(onSaved: (JournalEntry) -> Unit = {}) = action(recoveryFor = state.value.draft.id) {
        writer.flush()
        val sent = state.value.draft
        val saved = saveLatest(sent)
        reloadCurrent(sent)
        onSaved(saved)
    }

    private suspend fun saveLatest(draft: JournalDraft): JournalEntry {
        repeat(3) {
            val entry = repository.save(draft.key)
            if (repository.record(draft.key)?.draft == null) return entry
        }
        error("Writing is still changing. It is kept on this device; try Save again.")
    }

    fun rename(selected: JournalRecord, title: String) = action(recoveryFor = selected.id) {
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
        beforeDelete(JournalId(id))
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
        if (draft.body.isNotBlank()) {
            saveOrQueue(draft)
            return
        }
        val current = repository.record(draft.key)
        check(current?.entry == null) { "A saved entry cannot be empty. Keep writing or delete the entry." }
        if (current != null) repository.delete(draft.key)
    }

    private suspend fun saveOrQueue(draft: JournalDraft) {
        try { repository.save(draft.key) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: java.io.IOException) {
            if (failure is ApiFailure && failure.status in setOf(400, 401, 403, 404, 409, 422)) throw failure
            check(repository.record(draft.key)?.draft != null) { "Keep the entry open until it is saved on this device." }
        }
    }

    private suspend fun refresh(cursor: String? = null) {
        repository.refresh(cursor)
        reload()
    }

    private suspend fun reloadCurrent(source: JournalDraft) {
        val id = source.id
        reload()
        val record = requireNotNull(repository.record(JournalId(id)))
        if (state.value.draft.id == id) mutableState.update { it.copy(draft = record.editableDraft(), savedLocally = record.hasPendingWriting) }
    }

    private suspend fun reload() {
        val local = repository.load()
        mutableState.update { it.copy(records = sorted(local.records), recoveryCount = local.recoveryFiles.size,
            archive = local.archive, nextCursor = local.archive?.nextCursor, localWorkRoots = local.localWorkRoots) }
    }

    private fun action(recoveryFor: String? = null, block: suspend () -> Unit) = scope.launch {
        if (state.value.busy) return@launch
        mutableState.update { it.copy(busy = true, error = null) }
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure, recoveryFor) }
        finally { mutableState.update { it.copy(busy = false) } }
    }

    private suspend fun syncChanged(id: String?) {
        mutableState.update { it.copy(syncingId = id) }
        if (id != null) return
        reload()
        val record = repository.record(state.value.draft.key) ?: return
        mutableState.update { current ->
            if (current.draft.id != record.id) current else current.copy(
                draft = current.draft.copy(expectedVersion = maxOf(current.draft.expectedVersion, record.acknowledgedVersion)),
                savedLocally = record.hasPendingWriting,
                canRecoverCopy = record.syncRejection in setOf(404, 409))
        }
    }

    private fun writeResult(editId: String, success: Boolean) {
        mutableState.update {
            if (it.draft.editId != editId) it else it.copy(savedLocally = success,
                error = if (success) it.error else "Your writing could not be saved on this device. Keep this screen open and try again.")
        }
    }

    private fun report(failure: Exception, recoveryFor: String? = null) {
        val conflict = failure is ApiFailure && failure.status in setOf(404, 409)
        val recover = conflict && recoveryFor == state.value.draft.id
        val message = when {
            recover -> "This entry changed elsewhere. Your writing is kept here. You can save it as a new entry."
            failure is IllegalStateException -> failure.message ?: "Your draft is kept. Please try again."
            else -> failure.userMessage()
        }
        mutableState.update { it.copy(error = message, canRecoverCopy = recover) }
    }

    private fun sorted(records: List<JournalRecord>) = records.filter { it.rootEntryId == null }
        .sortedWith(compareByDescending<JournalRecord> { it.displayDate() }.thenByDescending { it.id })
}
