package com.cdcg.convolab.journal.feedback

import org.junit.Assert.*
import org.junit.Test

class ThinkingTimingTest {
    @Test fun illustrationsChangeInStaggeredSlots() {
        assertEquals(1, ThinkingTiming.frame(3.1f, 0).turn)
        assertEquals(0, ThinkingTiming.frame(3.1f, 1).turn)
        assertEquals(1, ThinkingTiming.frame(9.1f, 2).turn)
        for (tick in 10..900) {
            val frames = (0..2).map { ThinkingTiming.frame(tick / 10f, it) }
            assertTrue(frames.all { it.opacity in 0f..1f })
            assertTrue(frames.maxOf { it.opacity } > .5f)
        }
    }

    @Test fun successiveWaitingPhrasesDoNotRepeat() {
        var previous = ThinkingPhrases.next()
        repeat(40) {
            val next = ThinkingPhrases.next()
            assertNotEquals(previous, next)
            previous = next
        }
    }
}
