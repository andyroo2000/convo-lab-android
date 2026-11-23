package com.cdcg.convolab.journal.input

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import com.cdcg.convolab.ui.JournalBrand

/** Paint only the glyph-height portion of Compose's cursor, retaining native blinking and scrolling. */
internal fun journalCursorBrush(layout: TextLayoutResult?, selection: Int, fontSize: Float): Brush {
    if (layout == null) return SolidColor(Color.Transparent)
    val offset = selection.coerceIn(0, layout.layoutInput.text.length)
    val line = layout.getLineForOffset(offset)
    val bounds = layout.getCursorRect(offset)
    val baseline = layout.getLineBaseline(line)
    val top = maxOf(bounds.top, baseline - fontSize)
    val bottom = minOf(bounds.bottom, baseline + fontSize * .2f)
    return Brush.verticalGradient(
        0f to Color.Transparent, .001f to JournalBrand.Blue,
        .999f to JournalBrand.Blue, 1f to Color.Transparent,
        startY = top, endY = bottom,
    )
}
