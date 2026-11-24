package com.cdcg.convolab.journal.text

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** Separate paint at a shared correction boundary without moving text or tap targets. */
internal fun rubyHighlightRect(bounds: RubyHighlightBounds, highlights: List<RubyHighlightBounds>, padding: Float, gap: Float): Rect {
    val rect = bounds.rect
    val neighbors = highlights.filter { it.run != bounds.run && abs(it.baseline - bounds.baseline) < .5f }
    val inset = minOf(gap / 2, rect.width / 4)
    val left = if (neighbors.any { abs(it.rect.right - rect.left) < .5f }) rect.left + inset else rect.left - padding
    val right = if (neighbors.any { abs(it.rect.left - rect.right) < .5f }) rect.right - inset else rect.right + padding
    return Rect(left, rect.top, right, rect.bottom)
}
