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

    private val syncMessages = mapOf(
        400 to "This writing could not sync. Edit it or try Save again. It is kept on this device.",
        422 to "This writing could not sync. Edit it or try Save again. It is kept on this device.",
        403 to "This account cannot sync right now. Your writing is kept on this device; try Save after signing in again.")
    private var startupWarning: String? = null
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
            if (hasSyncWork(snapshot)) {
                automaticSync.synchronize()
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A failed local write must never be sent or discarded. */ }
    }

    private fun hasSyncWork(snapshot: NotebookState): Boolean {
        if (snapshot.records.any { it.isAutomaticSyncCandidate }) return true
        if (!snapshot.savedLocally || snapshot.draft.body.isBlank()) return false
        val recorded = snapshot.records.firstOrNull { it.id == snapshot.draft.id }
        return recorded?.draft?.editId != snapshot.draft.editId
    }

    fun start() = scope.launch {
        try {
            val local = repository.load()
            val records = sorted(local.records)
            val resume = records.firstOrNull { it.hasPendingWriting }
            mutableState.update { it.copy(records = records, draft = resume?.editableDraft() ?: it.draft,
                pendingDelete = resume?.pendingDelete ?: false,
                canRecoverCopy = resume?.syncRejection in setOf(404, 409),
                loading = false, savedLocally = resume != null, recoveryCount = local.recoveryFiles.size,
                archive = local.archive, nextCursor = local.archive?.nextCursor, localWorkRoots = local.localWorkRoots) }
            refresh()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { reportStartupFailure(failure) }
    }

    private fun reportStartupFailure(failure: Exception) {
        val restoredWriting = !state.value.loading && state.value.savedLocally
        report(failure)
        if (restoredWriting) startupWarning = state.value.error
        mutableState.update { it.copy(loading = false) }
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
        mutableState.update { it.copy(draft = record.editableDraft(), selectionRevision = it.selectionRevision + 1, contents = false, closePrompt = false, savedLocally = record.hasPendingWriting, pendingDelete = record.pendingDelete, canRecoverCopy = record.syncRejection in setOf(404, 409)) }
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
        mutableState.update { if (it.draft.id == id) it.copy(selectionRevision = it.selectionRevision + 1) else it }
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
            check(repository.record(draft.key)?.hasPendingWriting == true) { "Keep the entry open until it is saved on this device." }
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
        startupWarning = null
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
                savedLocally = localSaveStatus(current, record),
                error = syncError(current, record),
                canRecoverCopy = record.syncRejection in setOf(404, 409))
        }
    }

    private fun localSaveStatus(current: NotebookState, record: JournalRecord): Boolean {
        val pending = record.draft ?: record.pendingSave
        if (pending != null) return pending.editId == current.draft.editId || current.savedLocally
        return if (record.entry?.body == current.draft.body) false else current.savedLocally
    }

    private fun syncError(current: NotebookState, record: JournalRecord): String? {
        val acknowledged = !record.hasPendingWriting && record.entry?.body == current.draft.body
        if (acknowledged && current.error == startupWarning) return null
        if (current.error != null && current.error !in syncMessages.values) return current.error
        return syncMessages[record.syncRejection]
    }

    private fun writeResult(editId: String, success: Boolean) {
        mutableState.update {
            if (it.draft.editId != editId) it else it.copy(savedLocally = success,
                error = if (success) it.error else "Your writing could not be saved on this device. Keep this screen open and try again.")
        }
    }

    private fun report(failure: Exception, recoveryFor: String? = null) {
        startupWarning = null
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
