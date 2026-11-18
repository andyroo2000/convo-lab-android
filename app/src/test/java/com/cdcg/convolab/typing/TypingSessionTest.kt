package com.cdcg.convolab.typing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingSessionTest {
    private class FakeEngine : TypingEngine {
        val requests = mutableListOf<String>()
        var delayed: CompletableDeferred<Conversion>? = null
        override suspend fun prepare() = Unit
        override suspend fun close() = Unit
        override suspend fun convert(text: String): Conversion {
            requests += text
            val pending = delayed
            return if (pending == null) Conversion(text, listOf("候補$text"), 1.0)
            else withContext(NonCancellable) { pending.await() }
        }
    }

    @Test fun rapidTypingCoalescesBeforeNativeWork() = runTest {
        val engine = FakeEngine()
        val session = TypingSession(engine, backgroundScope)
        session.edit("n")
        advanceTimeBy(30)
        session.edit("ni")
        advanceTimeBy(30)
        session.edit("nihon")
        advanceTimeBy(81)
        runCurrent()
        assertEquals(listOf("nihon"), engine.requests)
        assertEquals("nihon", session.state.value.conversion?.reading)
    }

    @Test fun staleNativeResultCannotOverwriteNewInputOrClear() = runTest {
        val engine = FakeEngine()
        val old = CompletableDeferred<Conversion>()
        engine.delayed = old
        val session = TypingSession(engine, backgroundScope)
        session.edit("old")
        advanceTimeBy(81)
        runCurrent()
        session.edit("")
        old.complete(Conversion("old", listOf("古い"), 100.0))
        runCurrent()
        assertEquals("", session.state.value.input)
        assertNull(session.state.value.conversion)
        assertFalse(session.state.value.busy)
    }

    @Test fun commitsOnlyCurrentCandidatesAndClearsComposition() = runTest {
        val session = TypingSession(FakeEngine(), backgroundScope)
        session.edit("nihon")
        advanceTimeBy(81)
        runCurrent()
        assertNull(session.commit("stale"))
        assertEquals("候補nihon", session.commit("候補nihon"))
        assertNull(session.commit("候補nihon"))
        assertEquals("", session.state.value.input)
    }

    @Test fun rejectsOversizedPasteAndControlCharactersWithoutLosingInput() = runTest {
        val session = TypingSession(FakeEngine(), backgroundScope)
        session.edit("nihon")
        session.edit("x".repeat(TypingLimits.MAX_INPUT + 1))
        assertEquals("nihon", session.state.value.input)
        assertNotNull(session.state.value.error)
        session.edit("nihon") // Selection/composition update from the IME.
        assertNotNull(session.state.value.error)
        assertFalse(TypingLimits.accepts("a\nb"))
        assertFalse(TypingLimits.accepts("a\u0000b"))
        assertTrue(TypingLimits.accepts("日本😊"))
    }

    @Test fun conversionFailureIsRecoverable() = runTest {
        val engine = object : TypingEngine {
            override suspend fun prepare() = Unit
            override suspend fun close() = Unit
            override suspend fun convert(text: String): Conversion = error("Failed")
        }
        val session = TypingSession(engine, backgroundScope)
        session.edit("nihon")
        advanceTimeBy(81)
        runCurrent()
        assertNotNull(session.state.value.error)
        assertFalse(session.state.value.busy)
        session.edit("")
        assertNull(session.state.value.error)
    }
    @Test fun preparationCanRetryAndDoesNotRestartOnceReady() = runTest {
        var attempts = 0
        val engine = object : TypingEngine {
            override suspend fun prepare() { if (++attempts == 1) error("Temporary failure") }
            override suspend fun close() = Unit
            override suspend fun convert(text: String) = Conversion(text, emptyList(), 0.0)
        }
        val session = TypingSession(engine, backgroundScope)
        session.prepare()
        runCurrent()
        assertFalse(session.state.value.ready)
        assertNotNull(session.state.value.error)
        session.prepare()
        runCurrent()
        assertTrue(session.state.value.ready)
        assertNull(session.state.value.error)
        session.prepare()
        runCurrent()
        assertEquals(2, attempts)
    }

}
