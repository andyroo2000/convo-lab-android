package com.cdcg.convolab.journal.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.input.*
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

class ConversationPresentation(val state: ConversationState, val session: ConversationSession)

@Composable
fun ConversationContent(presentation: ConversationPresentation, tutor: TutorPresentation?, enabled: Boolean) {
    val state = presentation.state
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        state.replies.forEach { binding -> key(binding.controller) {
            Surface(color = Color(0xFFFAFCFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD9E6F2))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    binding.node?.question?.let { TeacherQuestion(it, binding.node?.parentId?.let(::JournalId)) }
                    ReplyContent(binding.controller, enabled)
                }
            }
        } }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        TextButton(onClick = { presentation.session.refresh() }, enabled = enabled && !state.busy) { Text("Reload conversation") }
        if (tutor?.state?.source != null) TutorCard(tutor)
        state.composer?.let { key(it.controller) { ReplyContent(it.controller, enabled) } }
    }
}

@Composable
fun ReplyContent(controller: ReplyController, enabled: Boolean) {
    val state by controller.state.collectAsStateWithLifecycle()
    val work by controller.workflow.state.collectAsStateWithLifecycle()
    val feedback = FeedbackPresentation(work, controller.workflow)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.showsEditor(feedback)) ReplyEditor(state, controller, enabled)
        else {
            FeedbackContent(feedback)
            TextButton(onClick = controller::beginEditing, enabled = enabled && !work.busy) { Text("Edit answer") }
        }
        ReplyMessages(state, controller, enabled)
    }
    if (work.finalEdit != null) FinalEditSheet(feedback)
}

private fun ReplyState.showsEditor(feedback: FeedbackPresentation) = editing || (!feedback.visible && !feedback.state.busy)

@Composable
private fun ReplyMessages(state: ReplyState, controller: ReplyController, enabled: Boolean) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (!state.saved) TextButton(onClick = { controller.retryLocal() }, enabled = enabled && !state.busy) { Text("Retry local save") }
        if (state.conflict) TextButton(onClick = { controller.saveAsNew() }, enabled = enabled && !state.busy) { Text("Save answer as new entry") }
}

@Composable
private fun ReplyEditor(state: ReplyState, controller: ReplyController, enabled: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val editable = enabled && !state.busy && state.record.pendingSave == null
    val input = LocalInputHost.current
    Text("Your answer")
    JournalWritingField(text = state.text, changed = controller::edit,
        options = WritingOptions("Your answer", editable, LocalJournalTypography.current.style, "返事を書く…"),
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("conversation-answer-${state.record.id}"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { input?.finish(); keyboard?.hide(); controller.submit() }, enabled = enabled && !state.busy && state.text.isNotBlank(),
            modifier = Modifier.testTag("conversation-submit-${state.record.id}")) { Text(if (state.busy) "Submitting…" else "Submit answer") }
        TextButton(onClick = { clipboard.setText(AnnotatedString(state.text)) }) { Text("Copy answer") }
    }
    if (state.record.pendingSave != null) Text("Sending was interrupted. Retry submits the same answer without creating a duplicate.")
    if (state.canDiscard()) DiscardAnswer(state, controller, enabled)
}

private fun ReplyState.canDiscard() = record.entry == null && record.pendingSave == null

@Composable
private fun DiscardAnswer(state: ReplyState, controller: ReplyController, enabled: Boolean) {
    var discard by remember { mutableStateOf(false) }
    TextButton(onClick = { discard = true }, enabled = enabled && !state.busy) { Text("Discard unsent answer") }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this answer?") },
        text = { Text("This removes the answer saved on this device. Copy any writing you want to keep first.") },
        confirmButton = { TextButton(onClick = { discard = false; controller.discardUnsent() }, enabled = enabled && !state.busy) { Text("Discard answer") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep writing") } })
}
