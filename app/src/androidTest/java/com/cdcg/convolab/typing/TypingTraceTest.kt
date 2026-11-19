package com.cdcg.convolab.typing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypingTraceTest {
    private class CountedEngine(private val delegate: TypingEngine) : TypingEngine by delegate {
        var calls = 0
        override suspend fun convert(text: String): Conversion { calls++; return delegate.convert(text) }
    }

    @Test fun appendingDeletingAndRetypingProducesCurrentCandidates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = CountedEngine(OnDeviceEngine(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val session = TypingSession(engine, scope)
        val samples = JSONArray()
        try {
            withContext(Dispatchers.Main) { session.prepare() }
            withTimeout(90_000) { session.state.first { it.ready } }
            val phrases = mapOf("nihongowobenkyoushiteimasu" to "にほんごをべんきょうしています",
                "kinoueigawomimashita" to "きのうえいがをみました", "hashiwowatarimashita" to "はしをわたりました")
            for ((phrase, reading) in phrases) {
                withContext(Dispatchers.Main) { session.edit("") }
                tracePhrase(session, phrase, samples)
                assertEquals(reading, session.state.value.conversion?.reading)
            }
            assertEquals("Repeated edits must avoid native work", phrases.keys.sumOf { it.length }, engine.calls)
            TestOutput.file("typing-trace.json").writeText(JSONObject()
                .put("nativeCalls", engine.calls).put("samples", samples)
                .put("scope", "Public romaji edits through the UI session. Includes scheduling/debounce/conversion; excludes keyboard and drawing.")
                .toString(2))
        } finally {
            scope.cancel()
            engine.close()
        }
    }

    private suspend fun tracePhrase(session: TypingSession, phrase: String, samples: JSONArray) {
        val firstResults = mutableMapOf<String, String>()
        for (text in edits(phrase)) {
            val measured = sample(session, text, text in firstResults)
            val candidates = measured.getString("candidates")
            firstResults[text]?.let { assertEquals("Cached candidates for $text", it, candidates) }
            firstResults[text] = candidates
            samples.put(measured)
        }
    }

    private fun edits(phrase: String): List<String> =
        (1..phrase.length).map { phrase.take(it) } +
            (1..8).map { phrase.dropLast(it) } +
            (7 downTo 0).map { phrase.dropLast(it) }

    private suspend fun sample(session: TypingSession, text: String, revisited: Boolean): JSONObject {
        val start = System.nanoTime()
        withContext(Dispatchers.Main) { session.edit(text) }
        val state = withTimeout(30_000) { session.state.first { it.input == text && it.conversion != null } }
        val elapsed = (System.nanoTime() - start) / 1e6
        val result = requireNotNull(state.conversion)
        return JSONObject().put("text", text).put("reading", result.reading)
            .put("firstCandidate", result.candidates.firstOrNull()).put("candidates", JSONArray(result.candidates).toString())
            .put("revisited", revisited)
            .put("elapsedMs", elapsed).put("engineMs", result.engineMs).put("resetMs", result.resetMs)
    }
}
