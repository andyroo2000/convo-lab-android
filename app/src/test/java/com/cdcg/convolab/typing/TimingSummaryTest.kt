package com.cdcg.convolab.typing

import org.junit.Assert.assertEquals
import org.junit.Test

class TimingSummaryTest {
    @Test fun percentilesIncludeSlowTailRegardlessOfInputOrder() {
        val result = TimingSummary.of((1..100).reversed().map(Int::toDouble))
        assertEquals(50.0, result.p50, 0.0)
        assertEquals(95.0, result.p95, 0.0)
        assertEquals(100.0, result.maximum, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun emptySamplesAreNotAZeroLatencyPass() {
        TimingSummary.of(emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSamplesCannotPass() {
        TimingSummary.of(listOf(Double.NaN))
    }
}
