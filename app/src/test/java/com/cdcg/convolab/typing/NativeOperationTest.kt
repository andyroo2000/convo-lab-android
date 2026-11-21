package com.cdcg.convolab.typing

import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class NativeOperationTest {
    @Test fun nativeCancellationErrorRemainsCoroutineCancellationAndJoinsWatcher() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val released = CountDownLatch(1)
        val work = async {
            runCancellableNative({}, { released.countDown() }) {
                entered.complete(Unit)
                check(released.await(5, TimeUnit.SECONDS))
                error("Native cancelled payload")
            }
        }
        entered.await()
        work.cancel()
        try { work.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(0L, released.count)
        assertEquals("next", runCancellableNative({}, {}, { "next" }))
    }
    @Test fun consecutiveNormalCallsFinishWatcherBeforeNextBegin() = runBlocking {
        val events = mutableListOf<String>()
        repeat(2) {
            assertEquals(42, runCancellableNative({ events += "begin" }, { events += "cancel" }, { 42 }))
        }
        assertEquals(listOf("begin", "cancel", "begin", "cancel"), events)
    }
}
