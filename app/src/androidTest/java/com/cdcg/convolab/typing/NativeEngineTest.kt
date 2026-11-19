package com.cdcg.convolab.typing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun romajiAndKanaUseTheRealBundledEngine() = runBlocking {
        val engine = OnDeviceEngine(context)
        try {
            engine.prepare()
            val cases = mapOf("nihongo" to "にほんご", "gakkou" to "がっこう",
                "kanpai" to "かんぱい", "shin'you" to "しんよう", "nyuuryoku" to "にゅうりょく",
                "hon" to "ほん", "NIHON" to "にほん", "日本😊" to "日本😊")
            cases.forEach { (input, reading) -> assertEquals(input, reading, engine.convert(input).reading) }
            assertTrue(engine.convert("nihongo").candidates.contains("日本語"))
            assertTrue(engine.convert("きのうえいがをみました").candidates.any { it.contains("映画") })
            val first = engine.convert("nihongo").candidates
            engine.convert("あめをたべました")
            assertEquals(first, engine.convert("nihongo").candidates)
        } finally {
            engine.close()
        }
    }

    @Test fun benchmarkExportsPublicFixturesAndEnforcesRegressionCeiling() = runBlocking {
        val engine = OnDeviceEngine(context)
        val start = System.nanoTime()
        try {
            engine.prepare()
            val report = TypingBenchmark(context, engine).run((System.nanoTime() - start) / 1e6) { _, _ -> }
            TestOutput.file("typing-benchmark.json").writeText(report)
            val json = JSONObject(report)
            val samples = json.getJSONArray("samples")
            assertEquals(72, samples.length())
            val accepted = (0 until samples.length()).count { samples.getJSONObject(it).getBoolean("anyAccepted") }
            assertTrue("Candidate quality regressed: $accepted / 72", accepted >= 54)
            // Broad smoke ceiling catches stalls. The 150 ms usability target is reported separately.
            assertTrue("Conversion p95 exceeds 2 seconds", json.getDouble("p95WallMs") < 2000)
        } finally {
            engine.close()
        }
    }
}
