package com.cdcg.convolab.typing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingResponsivenessTest {
    private class Engine : TypingEngine {
        val requests = mutableListOf<String>()
        var pending: CompletableDeferred<Conversion>? = null
        override suspend fun prepare() = Unit
        override suspend fun close() = Unit
        override suspend fun convert(text: String): Conversion {
            requests += text
            return pending?.let { withContext(NonCancellable) { it.await() } }
                ?: Conversion(text, listOf("候補$text"), 1.0)
        }
    }

    @Test fun freshInputStartsConversionAfter35Milliseconds() = runTest {
        val engine = Engine()
        val session = TypingSession(engine, backgroundScope)
        session.edit("nihon")
        advanceTimeBy(34)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("nihon"), engine.requests)
        assertEquals("nihon", session.state.value.conversion?.reading)
    }

    @Test fun revisitingInputPublishesImmediatelyWithoutNativeWork() = runTest {
        val engine = Engine()
        val session = TypingSession(engine, backgroundScope)
        for (text in listOf("ni", "nihon")) {
            session.edit(text)
            advanceTimeBy(36)
            runCurrent()
        }
        session.edit("ni")
        assertEquals("ni", session.state.value.conversion?.reading)
        assertFalse(session.state.value.busy)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("ni", "nihon"), engine.requests)
        assertNull(session.commit("候補nihon"))
        assertEquals("候補ni", session.commit("候補ni"))
        session.edit("ni")
        assertNull(session.state.value.conversion)
        advanceTimeBy(36)
        runCurrent()
        assertEquals(listOf("ni", "nihon", "ni"), engine.requests)
    }

    @Test fun oldestEntryIsEvictedAfter32Results() = runTest {
        val engine = Engine()
        val session = TypingSession(engine, backgroundScope)
        for (index in 0..32) {
            session.edit("word$index")
            advanceTimeBy(36)
            runCurrent()
        }
        session.edit("word1")
        assertEquals("word1", session.state.value.conversion?.reading)
        session.edit("word0")
        assertNull(session.state.value.conversion)
        advanceTimeBy(36)
        runCurrent()
        assertEquals(34, engine.requests.size)
    }

    @Test fun cancelledResultCannotReplaceCacheHitOrRepopulateClearedCache() = runTest {
        val engine = Engine()
        val session = TypingSession(engine, backgroundScope)
        session.edit("ni")
        advanceTimeBy(36)
        runCurrent()
        val old = CompletableDeferred<Conversion>()
        engine.pending = old
        session.edit("old")
        advanceTimeBy(36)
        runCurrent()
        session.edit("ni")
        old.complete(Conversion("old", listOf("古い"), 1.0))
        runCurrent()
        assertEquals("ni", session.state.value.conversion?.reading)
        session.edit("")
        engine.pending = null
        session.edit("old")
        assertNull(session.state.value.conversion)
        advanceTimeBy(36)
        runCurrent()
        assertEquals(listOf("ni", "old", "old"), engine.requests)
    }
}
