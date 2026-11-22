package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.SpanStyle
import com.cdcg.convolab.R
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.cdcg.convolab.journal.appearance.LocalJournalTypography
import com.cdcg.convolab.journal.text.*
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.ui.JournalStyle

@Composable
fun CorrectionPopover(segment: FeedbackSegment, anchor: Rect, dismiss: () -> Unit) {
    val density = LocalDensity.current
    val margin = with(density) { 16.dp.roundToPx() }
    val pointer = remember { mutableStateOf(PopoverPointer()) }
    val position = remember(anchor, margin) { CorrectionPosition(anchor, margin) { pointer.value = it } }
    val screen = LocalWindowInfo.current.containerSize
    val width = with(density) { screen.width.toDp() }
    val height = with(density) { screen.height.toDp() }
    Popup(popupPositionProvider = position, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        CompositionLocalProvider(LocalRubyOnPaper provides false) {
        Box(Modifier.width(minOf(340.dp, width - 32.dp)).heightIn(max = height * .75f).testTag("correction-popover")) {
            Column(Modifier.padding(vertical = 8.dp).shadow(7.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(Color.White).verticalScroll(rememberScrollState()).testTag("correction-scroll")) {
                CorrectionPassage(segment)
                CorrectionExplanation(segment)
            }
            CorrectionPointer(pointer, segment.replacement != null, Modifier.matchParentSize())
        }
        }
    }
}

@Composable
private fun CorrectionPassage(segment: FeedbackSegment) {
    val replacement = segment.replacement ?: return
    val known = LocalKnownKanji.current
    val size = maxOf(19, LocalJournalTypography.current.size - 2)
    val original = segment.reading.ifEmpty { segment.text }.ifEmpty { "Insert" }
    val corrected = (segment.replacementReading ?: replacement).ifEmpty { "Remove" }
    Column(Modifier.fillMaxWidth().background(Color(0xFF398C43)).padding(horizontal = 18.dp, vertical = 14.dp)) {
        val serif = remember { FontFamily(Font(R.font.noto_serif_jp, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))) }
        RubyParagraph(listOf(RubyRun(original, style = SpanStyle(color = Color(0xFFFFF7A9))), RubyRun(" → $corrected", style = SpanStyle(color = Color(0xFFFFFDF2)))),
            appearance = RubyAppearance(size, serif, known, Color(0xFFFFFDF2), Color(0xFFFFF7A9), compact = true))
    }
}

@Composable
private fun CorrectionExplanation(segment: FeedbackSegment) {
    val explanation = segment.explanationReading ?: segment.explanation.orEmpty()
    val runs = remember(explanation) { ExplanationMarkup.parse(explanation).map {
        RubyRun(it.text, style = SpanStyle(fontWeight = if (it.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (it.italic) FontStyle.Italic else FontStyle.Normal))
    } }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp)) {
        if (explanation.isNotEmpty()) RubyParagraph(runs, appearance = RubyAppearance(maxOf(14, LocalJournalTypography.current.size - 4), JournalBrand.Serif, LocalKnownKanji.current, compact = true))
        if (segment.replacement == null) segment.suggestion?.let {
            RubyText(segment.suggestionReading ?: it, appearance = RubyAppearance(18, JournalBrand.Handwriting, LocalKnownKanji.current))
        }
    }
}

@Composable
private fun CorrectionPointer(placement: State<PopoverPointer>, edited: Boolean, modifier: Modifier) {
    // Placement is consumed only during drawing: no composition/layout feedback loop.
    // Both orientations use the same fixed padding, and layout finishes before this draw.
    Canvas(modifier) {
        val pointer = placement.value
        val center = pointer.x.toFloat().coerceIn(36.dp.toPx(), size.width - 36.dp.toPx())
        val edge = if (pointer.top) 8.dp.toPx() else size.height - 8.dp.toPx()
        val tip = if (pointer.top) 0f else size.height
        val color = if (pointer.top && edited) Color(0xFF398C43) else Color.White
        drawPath(Path().apply { moveTo(center - 12.dp.toPx(), edge); lineTo(center, tip); lineTo(center + 12.dp.toPx(), edge); close() }, color)
    }
}

internal data class PopoverPointer(val top: Boolean = true, val x: Int = 100)

internal class CorrectionPosition(private val anchor: Rect, private val margin: Int, private val placed: (PopoverPointer) -> Unit) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val center = anchorBounds.left + anchor.center.x.toInt()
        val left = (center - popupContentSize.width / 2).coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
        val below = anchorBounds.top + anchor.bottom.toInt()
        val above = anchorBounds.top + anchor.top.toInt() - popupContentSize.height
        val top = above < margin
        val y = (if (top) below else above).coerceIn(margin, maxOf(margin, windowSize.height - popupContentSize.height - margin))
        placed(PopoverPointer(top, center - left))
        return IntOffset(left, y)
    }
}
