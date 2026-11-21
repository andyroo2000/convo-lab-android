package com.cdcg.convolab.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.journal.conversation.*

typealias NotebookMenu = @Composable () -> Unit
data class NotebookPanels(val feedback: FeedbackPresentation? = null, val tutor: TutorPresentation? = null,
    val conversation: ConversationPresentation? = null)

@Composable
fun NotebookScreen(state: NotebookState, controller: NotebookController, panels: NotebookPanels = NotebookPanels(), menu: NotebookMenu) {
    BackHandler(!state.busy && !state.contents && !state.closePrompt) { controller.requestClose() }
    BoxWithConstraints(Modifier.fillMaxSize().background(JournalBrand.Green).safeDrawingPadding().imePadding()) {
        val compact = maxHeight < 240.dp && WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize()) {
            if (!compact) { NotebookHeader(!state.loading, { controller.showContents(true) }, menu) }
            Column(Modifier.weight(1f).background(Color(0xFFFAF9F6))) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { NotebookPage(state, controller, panels) }
                NotebookActions(state, controller, panels.feedback, compact)
            }
        }
    }
    if (panels.feedback?.state?.finalEdit != null) FinalEditSheet(panels.feedback)
    if (state.contents) NotebookContents(state, controller)
    if (state.closePrompt) CloseDraftSheet(state, controller)
}

@Composable
private fun NotebookPage(state: NotebookState, controller: NotebookController, panels: NotebookPanels) {
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (panels.feedback?.visible == true) {
        if (state.busy) Text("Updating journal…") else NotebookFeedback(panels)
    } else {
        state.draft.title?.let { Text(it, fontFamily = JournalBrand.Serif, fontSize = 24.sp) }
        key(state.draft.id) { NotebookPaper(state, controller::edit) }
    }
    NotebookMessage(state, controller)
}

@Composable
private fun NotebookFeedback(panels: NotebookPanels) {
    panels.feedback?.let { FeedbackContent(it) }
    if (panels.conversation != null) ConversationContent(panels.conversation, panels.tutor, true)
    else if (panels.tutor?.state?.source != null) TutorCard(panels.tutor)
}

@Composable
private fun NotebookHeader(enabled: Boolean, contents: () -> Unit, menu: NotebookMenu) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("CONVO-LAB", fontFamily = JournalBrand.Anton, fontSize = 26.sp, color = JournalBrand.Yellow)
            Text("Journal", fontFamily = JournalBrand.Serif, fontSize = 17.sp, color = Color.White)
        }
        TextButton(onClick = contents, enabled = enabled) { Text("Contents", fontFamily = JournalBrand.Serif, fontSize = 20.sp, color = Color.White) }
        menu()
    }
}

@Composable
private fun NotebookActions(state: NotebookState, controller: NotebookController, feedback: FeedbackPresentation?, compact: Boolean) {
    val labelSize = actionLabelSize(compact)
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAF9F6)).padding(horizontal = 14.dp, vertical = actionPadding(compact)),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = controller::requestClose, enabled = !state.busy) { Text("Close", fontFamily = JournalBrand.Serif, fontSize = labelSize) }
        Button(onClick = {
            if (feedback?.visible == true) feedback.controller.cancel()
            else controller.save { feedback?.controller?.submit(it) }
        }, enabled = state.canEdit && state.draft.body.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF46764C)), modifier = Modifier.testTag("journal-save")) {
            Text(when { state.busy -> "Saving…"; feedback?.visible == true -> "Back to draft"; feedback != null -> "Submit"; else -> "Save draft" }, fontFamily = JournalBrand.Serif, fontSize = labelSize)
        }
    }
}

@Composable
private fun NotebookMessage(state: NotebookState, controller: NotebookController) {
    if (state.pendingDelete) {
        Text("Deletion is awaiting confirmation. Your local copy is kept until it succeeds.")
        TextButton(onClick = { controller.delete(state.draft) }, enabled = !state.busy) { Text("Retry deletion") }
    }
    if (state.savedLocally) Text("Saved on this device", color = Color(0xFF586556), fontSize = 12.sp)
    if (state.recoveryCount > 0) Text("${state.recoveryCount} saved records need recovery. Their files have been kept.", color = Color(0xFF974634))
    state.error?.let {
        Text(it, color = Color(0xFF974634), fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row {
            TextButton(onClick = { controller.flush() }, enabled = !state.busy) { Text("Retry local save") }
            if (state.canRecoverCopy) TextButton(onClick = { controller.recoverCopy() }) { Text("Save as new") }
        }
    }
}

private fun actionLabelSize(compact: Boolean): TextUnit { return if (compact) 18.sp else 22.sp }
private fun actionPadding(compact: Boolean): Dp { return if (compact) 4.dp else 8.dp }
