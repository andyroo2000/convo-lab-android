package com.cdcg.convolab.journal.text

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

internal data class RubyHighlightBounds(val run: Int, val rect: Rect, val baseline: Float)
internal data class RubyHighlightLayout(val text: String = "", val ranges: List<AnnotatedString.Range<String>> = emptyList(), val lines: List<RubyHighlightBounds> = emptyList()) {
    fun matches(value: AnnotatedString) = text == value.text && ranges == value.getStringAnnotations("ruby-run", 0, value.length)
}

internal fun rubyHighlightLayout(layout: TextLayoutResult): RubyHighlightLayout {
    val text = layout.layoutInput.text
    val ranges = text.getStringAnnotations("ruby-run", 0, text.length)
    val lines = buildList {
        ranges.filter { it.start < it.end }.forEach { range ->
            for (line in layout.getLineForOffset(range.start)..layout.getLineForOffset(range.end - 1)) {
                val start = maxOf(range.start, layout.getLineStart(line))
                val end = minOf(range.end, layout.getLineEnd(line, visibleEnd = true))
                if (start < end) add(RubyHighlightBounds(range.item.toInt(),
                    rubyLineBounds(layout, start, end), layout.getLineBaseline(line)))
            }
        }
    }
    return RubyHighlightLayout(text.text, ranges, lines)
}

// Android selection paths can include the next line's leading edge at a wrap boundary.
// Character bounds keep the painted area aligned with the actual clickable text.
private fun rubyLineBounds(layout: TextLayoutResult, start: Int, end: Int): Rect {
    var bounds = layout.getBoundingBox(start)
    for (offset in start + 1 until end) {
        val next = layout.getBoundingBox(offset)
        bounds = Rect(minOf(bounds.left, next.left), minOf(bounds.top, next.top),
            maxOf(bounds.right, next.right), maxOf(bounds.bottom, next.bottom))
    }
    return bounds
}

internal fun DrawScope.drawRubyHighlights(layout: RubyHighlightLayout, runs: List<RubyRun>, ink: android.graphics.Rect) {
    layout.lines.forEach { bounds ->
        runs.getOrNull(bounds.run)?.takeIf { it.background != Color.Transparent }?.let { run ->
            drawRubyHighlight(bounds, run, ink)
        }
    }
}

private fun DrawScope.drawRubyHighlight(bounds: RubyHighlightBounds, run: RubyRun, ink: android.graphics.Rect) {
    val rect = bounds.rect
    val top = bounds.baseline + ink.top - 2.dp.toPx()
    val bottom = bounds.baseline + ink.bottom + 2.dp.toPx()
    drawRoundRect(run.background, Offset(rect.left - 1.dp.toPx(), top),
        Size(rect.width + 2.dp.toPx(), bottom - top), CornerRadius(4.dp.toPx()))
    run.underline?.let { underline ->
        drawLine(underline.color, Offset(rect.left, bottom), Offset(rect.right, bottom), 1.dp.toPx(),
            pathEffect = if (underline.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null)
    }
}

class RubyUnderline(val color: Color, val dashed: Boolean = false)
