package com.cdcg.convolab.journal.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FinalEditSheet(presentation: FeedbackPresentation) {
    val edit = presentation.state.finalEdit ?: return
    val controller = presentation.controller
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit final", style = MaterialTheme.typography.headlineSmall)
                Text("Edit your corrected Japanese. Your original draft stays available.")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = edit.text, onValueChange = controller::changeFinalText,
                        enabled = !presentation.state.busy, label = { Text("Corrected text") },
                        modifier = Modifier.fillMaxWidth().testTag("final-editor"))
                    FeedbackError(presentation)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = controller::dismissFinalEdit, enabled = !presentation.state.busy) { Text("Cancel") }
                    Button(onClick = controller::saveFinalEdit,
                        enabled = !presentation.state.busy && presentation.state.canRetry && edit.text.isNotBlank(),
                        modifier = Modifier.testTag("final-save")) { Text(if (presentation.state.busy) "Saving…" else "Save") }
                }
            }
        }
    }
}
