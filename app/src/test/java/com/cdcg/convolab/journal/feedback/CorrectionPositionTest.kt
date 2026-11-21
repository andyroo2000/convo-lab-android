package com.cdcg.convolab.journal.feedback

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class CorrectionPositionTest {
    private val window = IntSize(400, 800)
    private val bubble = IntSize(340, 180)

    @Test fun pointsDownToTheSelectedSentenceWhenThereIsRoomAbove() {
        var pointer: PopoverPointer? = null
        val provider = CorrectionPosition(Rect(20f, 0f, 100f, 30f), 16) { pointer = it }
        val result = provider.calculatePosition(IntRect(10, 400, 390, 450), window, LayoutDirection.Ltr, bubble)
        assertEquals(IntOffset(16, 220), result)
        assertEquals(PopoverPointer(false, 54), pointer)
    }

    @Test fun opensBelowTextNearTheTopEdge() {
        var pointer: PopoverPointer? = null
        val provider = CorrectionPosition(Rect(0f, 0f, 80f, 30f), 16) { pointer = it }
        val result = provider.calculatePosition(IntRect(24, 32, 376, 90), window, LayoutDirection.Ltr, bubble)
        assertEquals(62, result.y)
        assertEquals(true, pointer?.top)
    }

    @Test fun rightEdgeAndLongContentRemainInsideWindowInEitherDirection() {
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            val provider = CorrectionPosition(Rect(270f, 0f, 340f, 30f), 16) {}
            val result = provider.calculatePosition(IntRect(20, 600, 380, 640), window, direction, IntSize(340, 650))
            assertEquals(44, result.x)
            assertEquals(134, result.y)
            assertTrue(result.x + 340 <= window.width - 16)
            assertTrue(result.y + 650 <= window.height - 16)
        }
    }
}
