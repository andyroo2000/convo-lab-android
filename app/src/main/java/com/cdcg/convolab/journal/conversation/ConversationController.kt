package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.feedback.WorkStatus
import com.cdcg.convolab.journal.feedback.WorkflowController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ConversationController(private val access: ConversationAccess) {
    private val mutableSession = MutableStateFlow<ConversationSession?>(null)
    val session = mutableSession.asStateFlow()

    suspend fun follow(root: WorkflowController) {
        root.state.map { state -> state.entry?.takeIf { state.readyForConversation } }
            .distinctUntilChanged().collectLatest { entry ->
                mutableSession.value = null
                access.tutor.close()
                if (entry != null) coroutineScope {
                    val conversation = ConversationSession(entry, root, access, this)
                    mutableSession.value = conversation
                    conversation.start()
                    try { awaitCancellation() }
                    finally { mutableSession.value = null; access.tutor.close() }
                }
            }
    }

    suspend fun beforeDelete(id: JournalId) { session.value?.takeIf { it.root.id == id.value }?.checkDeletion() }
    suspend fun flush() { session.value?.flush() }
}
