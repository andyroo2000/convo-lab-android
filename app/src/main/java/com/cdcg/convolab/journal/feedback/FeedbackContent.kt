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
import com.cdcg.convolab.ui.JournalBrand

class FeedbackPresentation(val state: WorkflowState, val controller: WorkflowController) {
    val visible: Boolean get() = state.snapshot?.let { it.analysis != null || it.clarification != null || it.submissionRequested } == true
}

@Composable
fun FeedbackContent(presentation: FeedbackPresentation) {
    val state = presentation.state
    val analysis = state.snapshot?.analysis
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (analysis?.status == WorkStatus.Ready) FeedbackDocument(presentation, analysis)
        else {
            Surface(color = JournalBrand.Paper, shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(state.entry?.title ?: "Your draft", fontFamily = JournalBrand.Handwriting, fontSize = 26.sp, color = Color(0xFF452713))
                    Spacer(Modifier.height(18.dp))
                    RubyText(state.entry?.body.orEmpty())
                }
            }
            val clarification = state.snapshot?.clarification
            if (clarification?.status == WorkStatus.Waiting) ClarificationCard(presentation, clarification)
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Preparing your feedback…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        FeedbackError(presentation)
    }
}

@Composable
private fun FeedbackDocument(presentation: FeedbackPresentation, analysis: Analysis) {
    var final by rememberSaveable(analysis.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(color = JournalBrand.Paper, shape = NotebookPaperShape, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp).drawBehind {
            drawLine(Color(0xFFD58B78), Offset(15.dp.toPx(), 0f), Offset(15.dp.toPx(), size.height), 1.dp.toPx())
        }.padding(start = 28.dp, end = 14.dp, top = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(colors = feedbackTabColors(), selected = !final, onClick = { final = false }, label = { Text("Draft", fontSize = 20.sp) })
                FilterChip(colors = feedbackTabColors(), selected = final, onClick = { final = true }, label = { Text("Final", fontSize = 20.sp) })
            }
            Text(journalDate(presentation.state.entry?.entryDate.orEmpty()), textAlign = TextAlign.End, color = Color(0xFF745340), modifier = Modifier.fillMaxWidth())
            Text(analysis.feedback?.title ?: presentation.state.entry?.title.orEmpty(), fontFamily = JournalBrand.Handwriting, fontSize = 26.sp, color = Color(0xFF452713))
            if (final) RubyText(analysis.finalReading ?: analysis.finalText.orEmpty())
            else CorrectionText(analysis.feedback?.segments.orEmpty(), presentation.state.entry?.body.orEmpty())
            if (final) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(analysis.finalText.orEmpty())) }) { Text("Copy") }
                TextButton(onClick = presentation.controller::beginFinalEdit, enabled = !presentation.state.busy) { Text("Edit final") }
            }
        }
    }
}

@Composable
fun FeedbackError(presentation: FeedbackPresentation) {
    presentation.state.error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (presentation.state.canRetry) TextButton(onClick = { presentation.controller.retry() }, enabled = !presentation.state.busy) { Text("Retry") }
    }
}

@Composable
private fun feedbackTabColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF398B49), selectedLabelColor = Color.White)

private fun journalDate(value: String): String = try {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPAN))
} catch (_: java.time.DateTimeException) { value }
