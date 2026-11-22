package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import com.cdcg.convolab.journal.NotebookPaperShape
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.journal.text.RubyText
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.audio.*
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

class FeedbackPresentation(val state: WorkflowState, val controller: WorkflowController) {
    val visible: Boolean get() = state.busy || state.error != null || state.recovery != null || state.snapshot?.let { it.analysis != null || it.clarification != null || it.submissionRequested } == true
}

@Composable
fun FeedbackContent(presentation: FeedbackPresentation) {
    val state = presentation.state
    if (state.recovery != null) { WorkflowRecoveryCard(presentation); return }
    val analysis = state.snapshot?.analysis
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            analysis?.status == WorkStatus.Ready -> FeedbackDocument(presentation, analysis)
            state.busy -> JournalThinking(lastAnswer = state.lastAnswer ?: state.snapshot?.pendingAnswer?.text, returnToDraft = presentation.controller::cancel)
            state.snapshot?.clarification?.status == WorkStatus.Waiting -> {
                WaitingTutorPortrait(Modifier.size(40.dp))
                ClarificationCard(presentation, state.snapshot.clarification)
            }
        }
        FeedbackError(presentation)
    }
}

@Composable
private fun FeedbackDocument(presentation: FeedbackPresentation, analysis: Analysis) {
    val preferences = LocalJournalViewPreferences.current
    val entryId = presentation.state.entry?.id.orEmpty()
    val saved = preferences?.versions?.collectAsStateWithLifecycle()?.value
    var fallback by rememberSaveable(entryId) { mutableStateOf(false) }
    val final = saved?.get(entryId) ?: fallback
    val choose: (Boolean) -> Unit = { fallback = it; if (entryId.isNotEmpty()) preferences?.select(entryId, it) }
    Column {
    FeedbackToolbar(final, choose, presentation, analysis)
    if (final) FinalAudio(presentation, analysis, header = true)
    Surface(color = JournalBrand.Paper, shape = NotebookPaperShape, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp).drawBehind {
            drawLine(Color(0xFFD58B78), Offset(15.dp.toPx(), 0f), Offset(15.dp.toPx(), size.height), 1.dp.toPx())
        }.padding(start = 28.dp, end = 14.dp, top = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(journalDate(presentation.state.entry?.entryDate.orEmpty()), textAlign = TextAlign.End, fontFamily = LocalJournalTypography.current.family, fontSize = 18.sp, color = Color(0xFF4B1800), modifier = Modifier.fillMaxWidth())
            Text(analysis.feedback?.title ?: presentation.state.entry?.title.orEmpty(), fontFamily = LocalJournalTypography.current.family, fontSize = 30.sp, color = Color(0xFF4B1800), modifier = Modifier.padding(bottom = 18.dp))
            if (final) FinalAudio(presentation, analysis, header = false)
            else DraftFeedback(analysis, presentation.state.entry?.body.orEmpty())
        }
    }
    }
}

@Composable
private fun FinalAudio(presentation: FeedbackPresentation, analysis: Analysis, header: Boolean) {
    val entry = presentation.state.entry ?: return
    val source = remember(entry.id, analysis.id, analysis.replyRevision, analysis.finalText, analysis.finalReading) {
        AudioSource(JournalId(entry.id), AudioContent.Final(JournalId(analysis.id), analysis.replyRevision,
            analysis.finalText.orEmpty(), analysis.finalReading ?: analysis.finalText.orEmpty(), analysis.feedback?.title ?: entry.title ?: "Journal entry"))
    }
    if (header) AudioControls(source) else AudioReading(source)
}

@Composable
fun FeedbackError(presentation: FeedbackPresentation) {
    presentation.state.error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!presentation.state.canRetry) TextButton(onClick = presentation.controller::cancel) { Text("Back to draft") }
        if (presentation.state.canRetry) TextButton(onClick = { presentation.controller.retry() }, enabled = !presentation.state.busy) { Text("Retry") }
    }
}

internal fun journalDate(value: String): String = try {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPAN))
} catch (_: java.time.DateTimeException) { value }
