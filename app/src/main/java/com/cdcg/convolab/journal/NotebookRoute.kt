package com.cdcg.convolab.journal

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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

@Composable
fun NotebookRoute(account: Account, sessions: SessionController, menu: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context, sessions) { viewModelFactory {
        initializer { NotebookViewModel(sessions, workflow = { session, scope ->
            val api = JournalApi(HttpApiTransport(), session, sessions::snapshot)
            WorkflowController(WorkflowAccess(FeedbackApi(api), WorkflowDiskStore(context.noBackupFilesDir, session.account.id)), scope)
        }) { session ->
            JournalRepository(JournalApi(HttpApiTransport(), session, sessions::snapshot),
                JournalDiskStore(context.noBackupFilesDir, session.account.id))
        } }
    } }
    val model: NotebookViewModel = viewModel(key = "active-journal", factory = factory)
    val binding by model.binding.collectAsStateWithLifecycle()
    val current = binding?.takeIf { it.accountId == account.id }?.controller
    if (current == null) CircularProgressIndicator() else {
        val state by current.state.collectAsStateWithLifecycle()
        FlushOnStop(current)
        val workflow = binding?.workflow
        val feedback = workflow?.state?.collectAsStateWithLifecycle()?.value
        NotebookScreen(state, current, feedback = feedback?.let { FeedbackPresentation(it, workflow) }, menu = menu)
    }
}

@Composable
private fun FlushOnStop(controller: NotebookController) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.flush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
