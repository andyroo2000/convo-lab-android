package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cdcg.convolab.journal.NotebookPaperShape
import com.cdcg.convolab.journal.notebookLines
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FinalEditSheet(presentation: FeedbackPresentation) {
    FinalEditorLayout(presentation)
}

/** A reply editor owns its input host while the scrollable conversation stays mounted. */
@Composable
fun ReplyFinalEditDialog(presentation: FeedbackPresentation) {
    val input = remember { JournalInputHost() }
    CompositionLocalProvider(LocalInputHost provides input) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(decorFitsSystemWindows = false, usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
            LightDialogStatusBar()
            Surface(Modifier.fillMaxSize(), color = JournalStyle.Paper) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) { FinalEditorLayout(presentation) }
            }
        }
    }
}

@Composable
private fun FinalEditorLayout(presentation: FeedbackPresentation) {
    if (presentation.state.finalEdit == null) return
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        val compact = maxHeight < 240.dp && WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp).semantics { paneTitle = "Edit final" }) {
            if (!compact) presentation.state.snapshot?.analysis?.let { FeedbackToolbar(true, {}, presentation, it) }
            FinalEditorPaper(presentation, compact, Modifier.weight(1f))
            Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
            JournalInputFooter(compact, actionWidth = 240.dp) { FinalEditActions(presentation) }
        }
    }
}

@Composable
private fun FinalEditorPaper(presentation: FeedbackPresentation, compact: Boolean, modifier: Modifier) {
    val typography = LocalJournalTypography.current
    Column(modifier.fillMaxWidth().shadow(3.dp, NotebookPaperShape).background(JournalStyle.Paper, NotebookPaperShape)
        .border(1.dp, Color(0xFFE5DECC), NotebookPaperShape).drawBehind {
            drawLine(Color(0xFFD58B78), Offset(15.dp.toPx(), 0f), Offset(15.dp.toPx(), size.height), 1.dp.toPx())
        }.verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 14.dp, top = 18.dp, bottom = 32.dp)) {
        if (!compact) {
            Text(journalDate(presentation.state.entry?.entryDate.orEmpty()), textAlign = TextAlign.End,
                fontFamily = typography.family, fontSize = 18.sp, color = JournalStyle.Ink, modifier = Modifier.fillMaxWidth())
            Text(presentation.state.snapshot?.analysis?.feedback?.title ?: presentation.state.entry?.title.orEmpty(),
                fontFamily = typography.family, fontSize = 30.sp, color = JournalStyle.Ink, modifier = Modifier.padding(top = 24.dp, bottom = 42.dp))
        }
        JournalWritingField(text = presentation.state.finalEdit?.text.orEmpty(), changed = presentation.controller::changeFinalText,
            options = WritingOptions("Corrected text", !presentation.state.busy, typography.style.copy(color = JournalStyle.Ink)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).notebookLines(typography.size).testTag("final-editor"))
        FeedbackError(presentation)
    }
}

@Composable
private fun FinalEditActions(presentation: FeedbackPresentation) {
    val edit = presentation.state.finalEdit ?: return
    val controller = presentation.controller
    val input = LocalInputHost.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
        JournalDraftButton("Cancel", controller::dismissFinalEdit, style = DraftButtonStyle(enabled = !presentation.state.busy, filled = false))
        JournalDraftButton(if (presentation.state.busy) "Saving…" else "Save changes", action = { input?.finish(); controller.saveFinalEdit() },
            style = DraftButtonStyle(enabled = !presentation.state.busy && presentation.state.canRetry && edit.text.isNotBlank()),
            modifier = Modifier.testTag("final-save"))
    }
}
