package com.cdcg.convolab.typing

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public synthetic sentences only; captures numerical and timing evidence. */
class BoundaryScoringProbeTest {
    // Match interactive typing's foreground CPU scheduling, including on physical phones.
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    // Oversubscribing emulator vCPUs measures scheduler starvation, not inference.
    // Keep the full sweep on phones with >= 6 cores, and match the engine's cap elsewhere.
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val threadCounts = listOf(2, 4, 6).map { it.coerceAtMost(cores) }.distinct()
    private val poolThreads = (InstrumentationRegistry.getArguments().getString("poolThreads")?.toInt() ?: 4)
        .coerceIn(1, minOf(8, cores))
    @Test fun comparePrefixReuseAndBatchingWithUnchangedUpstreamScores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handle = NativeTyping.create(EngineAssets(context).prepare().absolutePath.toByteArray(Charsets.UTF_8))
        assertNotEquals(0L, handle)
        val reports = JSONArray()
        try {
            for (threads in threadCounts) {
                for ((reading, texts) in phrases) {
                    val request = JSONObject().put("action", "benchmarkBoundary").put("reading", reading)
                        .put("texts", JSONArray(texts)).put("threads", threads)
                    val bytes = checkNotNull(NativeTyping.convert(handle, request.toString().toByteArray(Charsets.UTF_8)))
                    val result = JSONObject(bytes.toString(Charsets.UTF_8))
                    assertFalse(result.toString(), result.has("error"))
                    result.put("reading", reading).put("texts", JSONArray(texts)).put("threads", threads)
                    reports.put(result)
                    TestOutput.file("boundary-scoring-probe.json").writeText(reports.toString(2))
                    assertScoresMatch(result, texts.size)
                }
            }
        } finally { NativeTyping.destroy(handle) }
    }
    @Test fun completeBoundaryPoolKeepsReferenceScoresRankingAndWarmCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handle = NativeTyping.create(EngineAssets(context).prepare().absolutePath.toByteArray(Charsets.UTF_8))
        assertNotEquals(0L, handle)
        try {
            val request = JSONObject().put("reading", "はしでたべました").put("expanded", true).put("boundaries", JSONArray(listOf(2)))
            val first = call(handle, request)
            val candidates = first.getJSONArray("candidates")
            assertEquals(160, candidates.length())
            val texts = JSONArray((0 until candidates.length()).map { candidates.getJSONObject(it).getString("text") })
            val probe = call(handle, JSONObject().put("action", "benchmarkBoundary").put("reading", request.getString("reading"))
                .put("texts", texts).put("threads", poolThreads))
            probe.put("texts", texts).put("coldEngineMs", first.getDouble("engineMs"))
                .put("backend", NativeTyping.backend()).put("threads", poolThreads)
            TestOutput.file("boundary-full-pool.json").writeText(probe.toString(2))
            assertScoresMatch(probe, 160)
            val repeated = call(handle, request)
            assertEquals(candidateOrder(candidates), candidateOrder(repeated.getJSONArray("candidates")))
            assertTrue(repeated.getDouble("engineMs") < 1000)
            probe.put("warmEngineMs", repeated.getDouble("engineMs"))
            TestOutput.file("boundary-full-pool.json").writeText(probe.toString(2))
        } finally { NativeTyping.destroy(handle) }
    }
    private fun candidateOrder(candidates: JSONArray) = (0 until candidates.length()).map { candidates.getJSONObject(it).getString("text") to candidates.getJSONObject(it).getDouble("neuralScore") }

    private fun call(handle: Long, request: JSONObject): JSONObject {
        val result = JSONObject(checkNotNull(NativeTyping.convert(handle, request.toString().toByteArray(Charsets.UTF_8))).toString(Charsets.UTF_8))
        assertFalse(result.toString(), result.has("error"))
        return result
    }
    private fun assertScoresMatch(result: JSONObject, count: Int) {
        val reference = result.getJSONArray("reference")
        val optimized = result.getJSONArray("optimized")
        assertEquals(count, reference.length())
        assertEquals(count, optimized.length())
        for (index in 0 until count) assertEquals("score $index", reference.getDouble(index), optimized.getDouble(index), 0.0001)
        assertEquals((0 until count).sortedByDescending { reference.getDouble(it) }, (0 until count).sortedByDescending { optimized.getDouble(it) })
    }
    private val phrases = linkedMapOf(
        "はしでたべました" to listOf("箸で食べました", "橋で食べました", "端で食べました", "はしで食べました", "箸でたべました", "橋でたべました", "箸で食べ増した", "端でたべました"),
        "きょうはいいてんきです" to listOf("今日はいい天気です", "今日は良い天気です", "きょうはいい天気です", "今日は良い転機です", "今日はい良い天気です", "京はいい天気です", "今日はい胃天気です", "きょうは良い天気です"),
        "かみをきりました" to listOf("髪を切りました", "紙を切りました", "神を切りました", "かみを切りました", "紙をきりました", "髪をきりました", "上を切りました", "紙を切り増した")
    )
}
