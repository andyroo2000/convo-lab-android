package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.cdcg.convolab.journal.text.RubyText
import com.cdcg.convolab.journal.text.RubyParagraph
import com.cdcg.convolab.journal.text.RubyRun

@Composable
fun CorrectionText(segments: List<FeedbackSegment>, fallback: String) {
    var selected by remember(segments) { mutableStateOf<Int?>(null) }
    val anchors = remember(segments) { mutableMapOf<Int, Rect>() }
    Box {
        if (segments.isEmpty()) RubyText(fallback)
        val runs = segments.mapIndexed { index, segment ->
            val color = when (segment.kind) {
                SegmentKind.Correction -> Color(0xFFFBE9E9)
                SegmentKind.Optional -> Color(0xFFFFF2C5)
                SegmentKind.Keep -> Color(0xFFE1F1DC)
                SegmentKind.Plain -> Color.Transparent
            }
            val action: (() -> Unit)? = if (segment.kind == SegmentKind.Plain) null else { { selected = index } }
            RubyRun(segment.reading.ifEmpty { segment.text }, color, onClick = action)
        }
        RubyParagraph(runs, onRunBounds = { index, bounds -> anchors[index] = bounds })
        selected?.let { index ->
            CorrectionPopover(segments[index], anchors[index] ?: Rect.Zero) { selected = null }
        }
    }
}
