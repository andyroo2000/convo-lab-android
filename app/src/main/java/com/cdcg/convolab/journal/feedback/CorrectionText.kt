package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.journal.text.RubyText
import com.cdcg.convolab.journal.text.RubyParagraph
import com.cdcg.convolab.journal.text.RubyRun

@Composable
fun CorrectionText(segments: List<FeedbackSegment>, fallback: String) {
    var selected by remember(segments) { mutableStateOf<FeedbackSegment?>(null) }
    if (segments.isEmpty()) RubyText(fallback)
    val runs = segments.map { segment ->
        val color = when (segment.kind) {
            SegmentKind.Correction -> Color(0xFFFFE3DB)
            SegmentKind.Optional -> Color(0xFFFFF2BE)
            SegmentKind.Keep -> Color(0xFFE1F1DC)
            SegmentKind.Plain -> Color.Transparent
        }
        val action: (() -> Unit)? = if (segment.kind == SegmentKind.Plain) null else { { selected = segment } }
        RubyRun(segment.reading.ifEmpty { segment.text }, color, action)
    }
    RubyParagraph(runs)
    selected?.let { segment ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(segment.kind.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RubyText(segment.reading.ifEmpty { segment.text })
                segment.replacement?.let { Text(it) }
                segment.suggestion?.let { Text(it) }
                segment.explanation?.let { Text(it) }
            } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Got it") } })
    }
}
