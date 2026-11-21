package com.cdcg.convolab.auth.deletion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.auth.AuthPasswordField

@Composable
fun AccountDeletionDialog(state: AccountDeletionState, controller: AccountDeletionController) {
    if (!state.showing) return
    val password = remember { TextFieldState() }
    AlertDialog(onDismissRequest = controller::dismiss,
        title = { Text(if (state.cleanup) "Account cleanup" else "Delete your account?") },
        text = { DeletionExplanation(state, password) },
        confirmButton = { DeletionConfirmation(state, controller, password) },
        dismissButton = { if (!state.cleanup) TextButton(onClick = controller::dismiss, enabled = !state.busy) { Text("Cancel") } })
}

@Composable
private fun DeletionExplanation(state: AccountDeletionState, password: TextFieldState) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.cleanup) Text(if (state.pending) "Removing this account’s data from this device." else "Your account and its local data have been removed.")
        else {
            Text("This permanently deletes your entire ConvoLab / Learning OS account, including your journal and study data. This cannot be undone.")
            AuthPasswordField("Current password", password, !state.busy)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeletionConfirmation(state: AccountDeletionState, controller: AccountDeletionController, password: TextFieldState) {
    if (state.cleanup) TextButton(onClick = { if (state.pending) controller.retry() else controller.dismiss() }, enabled = !state.busy) {
        Text(if (state.pending) "Retry cleanup" else "Done")
    } else TextButton(onClick = {
        val submitted = password.text.toString()
        password.edit { replace(0, length, "") }
        controller.delete(submitted)
    }, enabled = !state.busy && password.text.isNotEmpty()) {
        Text("Permanently delete account", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun AccountCleanupRetry(state: AccountDeletionState, controller: AccountDeletionController) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Local account cleanup", style = MaterialTheme.typography.headlineSmall)
        Text(state.error ?: "Local cleanup needs to finish before sign-in can be restored.")
        TextButton(onClick = controller::retry) { Text("Retry cleanup") }
    }
}
