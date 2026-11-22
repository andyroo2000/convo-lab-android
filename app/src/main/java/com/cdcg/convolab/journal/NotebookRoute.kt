package com.cdcg.convolab.journal

import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.network.HttpApiTransport
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.journal.conversation.*

@Composable
fun NotebookRoute(account: Account, sessions: SessionController, menu: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context, sessions) { viewModelFactory {
        initializer { NotebookViewModel(sessions, workflow = { session, scope, repository ->
            val api = JournalApi(HttpApiTransport(), session, sessions::snapshot, sessions::expire)
            WorkflowController(WorkflowAccess(FeedbackApi(api), WorkflowDiskStore(context.noBackupFilesDir, session.account.id), acceptMetadata = repository::acceptFeedbackMetadata), scope)
        }, tutor = { session, scope ->
            TutorController(TutorApi(JournalApi(HttpApiTransport(), session, sessions::snapshot, sessions::expire)), scope)
        }, conversation = { session, repository, tutor ->
            val api = JournalApi(HttpApiTransport(), session, sessions::snapshot, sessions::expire)
            val workflows = WorkflowAccess(FeedbackApi(api), WorkflowDiskStore(context.noBackupFilesDir, session.account.id), acceptMetadata = repository::acceptFeedbackMetadata)
            ConversationController(ConversationAccess(ConversationApi(api), repository, { scope -> WorkflowController(workflows, scope) }, tutor))
        }, preferences = { session, scope -> JournalViewPreferences(context.noBackupFilesDir, session.account.id, scope) }) { session ->
            val workflows = WorkflowDiskStore(context.noBackupFilesDir, session.account.id)
            JournalRepository(JournalApi(HttpApiTransport(), session, sessions::snapshot, sessions::expire),
                JournalDiskStore(context.noBackupFilesDir, session.account.id),
                archive = JournalArchiveDiskStore(context.noBackupFilesDir, session.account.id),
                localWork = JournalLocalWork { workflows.read(it)?.needsArchiveRecovery() == true })
        } }
    } }
    val model: NotebookViewModel = viewModel(key = "active-journal", factory = factory)
    val binding by model.binding.collectAsStateWithLifecycle()
    val current = binding?.takeIf { it.accountId == account.id }?.controller
    if (current == null) CircularProgressIndicator() else {
        val state by current.state.collectAsStateWithLifecycle()
        FlushOnStop(current, binding?.conversation)
        val workflow = binding?.workflow
        val feedback = workflow?.state?.collectAsStateWithLifecycle()?.value
        val teacher = binding?.tutor
        val waitingTutor = teacher?.waitingTutor?.collectAsStateWithLifecycle()?.value
        val tutorState = teacher?.state?.collectAsStateWithLifecycle()?.value
        val conversation = binding?.conversation?.session?.collectAsStateWithLifecycle()?.value
        val conversationState = conversation?.state?.collectAsStateWithLifecycle()?.value
        CompositionLocalProvider(LocalJournalViewPreferences provides binding?.preferences, LocalThinkingTutor provides waitingTutor) {
        NotebookScreen(state, current, NotebookPanels(feedback = feedback?.let { FeedbackPresentation(it, workflow) },
            tutor = tutorState?.let { TutorPresentation(it, teacher) },
            conversation = conversationState?.let { ConversationPresentation(it, conversation) }), menu = menu)
        }
    }
}

@Composable
private fun FlushOnStop(controller: NotebookController, conversation: ConversationController?) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller, conversation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) controller.refreshVisibleContents()
            if (event == Lifecycle.Event.ON_STOP) { controller.flush(); scope.launch { conversation?.flush() } }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

private fun WorkflowSnapshot.needsArchiveRecovery() = pendingAnswer != null || pendingFinalEdit != null || submissionRequested
