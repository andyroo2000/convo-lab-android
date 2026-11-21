package com.cdcg.convolab.journal.audio

import org.junit.Assert.*
import org.junit.Test

class AudioTimelineTest {
    private val sentences = listOf(SentenceTiming("日本へ。", 0.0, 2.0), SentenceTiming("🙂楽しい。", 2.5, 5.0))
    @Test fun exactTranscriptAndFiniteOrderedTimesAreRequiredForHighlighting() {
        assertEquals(sentences, AudioTimeline.validated(sentences, "日本へ。🙂楽しい。"))
        assertTrue(AudioTimeline.validated(sentences, "別の文章。").isEmpty())
        assertTrue(AudioTimeline.validated(sentences.map { it.copy(start = Double.NaN) }, "日本へ。🙂楽しい。").isEmpty())
        assertTrue(AudioTimeline.validated(sentences.reversed(), "🙂楽しい。日本へ。").isEmpty())
        assertNull(AudioTimeline.current(sentences, 2.2))
        assertEquals(1, AudioTimeline.current(sentences, 3.0))
        assertEquals(0.0, AudioTimeline.adjacent(sentences, 0.0, -1))
        assertEquals(2.5, AudioTimeline.adjacent(sentences, 0.0, 1))
        assertEquals(2.5, AudioTimeline.adjacent(sentences, 8.0, 1))
    }
    @Test fun unknownDurationDoesNotRewindAndKnownSeeksStayWithinTheRecording() {
        assertNull(AudioTimeline.seekPosition(1000, -9223372036854775807L))
        assertNull(AudioTimeline.seekPosition(1000, 0))
        assertEquals(5000L, AudioTimeline.seekPosition(9000, 5000))
        assertEquals(0L, AudioTimeline.seekPosition(-1, 5000))
    }
    @Test fun rubyAndEmojiArePreservedAtSentenceBoundaries() {
        assertEquals(listOf("日本[にほん]へ。", "🙂楽[たの]しい。"), AudioTimeline.readings("日本[にほん]へ。🙂楽[たの]しい。", sentences))
        assertNull(AudioTimeline.readings("違[ちが]う文章。", sentences))
        val split = listOf(SentenceTiming("日", 0.0, 1.0), SentenceTiming("本", 1.0, 2.0))
        assertEquals(listOf("日", "本"), AudioTimeline.readings("日本[にほん]", split))
    }
}
