package com.cdcg.convolab.typing

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import com.cdcg.convolab.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class TimingSummary(val p50: Double, val p95: Double, val maximum: Double) {
    companion object {
        fun of(samples: List<Double>): TimingSummary {
            require(samples.isNotEmpty() && samples.all { it.isFinite() && it >= 0 })
            val sorted = samples.sorted()
            return TimingSummary(sorted[ceil(sorted.size * 0.5).toInt() - 1],
                sorted[ceil(sorted.size * 0.95).toInt() - 1], sorted.last())
        }
    }
}

class TypingBenchmark(private val context: Context, private val engine: TypingEngine) {
    suspend fun run(preloadMs: Double, progress: (Int, Int) -> Unit): String {
        val fixtures = JSONArray(context.assets.open("typing/fixtures.json").bufferedReader().use { it.readText() })
        val samples = JSONArray()
        val durations = mutableListOf<Double>()
        repeat(3) { pass ->
            for (index in 0 until fixtures.length()) {
                currentCoroutineContext().ensureActive()
                val fixture = fixtures.getJSONObject(index)
                val sample = measure(fixture, pass)
                durations += sample.getDouble("wallMs")
                samples.put(sample)
                progress(samples.length(), fixtures.length() * 3)
            }
        }
        val summary = TimingSummary.of(durations)
        return report(preloadMs, samples).put("p50WallMs", summary.p50)
            .put("p95WallMs", summary.p95).put("maxWallMs", summary.maximum)
            .put("targetP95Ms", 150).put("meetsTarget", summary.p95 <= 150).toString(2)
    }

    private suspend fun measure(fixture: JSONObject, pass: Int): JSONObject {
        val start = System.nanoTime()
        val result = engine.convert(fixture.getString("reading"))
        val wallMs = (System.nanoTime() - start) / 1e6
        val expected = fixture.getJSONArray("expected")
        val accepted = List(expected.length()) { expected.getString(it) }
        return JSONObject().put("pass", pass + 1).put("reading", fixture.getString("reading"))
            .put("category", fixture.getString("category")).put("candidates", JSONArray(result.candidates))
            .put("topAccepted", result.candidates.firstOrNull() in accepted)
            .put("anyAccepted", result.candidates.any { it in accepted })
            .put("engineMs", result.engineMs).put("wallMs", wallMs)
            .put("sampledPssKb", Debug.getPss())
    }

    private fun report(preloadMs: Double, samples: JSONArray) = JSONObject()
        .put("schema", 1).put("device", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
        .put("measuredAtEpochMs", System.currentTimeMillis())
        .put("abi", Build.SUPPORTED_ABIS.first()).put("emulator", Build.HARDWARE in listOf("ranchu", "goldfish") || Build.MODEL.startsWith("sdk_"))
        .put("hardware", Build.HARDWARE)
        .put("thermalStatusAtEnd", thermalStatus())
        .put("azooKeyRevision", "80b8204f1cdfb364bb2ed355cf52c7ebb2519a0c")
        .put("llamaRevision", "10131b23ee0dbd9d03dfc618cec9fe3402919277")
        .put("androidApi", Build.VERSION.SDK_INT).put("appVersion", BuildConfig.VERSION_NAME)
        .put("modelSha256", EngineAssets.MODEL_SHA).put("backend", "CPU / ${Build.SUPPORTED_ABIS.first()}")
        .put("preloadMs", preloadMs).put("samples", samples)
        .put("scope", "Public fixtures only. Excludes keyboard, debounce and rendering. PSS is sampled, not peak.")
    private fun thermalStatus(): Int = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(PowerManager::class.java).currentThermalStatus
    } else { -1 }

}
