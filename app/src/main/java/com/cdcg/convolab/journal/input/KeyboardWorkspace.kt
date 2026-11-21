package com.cdcg.convolab.journal.input

import android.content.Context
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdcg.convolab.auth.SessionController
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun KeyboardWorkspace(accountId: Long, sessions: SessionController, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context, sessions) { viewModelFactory { initializer {
        KeyboardViewModel(sessions) { id ->
            val preferences = KeyboardPreference(context.getSharedPreferences("journal-keyboard", Context.MODE_PRIVATE))
            JournalInputSession(preferences, NativeJournalTyping(context, id))
        }
    } } }
    val owner: KeyboardViewModel = viewModel(key = "active-keyboard", factory = factory)
    val binding by owner.binding.collectAsStateWithLifecycle()
    val session = binding?.takeIf { it.accountId == accountId }?.session
    if (session == null) { CircularProgressIndicator(); return }
    val preferences = session.preference
    CompositionLocalProvider(LocalJournalInput provides session, content = content)
    session.status?.let { message ->
        AlertDialog(onDismissRequest = session::dismissStatus, title = { Text("Typing learning") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = session::dismissStatus) { Text("OK") } })
    }
    session.error?.let { message ->
        AlertDialog(onDismissRequest = session::dismissError, title = { Text("Japanese input") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = session::dismissError) { Text("OK") } },
            dismissButton = { TextButton(onClick = { preferences.change(JournalKeyboard.Device); session.dismissError() }) { Text("Use device keyboard") } })
    }
}
