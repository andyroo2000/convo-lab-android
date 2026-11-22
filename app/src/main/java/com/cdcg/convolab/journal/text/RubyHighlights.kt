package com.cdcg.convolab.journal.text

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

internal data class RubyHighlightBounds(val run: Int, val rect: androidx.compose.ui.geometry.Rect, val baseline: Float)
internal data class RubyHighlightLayout(val text: String = "", val lines: List<RubyHighlightBounds> = emptyList())

internal fun rubyHighlightLayout(layout: TextLayoutResult): RubyHighlightLayout {
    val text = layout.layoutInput.text
    val lines = buildList {
        text.getStringAnnotations("ruby-run", 0, text.length).filter { it.start < it.end }.forEach { range ->
            for (line in layout.getLineForOffset(range.start)..layout.getLineForOffset(range.end - 1)) {
                val start = maxOf(range.start, layout.getLineStart(line))
                val end = minOf(range.end, layout.getLineEnd(line, visibleEnd = true))
                if (start < end) add(RubyHighlightBounds(range.item.toInt(),
                    layout.getPathForRange(start, end).getBounds(), layout.getLineBaseline(line)))
            }
        }
    }
    return RubyHighlightLayout(text.text, lines)
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
