package com.cdcg.convolab.typing

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Calls are deliberately sequential, as in the production native owner. */
class NativeJournalEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun expandedChoicesRetainWholePhraseAndRequestedSectionBoundaries() {
        NativeJournalFixture(context.noBackupFilesDir, EngineAssets(context).prepare()).use { fixture ->
            val reading = "あしたははしをわたります"
            val ordinary = fixture.convert(reading)
            val expanded = fixture.convert(reading, expanded = true)
            assertTrue(ordinary.getJSONArray("candidates").length() > 0)
            assertTrue(expanded.getJSONArray("candidates").length() > 10)
            assertEquals("whole-phrase-neural-and-dictionary", expanded.getString("expansion"))
            val adjusted = fixture.convert(reading, boundaries = listOf(4, 7))
            val candidates = adjusted.getJSONArray("candidates")
            assertTrue(candidates.length() > 0)
            for (index in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(index)
                assertEquals(reading, candidate.getString("reading"))
                val sections = candidate.getJSONArray("sections")
                assertEquals(candidate.getString("text"), joined(sections, "text"))
                assertEquals(reading, joined(sections, "reading"))
                val units = candidate.getJSONArray("units")
                var offset = 0
                val edges = (0 until units.length()).map { offset += units.getJSONObject(it).getString("reading").length; offset }
                assertTrue(edges.containsAll(listOf(4, 7)))
            }
            TestOutput.file("journal-native-expanded.json").writeText(expanded.toString(2))
            TestOutput.file("journal-native-boundaries.json").writeText(adjusted.toString(2))
        }
    }

    @Test fun onlyConfirmedCandidatesLearnAndLearningSurvivesRestartUntilReset() {
        NativeJournalFixture(context.noBackupFilesDir, EngineAssets(context).prepare()).use { fixture ->
            val reading = "はし"
            val original = fixture.convert(reading).getJSONArray("candidates").getJSONObject(0).getString("text")
            val pool = fixture.convert(reading, expanded = true).getJSONArray("candidates")
            val alternative = (0 until pool.length()).map { pool.getJSONObject(it).getString("text") }
                .first { it != original && it in listOf("橋", "箸", "端") }
            assertFalse(fixture.learn(reading, "invented choice").getBoolean("learned"))
            assertTrue(fixture.learn(reading, alternative).getBoolean("learned"))
            assertEquals(alternative, fixture.convert(reading).getJSONArray("candidates").getJSONObject(0).getString("text"))
            fixture.restart()
            assertEquals(alternative, fixture.convert(reading).getJSONArray("candidates").getJSONObject(0).getString("text"))
            assertTrue(fixture.request(JSONObject().put("action", "resetLearning")).getBoolean("reset"))
            assertFalse(File(fixture.learning, "confirmed.json").exists())
            assertEquals(original, fixture.convert(reading).getJSONArray("candidates").getJSONObject(0).getString("text"))
        }
    }

    @Test fun failedLearningResetReportsErrorAndPreservesPreferencesForRetry() {
        NativeJournalFixture(context.noBackupFilesDir, EngineAssets(context).prepare()).use { fixture ->
            fixture.convert("はし", expanded = true)
            assertTrue(fixture.learn("はし", "橋").getBoolean("learned"))
            assertTrue(fixture.learning.setWritable(false, false))
            try {
                assertTrue(fixture.request(JSONObject().put("action", "resetLearning")).has("error"))
                assertTrue(File(fixture.learning, "confirmed.json").exists())
            } finally { assertTrue(fixture.learning.setWritable(true, true)) }
            assertEquals("橋", fixture.convert("はし").getJSONArray("candidates").getJSONObject(0).getString("text"))
            assertTrue(fixture.request(JSONObject().put("action", "resetLearning")).getBoolean("reset"))
            assertFalse(File(fixture.learning, "confirmed.json").exists())
        }
    }

    @Test fun invalidBoundariesAndOversizedReadingFailWithoutBreakingTheNextRequest() {
        NativeJournalFixture(context.noBackupFilesDir, EngineAssets(context).prepare()).use { fixture ->
            assertTrue(fixture.convert("はし", boundaries = listOf(1, 1)).has("error"))
            assertTrue(fixture.convert("😊はし", boundaries = listOf(1)).has("error"))
            assertTrue(fixture.convert("あ".repeat(121)).has("error"))
            assertTrue(fixture.convert("にほんご").getJSONArray("candidates").length() > 0)
        }
    }
    private fun joined(parts: JSONArray, key: String) = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).getString(key) }
}

private class NativeJournalFixture(root: File, assets: File) : AutoCloseable {
    val learning = File(root, "typing-test-learning/${UUID.randomUUID()}")
    private val configuration = JSONObject().put("directory", assets.absolutePath).put("learningDirectory", learning.absolutePath)
    private var handle = create()
    private fun create(): Long = NativeTyping.create(configuration.toString().toByteArray(Charsets.UTF_8)).also { check(it != 0L) }
    fun restart() { NativeTyping.destroy(handle); handle = 0; handle = create() }
    fun request(request: JSONObject): JSONObject {
        val bytes = checkNotNull(NativeTyping.convert(handle, request.toString().toByteArray(Charsets.UTF_8)))
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }
    fun convert(reading: String, expanded: Boolean = false, boundaries: List<Int> = emptyList()) =
        request(JSONObject().put("reading", reading).put("expanded", expanded).put("boundaries", JSONArray(boundaries)))
    fun learn(reading: String, text: String) = request(JSONObject().put("action", "learn").put("reading", reading)
        .put("text", text).put("start", 0).put("end", reading.length))
    override fun close() { if (handle != 0L) NativeTyping.destroy(handle); learning.deleteRecursively() }
}
