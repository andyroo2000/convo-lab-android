package com.cdcg.convolab.journal.text

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class RubyHighlightSpacingTest {
    @Test fun adjacentCorrectionsHaveSixPixelsOfPaperBetweenTheirPaint() {
        val first = bounds(0, 0f, 80f)
        val second = bounds(1, 80f, 160f)
        val pair = listOf(first, second)
        val left = rubyHighlightRect(first, pair, 1f, 6f)
        val right = rubyHighlightRect(second, pair, 1f, 6f)
        assertEquals(6f, right.left - left.right, .01f)
        assertEquals(-1f, left.left, .01f)
        assertEquals(161f, right.right, .01f)
    }

    @Test fun wrappedAndSeparatedFragmentsKeepTheirNormalPadding() {
        val first = bounds(0, 0f, 80f)
        for (other in listOf(bounds(0, 80f, 160f), bounds(1, 80f, 160f, 90f), bounds(1, 100f, 160f))) {
            assertEquals(Rect(-1f, 0f, 81f, 30f), rubyHighlightRect(first, listOf(first, other), 1f, 6f))
        }
    }

    @Test fun narrowCorrectionsKeepPositivePaintWidth() {
        val first = bounds(0, 0f, 2f)
        val second = bounds(1, 2f, 4f)
        assertTrue(rubyHighlightRect(first, listOf(first, second), 1f, 6f).width > 0)
    }

    private fun bounds(run: Int, left: Float, right: Float, baseline: Float = 30f) =
        RubyHighlightBounds(run, Rect(left, 0f, right, 30f), baseline)
}
