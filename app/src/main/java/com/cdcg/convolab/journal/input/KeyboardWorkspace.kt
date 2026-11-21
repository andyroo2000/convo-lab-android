package com.cdcg.convolab.journal.input

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun KeyboardWorkspace(accountId: Long, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { KeyboardPreference(context.getSharedPreferences("journal-keyboard", Context.MODE_PRIVATE)) }
    val session = remember(accountId) { JournalInputSession(preferences, NativeJournalTyping(context, accountId)) }
    DisposableEffect(session) { onDispose { session.close() } }
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
