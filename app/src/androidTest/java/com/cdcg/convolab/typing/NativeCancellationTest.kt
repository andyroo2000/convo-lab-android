package com.cdcg.convolab.typing

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeCancellationTest {
    @Test fun cancellingBoundaryInferenceReleasesTheEngineForTheNextKeystroke() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handle = NativeTyping.create(EngineAssets(context).prepare().absolutePath.toByteArray(Charsets.UTF_8))
        assertNotEquals(0L, handle)
        val request = JSONObject().put("reading", "あしたははしをわたります").put("expanded", true).put("boundaries", JSONArray(listOf(4, 7)))
        val entered = CompletableDeferred<Unit>()
        val work = launch(Dispatchers.Default) { NativeOperation.mutex.withLock { NativeOperation.run(handle) { entered.complete(Unit); call(handle, request) } } }
        try {
            entered.await()
            delay(100)
            assertTrue("Fixture must still be doing its cold boundary scoring", work.isActive)
            val start = System.nanoTime()
            work.cancelAndJoin()
            val elapsed = (System.nanoTime() - start) / 1e6
            assertTrue("Cancellation held the engine for $elapsed ms", elapsed < 1500)
            val next = NativeOperation.mutex.withLock { NativeOperation.run(handle) { call(handle, JSONObject().put("reading", "にほんご")) } }
            assertFalse(next.toString(), next.has("error"))
            assertTrue(next.getJSONArray("candidates").length() > 0)
            TestOutput.file("native-cancellation.json").writeText(JSONObject().put("cancelMs", elapsed).toString(2))
        } finally { work.cancelAndJoin(); NativeTyping.destroy(handle) }
    }
    private fun call(handle: Long, request: JSONObject) = JSONObject(checkNotNull(NativeTyping.convert(handle,
        request.toString().toByteArray(Charsets.UTF_8))).toString(Charsets.UTF_8))
}
