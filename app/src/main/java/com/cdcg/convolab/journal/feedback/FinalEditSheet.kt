package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.appearance.LocalJournalTypography
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.cdcg.convolab.journal.input.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FinalEditSheet(presentation: FeedbackPresentation) {
    if (presentation.state.finalEdit == null) return
    val input = remember { JournalInputHost() }
    CompositionLocalProvider(LocalInputHost provides input) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(decorFitsSystemWindows = false, usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
            Surface(Modifier.fillMaxSize()) { FinalEditorLayout(presentation) }
        }
    }
}

@Composable
private fun FinalEditorLayout(presentation: FeedbackPresentation) {
    val edit = presentation.state.finalEdit ?: return
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val compact = maxHeight < 240.dp && WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
            Text("Edit final", style = MaterialTheme.typography.headlineSmall)
            if (!compact) Text("Edit your corrected Japanese. Your original draft stays available.")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                JournalWritingField(text = edit.text, changed = presentation.controller::changeFinalText,
                    options = WritingOptions("Corrected text", !presentation.state.busy, LocalJournalTypography.current.style),
                    modifier = Modifier.fillMaxWidth().testTag("final-editor"))
                FeedbackError(presentation)
            }
            JournalInputFooter(compact, actionWidth = 240.dp) { FinalEditActions(presentation) }
        }
    }
}

@Composable
private fun FinalEditActions(presentation: FeedbackPresentation) {
    val edit = presentation.state.finalEdit ?: return
    val controller = presentation.controller
    val clipboard = LocalClipboardManager.current
    val input = LocalInputHost.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = controller::dismissFinalEdit, enabled = !presentation.state.busy) { Text("Cancel") }
        TextButton(onClick = { clipboard.setText(AnnotatedString(edit.text)) }) { Text("Copy") }
        Button(onClick = { input?.finish(); controller.saveFinalEdit() },
            enabled = !presentation.state.busy && presentation.state.canRetry && edit.text.isNotBlank(),
            modifier = Modifier.testTag("final-save")) { Text(if (presentation.state.busy) "Saving…" else "Save") }
    }
}
