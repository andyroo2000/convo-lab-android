package com.cdcg.convolab.journal

import kotlinx.coroutines.job
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.journal.feedback.WorkflowController
import com.cdcg.convolab.journal.conversation.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Retained by the activity across rotation; account transitions await the previous writer's flush. */
class NotebookViewModel(sessions: SessionController,
    workflow: ((Session, CoroutineScope) -> WorkflowController)? = null,
    tutor: ((Session, CoroutineScope) -> TutorController)? = null,
    conversation: ((Session, JournalRepository, TutorController) -> ConversationController)? = null,
    repository: (Session) -> JournalRepository) : ViewModel() {
    private val mutableBinding = MutableStateFlow<NotebookBinding?>(null)
    val binding = mutableBinding.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.state.map { if (it.account == null) null else sessions.snapshot() }
                .distinctUntilChanged { previous, next -> previous?.token == next?.token }
                .onEach { mutableBinding.value = null }
                .collectLatest { session ->
                    if (session != null) coroutineScope {
                        if (!sessions.work.own(session.account.id, coroutineContext.job)) return@coroutineScope
                        val journal = repository(session)
                        val feedback = workflow?.invoke(session, this)
                        val teacher = tutor?.invoke(session, this)
                        val replies = teacher?.let { conversation?.invoke(session, journal, it) }
                        val notebook = NotebookController(journal, this) { replies?.beforeDelete(it) }
                        mutableBinding.value = NotebookBinding(session.account.id, notebook, feedback, teacher, replies)
                        notebook.start()
                        if (feedback != null) {
                            launch { followEntry(notebook, feedback) }
                            if (replies != null) launch { replies.follow(feedback) }
                            else if (teacher != null) launch { teacher.follow(feedback) }
                        }
                        awaitCancellation()
                    }
                }
        }
    }

    private suspend fun followEntry(notebook: NotebookController, feedback: WorkflowController) {
        notebook.state.map { it.savedEntry() }.distinctUntilChanged().collect { entry ->
            if (entry == null) feedback.cancel()
            else if (feedback.state.value.entry != entry) feedback.open(entry)
        }
    }
}

class NotebookBinding(val accountId: Long, val controller: NotebookController, val workflow: WorkflowController? = null, val tutor: TutorController? = null, val conversation: ConversationController? = null)

private fun NotebookState.savedEntry(): JournalEntry? = records.firstOrNull { it.id == draft.id }?.entry?.takeIf {
    !pendingDelete && it.version == draft.expectedVersion && it.body == draft.body && it.title == draft.title
}
