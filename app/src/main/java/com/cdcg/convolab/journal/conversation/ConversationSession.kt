package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConversationAccess(val api: ConversationGateway, val repository: JournalRepository,
    val workflow: (CoroutineScope) -> WorkflowController, val tutor: TutorController)
class ReplyBinding(val controller: ReplyController, val job: Job, var node: ConversationNode? = null)
data class ConversationState(val replies: List<ReplyBinding> = emptyList(), val composer: ReplyBinding? = null,
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null)

/** A root owns its child scopes and writers until navigation waits for their final local flush. */
class ConversationSession(val root: JournalEntry, private val rootWorkflow: WorkflowController,
    private val access: ConversationAccess, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(ConversationState())
    val state = mutableState.asStateFlow()
    private val bindings = linkedMapOf<String, ReplyBinding>()
    private var nodes = emptyList<ConversationNode>()
    private var leafWork: Job? = null
    private var leafId: String? = null
    private val updates = Mutex()
    private var pendingUpdates = 0

    fun start() {
        action {
            val local = access.repository.load().records.mapNotNull { it.entry }.filter { it.rootEntryId == root.id }
            nodes = local.sortedWith(compareBy<JournalEntry> { it.createdAt }.thenBy { it.id })
                .map { ConversationNode(it, null, null, null) }
            rebuild()
            refreshRemote()
        }
        scope.launch {
            access.tutor.state.map { it.question }.distinctUntilChanged().collect { question ->
                if (question?.tutor != null) prepareComposer(question) else clearComposer()
            }
        }
    }

    fun refresh() = action {
        flush()
        refreshRemote()
    }

    private suspend fun refreshRemote() {
        nodes = access.api.load(JournalId(root.id))
        nodes.forEach { access.repository.accept(it.entry) }
        rebuild()
    }

    suspend fun flush() { bindings.values.toList().forEach { it.controller.flush() } }
    suspend fun checkDeletion() {
        check(pendingUpdates == 0 && !access.tutor.state.value.busy && bindings.values.none { it.controller.state.value.busy || it.controller.workflow.state.value.busy }) {
            "Wait for this conversation to finish before deleting it."
        }
        flush() // JournalRepository's delete guard includes every unsent child draft.
    }

    private suspend fun rebuild() {
        val records = access.repository.load().records.filter { it.rootEntryId == root.id }
        val byId = records.associateBy { it.id }
        val removed = bindings.keys.filterNot(byId::containsKey)
        removed.forEach { bindings.remove(it)?.job?.cancelAndJoin() }
        val saved = nodes.mapNotNull { node -> byId[node.entry.id]?.let { binding(it, node) } }
        val unsent = records.filter { it.entry == null && it.draft?.body?.isNotBlank() == true }.map { binding(it, null) }
        val retained = state.value.composer?.takeIf { byId[it.controller.state.value.record.id]?.entry == null }
            ?.takeIf { byId.containsKey(it.controller.state.value.record.id) }
        mutableState.update { it.copy(replies = (saved + unsent).filterNot { reply -> reply === retained }, composer = retained, loading = false) }
        followLeaf(saved.lastOrNull()?.controller?.workflow ?: rootWorkflow)
        access.tutor.state.value.question?.let { prepareComposer(it) }
    }

    private suspend fun binding(record: JournalRecord, node: ConversationNode?): ReplyBinding {
        val existing = bindings[record.id]
        if (existing != null) { existing.node = node ?: existing.node; existing.controller.refreshRecord(); return existing }
        val job = SupervisorJob(scope.coroutineContext[Job])
        val childScope = CoroutineScope(scope.coroutineContext + job)
        val workflow = access.workflow(childScope)
        val reply = ReplyController(record, access.repository, workflow, childScope) { refresh().join() }
        return ReplyBinding(reply, job, node).also { bindings[record.id] = it; reply.start() }
    }

    private fun followLeaf(workflow: WorkflowController) {
        val id = workflow.state.value.entry?.id ?: root.id
        if (leafId == id) return
        leafId = id
        leafWork?.cancel()
        access.tutor.close()
        leafWork = scope.launch { access.tutor.follow(workflow) }
    }

    private suspend fun prepareComposer(question: TutorQuestion) {
        val source = access.tutor.state.value.source ?: return
        val origin = AnswerOrigin(source.entryId, JournalId(question.id))
        try {
            val record = access.repository.answerDraft(root, origin)
            if (record.entry != null) return // The acknowledged answer appears in the conversation list.
            val reply = binding(record, null)
            mutableState.update {
                if (access.tutor.state.value.source != source || access.tutor.state.value.question?.id != question.id) it
                else it.copy(composer = reply, replies = it.replies.filterNot { old -> old === reply })
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.update { it.copy(error = failure.userMessage()) } }
    }

    private fun clearComposer() {
        mutableState.update { current ->
            val previous = current.composer ?: return@update current
            val kept = if (previous.controller.state.value.text.isBlank()) current.replies else (current.replies + previous).distinct()
            current.copy(composer = null, replies = kept)
        }
    }

    private fun action(operation: suspend () -> Unit): Job {
        pendingUpdates++
        return scope.launch {
            try { updates.withLock { runUpdate(operation) } }
            finally { pendingUpdates-- }
        }
    }

    private suspend fun runUpdate(operation: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true, error = null) }
        try { operation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.update { it.copy(error = failure.userMessage()) } }
        finally { mutableState.update { it.copy(busy = false, loading = false) } }
    }
}
