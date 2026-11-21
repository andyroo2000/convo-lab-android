package com.cdcg.convolab.journal.input

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class JournalInputLifetimeTest {
    @Test fun closingDrainsConfirmedLearningBeforeDestroyingEngine() = runBlocking {
        val engine = LifetimeEngine()
        val session = session(engine)
        withContext(Dispatchers.Main) { session.prepare() }
        await { session.ready }
        engine.learnGate = CompletableDeferred()
        withContext(Dispatchers.Main) {
            session.confirm(listOf(ConfirmedInput("にほんご", "日本語", 0, 4)))
            session.close()
        }
        delay(50)
        assertFalse(engine.closed)
        assertFalse(session.lifetime.isCompleted)
        engine.learnGate!!.complete(Unit)
        withTimeout(5_000) { session.lifetime.join() }
        assertTrue(engine.learned)
        assertTrue(engine.closed)
    }
    @Test fun cancelledQueryCannotPublishAfterConfirmation() = runBlocking {
        val engine = LifetimeEngine()
        engine.queryGate = CompletableDeferred()
        val session = session(engine)
        var received = false
        withContext(Dispatchers.Main) { session.query(CompositionQuery("にほんご", emptyList(), 1)) { received = true } }
        withTimeout(5_000) { engine.queryEntered.await() }
        withContext(Dispatchers.Main) { session.confirm(listOf(ConfirmedInput("にほんご", "日本語", 0, 4))) }
        engine.queryGate!!.complete(Unit)
        await { engine.returned }
        assertFalse(received)
        withContext(Dispatchers.Main) { session.close() }
        session.lifetime.join()
    }
    @Test fun failedPreparationCanRetryAndResetFailureIsNotSuccess() = runBlocking {
        val engine = LifetimeEngine().apply { failPrepare = true }
        val session = session(engine)
        withContext(Dispatchers.Main) { session.prepare() }
        await { session.error != null }
        assertFalse(session.ready)
        engine.failPrepare = false
        withContext(Dispatchers.Main) { session.retry() }
        await { session.ready }
        assertNull(session.error)
        engine.failReset = true
        withContext(Dispatchers.Main) { session.reset() }
        await { session.error != null }
        assertNull(session.status)
        withContext(Dispatchers.Main) { session.close() }
        session.lifetime.join()
    }
    private fun session(engine: LifetimeEngine): JournalInputSession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return JournalInputSession(KeyboardPreference(context.getSharedPreferences("lifetime-${UUID.randomUUID()}", Context.MODE_PRIVATE)), engine)
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { condition() }) delay(10)
    }
}

private class LifetimeEngine : JournalTypingEngine {
    var learnGate: CompletableDeferred<Unit>? = null
    var queryGate: CompletableDeferred<Unit>? = null
    val queryEntered = CompletableDeferred<Unit>()
    var learned = false
    var closed = false
    var returned = false
    var failPrepare = false
    var failReset = false
    override suspend fun prepare() { check(!failPrepare) }
    override suspend fun candidates(query: CompositionQuery, expanded: Boolean): List<JapaneseCandidate> = withContext(NonCancellable) {
        queryEntered.complete(Unit)
        queryGate?.await()
        returned = true
        emptyList()
    }
    override suspend fun learn(choice: ConfirmedInput) { learnGate?.await(); learned = true }
    override suspend fun reset() { check(!failReset) }
    override suspend fun close() { closed = true }
}
