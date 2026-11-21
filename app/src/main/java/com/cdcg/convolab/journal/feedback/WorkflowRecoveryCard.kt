package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun WorkflowRecoveryCard(presentation: FeedbackPresentation) {
    val recovery = presentation.state.recovery ?: return
    val clipboard = LocalClipboardManager.current
    var confirming by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Saved work needs attention", style = MaterialTheme.typography.titleLarge)
        Text(recovery.message)
        if (recovery.text.isNotEmpty()) {
            Text(recovery.text)
            TextButton(onClick = { clipboard.setText(AnnotatedString(recovery.text)) }) { Text("Copy saved text") }
        }
        Button(onClick = { confirming = true }, enabled = !presentation.state.busy) { Text("Archive earlier work and continue") }
        FeedbackError(presentation)
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Continue with this version?") },
        text = { Text("The earlier saved work will stay in a recovery file on this device. It will not be submitted automatically.") },
        confirmButton = { TextButton(onClick = { confirming = false; presentation.controller.recoverArchivedWork() }) { Text("Archive and continue") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}
