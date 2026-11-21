package com.cdcg.convolab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcg.convolab.auth.deletion.AccountCleanupRetry
import com.cdcg.convolab.auth.deletion.AccountDeletionDialog
import com.cdcg.convolab.auth.AuthScreen
import com.cdcg.convolab.auth.AuthViewModel
import com.cdcg.convolab.settings.AccountWorkspace
import com.cdcg.convolab.settings.SettingsActions

@Composable
fun JournalApp(openTypingLab: (() -> Unit)?, model: AuthViewModel = viewModel()) {
    val state by model.sessions.state.collectAsStateWithLifecycle()
    val deletion by model.deletion.state.collectAsStateWithLifecycle()
    val account = state.account
    Box(Modifier.fillMaxSize()) {
    when {
        state.restoring && deletion.pending && !deletion.busy -> AccountCleanupRetry(deletion, model.deletion)
        state.restoring -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        account == null -> AuthScreen(state, model::submit, model::clearMessage)
        else -> AccountWorkspace(account, model.sessions, SettingsActions({}, model::signOut, openTypingLab, model.deletion::open))
    }
    if (deletion.pending && !deletion.showing) TextButton(onClick = model.deletion::openCleanup,
        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) { Text("Finish removing local account data") }
    }
    AccountDeletionDialog(deletion, model.deletion)
    if (account != null && state.error != null) AlertDialog(onDismissRequest = model::clearMessage,
        title = { Text("Account") }, text = { Text(state.error.orEmpty()) },
        confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
}
