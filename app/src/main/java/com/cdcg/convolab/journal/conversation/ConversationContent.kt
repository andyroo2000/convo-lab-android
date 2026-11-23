package com.cdcg.convolab.journal.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.input.*
import com.cdcg.convolab.ui.*
import com.cdcg.convolab.journal.NotebookPaperShape
import com.cdcg.convolab.journal.notebookLines
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

class ConversationPresentation(val state: ConversationState, val session: ConversationSession)

@Composable
fun ConversationContent(presentation: ConversationPresentation, tutor: TutorPresentation?, enabled: Boolean) {
    val state = presentation.state
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        state.replies.forEach { binding -> key(binding.controller) {
            Surface(color = Color(0xFFFAFCFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0x1F007AFF))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    binding.node?.question?.let { TeacherQuestion(it, binding.node?.parentId?.let(::JournalId)) }
                    HorizontalDivider()
                    ReplyContent(binding.controller, enabled)
                }
            }
        } }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = { presentation.session.refresh() }, enabled = enabled && !state.busy) { Text("Retry conversation") }
        }
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
            Box(Modifier.testTag("conversation-feedback-${state.record.id}")) { FeedbackContent(feedback) }
            if (work.canReturnToDraft) TextButton(onClick = controller::beginEditing, enabled = enabled && !work.busy) { Text("Back to draft") }
        }
        ReplyMessages(state, controller, enabled)
    }
    if (work.finalEdit != null) ReplyFinalEditDialog(feedback)
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
    val editable = enabled && !state.busy && state.record.pendingSave == null
    val input = LocalInputHost.current
    Box(Modifier.fillMaxWidth().background(JournalStyle.Paper, NotebookPaperShape).border(1.dp, JournalStyle.Border, NotebookPaperShape).padding(24.dp)) {
    JournalWritingField(text = state.text, changed = controller::edit,
        options = WritingOptions("Your answer", editable, LocalJournalTypography.current.style, "返事を書く…"),
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).notebookLines(LocalJournalTypography.current.size).testTag("conversation-answer-${state.record.id}"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalDraftButton(if (state.busy) "Submitting…" else "Submit answer", { input?.finish(); keyboard?.hide(); controller.submit() },
            style = DraftButtonStyle(enabled = enabled && !state.busy && state.text.isNotBlank()), modifier = Modifier.testTag("conversation-submit-${state.record.id}"))
    }
    if (state.record.pendingSave != null) Text("Sending was interrupted. Retry submits the same answer without creating a duplicate.")
}
