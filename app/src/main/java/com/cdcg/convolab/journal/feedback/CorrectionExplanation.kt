package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.R
import com.cdcg.convolab.journal.appearance.LocalJournalTypography
import com.cdcg.convolab.journal.text.*
import com.cdcg.convolab.ui.JournalBrand

@Composable
internal fun CorrectionExplanation(segment: FeedbackSegment) {
    val size = maxOf(14, LocalJournalTypography.current.size - 4)
    val japanese = remember { FontFamily(Font(R.font.noto_serif_jp, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)))) }
    val runs = remember(segment, size, japanese) { CorrectionExplanationContent.runs(segment).map {
        RubyRun(it.text, style = explanationStyle(it, size, japanese))
    } }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp)) {
        if (runs.isNotEmpty()) RubyParagraph(runs, appearance = RubyAppearance(size, JournalBrand.Serif, LocalKnownKanji.current, compact = true))
        if (segment.replacement == null) segment.suggestion?.let {
            RubyText(segment.suggestionReading ?: it, appearance = RubyAppearance(18, JournalBrand.Handwriting, LocalKnownKanji.current))
        }
    }
}

private fun explanationStyle(run: CorrectionExplanationRun, size: Int, japanese: FontFamily) = SpanStyle(
    fontFamily = if (run.japanese) japanese else JournalBrand.Serif,
    fontSize = (size * if (run.japanese) 1f else .8f).sp,
    fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
    color = if (run.replacement) Color(0xFF3D694C) else Color(0xFF4B1800),
)
