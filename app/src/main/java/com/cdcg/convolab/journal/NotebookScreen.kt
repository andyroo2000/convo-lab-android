package com.cdcg.convolab.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.cdcg.convolab.journal.input.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
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
import com.cdcg.convolab.ui.*
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.journal.conversation.*

typealias NotebookMenu = @Composable () -> Unit
data class NotebookPanels(val feedback: FeedbackPresentation? = null, val tutor: TutorPresentation? = null,
    val conversation: ConversationPresentation? = null)

private class NotebookPresentation(val state: NotebookState, val controller: NotebookController, val panels: NotebookPanels) {
    val editing = panels.feedback?.state?.finalEdit != null
    val writing = panels.feedback?.visible != true
    val canNavigate = !state.loading && !editing
    val canClose = !editing && !state.busy && !state.contents && !state.closePrompt
}

@Composable
fun NotebookScreen(state: NotebookState, controller: NotebookController, panels: NotebookPanels = NotebookPanels(), menu: NotebookMenu) {
    val input = remember { JournalInputHost() }
    val presentation = NotebookPresentation(state, controller, panels)
    CompositionLocalProvider(LocalInputHost provides input, LocalJournalNavigationEnabled provides presentation.canNavigate, LocalTutorPicker provides panels.tutor?.controller?.let { { it.showPicker() } }) {
        BackHandler(presentation.canClose) { controller.requestClose() }
        BackHandler(presentation.editing) {}
        BoxWithConstraints(Modifier.fillMaxSize().background(JournalBrand.Green).safeDrawingPadding().imePadding()) {
            val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val compact = maxHeight < 240.dp && keyboardVisible
            Column(Modifier.fillMaxSize()) {
                if (!compact) NotebookHeader(presentation.canNavigate, { controller.showContents(true) }, menu)
                Column(Modifier.weight(1f).background(Color(0xFFFAF9F6))) { NotebookBody(presentation, keyboardVisible, compact) }
            }
        }
        NotebookOverlays(presentation)
    }
}

@Composable
private fun ColumnScope.NotebookBody(presentation: NotebookPresentation, keyboardVisible: Boolean, compact: Boolean) {
    val (state, controller, panels) = Triple(presentation.state, presentation.controller, presentation.panels)
    if (presentation.editing) FinalEditSheet(panels.feedback!!) else {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            NotebookPage(state, controller, panels)
            if (!keyboardVisible && presentation.writing) NotebookActions(state, controller, panels.feedback, compact)
        }
        JournalInputFooter(compact) { if (keyboardVisible && presentation.writing) NotebookActions(state, controller, panels.feedback, compact) }
    }
}

@Composable
private fun NotebookOverlays(presentation: NotebookPresentation) {
    val (state, controller, panels) = Triple(presentation.state, presentation.controller, presentation.panels)
    if (panels.tutor?.state?.source == null && panels.tutor?.state?.choosing == true) TutorPicker(panels.tutor)
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
    if (panels.feedback?.visible != true) NotebookMessage(state, controller)
}

@Composable
private fun NotebookFeedback(panels: NotebookPanels) {
    panels.feedback?.let { FeedbackContent(it) }
    if (panels.conversation != null) ConversationContent(panels.conversation, panels.tutor, true)
    else if (panels.tutor?.state?.source != null) TutorCard(panels.tutor)
}

@Composable
private fun NotebookHeader(enabled: Boolean, contents: () -> Unit, menu: NotebookMenu) {
    val large = LocalDensity.current.fontScale >= 1.5f
    val navigationColor = Color.White.copy(alpha = if (enabled) 1f else .5f)
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
        if (large) Text("Journal", fontFamily = JournalBrand.Serif, fontSize = 18.sp, color = Color.White)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!large) NotebookHeaderBrand(navigationColor)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = contents, enabled = enabled, contentPadding = PaddingValues(4.dp)) {
                JournalIcon(JournalSymbol.Contents, color = navigationColor)
                Spacer(Modifier.width(8.dp))
                Text("Contents", fontFamily = JournalBrand.Serif, fontSize = 18.sp, color = navigationColor)
            }
            menu()
        }
    }
}

@Composable
private fun RowScope.NotebookHeaderBrand(navigationColor: Color) {
    Text("CONVO-LAB", fontFamily = JournalBrand.Anton, fontSize = 30.sp, letterSpacing = (-.5).sp, color = JournalBrand.Yellow)
    Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(32.dp).background(Color.White.copy(alpha = .4f)))
    Text("Journal", fontFamily = JournalBrand.Serif, fontSize = 18.sp, color = navigationColor)
}

@Composable
private fun NotebookActions(state: NotebookState, controller: NotebookController, feedback: FeedbackPresentation?, compact: Boolean) {
    val input = LocalInputHost.current
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAF9F6)).padding(horizontal = 14.dp, vertical = actionPadding(compact)),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        JournalDraftButton("Close", { input?.finish(); controller.requestClose() }, style = DraftButtonStyle(enabled = !state.busy, filled = false))
        JournalDraftButton(when { state.busy -> "Saving…"; feedback?.visible == true -> "Back to draft"; feedback != null -> "Submit"; else -> "Save draft" }, action = {
            input?.finish()
            if (feedback?.visible == true) feedback.controller.cancel()
            else controller.save { feedback?.controller?.submit(it) }
        }, style = DraftButtonStyle(enabled = state.canEdit && state.draft.body.isNotBlank()), modifier = Modifier.testTag("journal-save"))
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

private fun actionPadding(compact: Boolean): Dp { return if (compact) 4.dp else 8.dp }
