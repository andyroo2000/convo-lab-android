package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ConversationAccess(val api: ConversationGateway, val repository: JournalRepository,
    val workflow: (CoroutineScope) -> WorkflowController, val tutor: TutorController)
class ReplyBinding(val controller: ReplyController, var node: ConversationNode? = null)
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
                if (question?.tutor != null) prepareComposer(question)
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
        check(!state.value.busy && !access.tutor.state.value.busy && bindings.values.none { it.controller.state.value.busy || it.controller.workflow.state.value.busy }) {
            "Wait for this conversation to finish before deleting it."
        }
        flush() // JournalRepository's delete guard includes every unsent child draft.
    }

    private suspend fun rebuild() {
        val records = access.repository.load().records.filter { it.rootEntryId == root.id }
        val byId = records.associateBy { it.id }
        val saved = nodes.mapNotNull { node -> byId[node.entry.id]?.let { binding(it, node) } }
        val unsent = records.filter { it.entry == null && it.draft?.body?.isNotBlank() == true }.map { binding(it, null) }
        mutableState.update { it.copy(replies = saved + unsent, composer = null, loading = false) }
        followLeaf(saved.lastOrNull()?.controller?.workflow ?: rootWorkflow)
        access.tutor.state.value.question?.let { prepareComposer(it) }
    }

    private suspend fun binding(record: JournalRecord, node: ConversationNode?): ReplyBinding {
        val existing = bindings[record.id]
        if (existing != null) { existing.node = node ?: existing.node; existing.controller.refreshRecord(); return existing }
        val workflow = access.workflow(scope)
        val reply = ReplyController(record, access.repository, workflow, scope) { refresh().join() }
        return ReplyBinding(reply, node).also { bindings[record.id] = it; reply.start() }
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
            if (access.tutor.state.value.source != source || access.tutor.state.value.question?.id != question.id) return
            mutableState.update { it.copy(composer = reply, replies = it.replies.filterNot { old -> old === reply }) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.update { it.copy(error = failure.userMessage()) } }
    }

    private fun action(operation: suspend () -> Unit) = scope.launch {
        if (state.value.busy) return@launch
        mutableState.update { it.copy(busy = true, error = null) }
        try { operation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.update { it.copy(error = failure.userMessage()) } }
        finally { mutableState.update { it.copy(busy = false, loading = false) } }
    }
}
