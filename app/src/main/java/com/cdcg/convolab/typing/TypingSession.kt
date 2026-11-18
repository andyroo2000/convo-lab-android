package com.cdcg.convolab.typing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TypingState(
    val ready: Boolean = false,
    val input: String = "",
    val conversion: Conversion? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val preloadMs: Double = 0.0,
)

class TypingSession(private val engine: TypingEngine, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(TypingState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var revision = 0L
    private var preparation: Job? = null

    fun prepare() {
        if (preparation?.isActive == true || state.value.ready) return
        mutable.update { it.copy(error = null) }
        preparation = scope.launch { prepareEngine() }
    }

    private suspend fun prepareEngine() {
        val start = System.nanoTime()
        try {
            engine.prepare()
            mutable.update { it.copy(ready = true, preloadMs = (System.nanoTime() - start) / 1e6) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutable.update { it.copy(error = "The typing engine could not start. Tap Retry to try again.") }
        }
    }

    fun edit(text: String) {
        if (!TypingLimits.accepts(text)) {
            mutable.update { it.copy(error = "Use one phrase of up to ${TypingLimits.MAX_INPUT} characters.") }
            return
        }
        request?.cancel()
        val ticket = ++revision
        mutable.update { it.copy(input = text, conversion = null, busy = text.isNotBlank(), error = null) }
        if (text.isBlank()) return
        request = scope.launch { convertLatest(text, ticket) }
    }

    private suspend fun convertLatest(text: String, ticket: Long) {
        try {
            delay(80)
            val result = engine.convert(text)
            if (ticket == revision) mutable.update { it.copy(conversion = result, busy = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (ticket == revision) mutable.update {
                it.copy(busy = false, error = "Conversion failed. Try a shorter phrase.")
            }
        }
    }

    fun commit(candidate: String): String? {
        val conversion = state.value.conversion ?: return null
        if (candidate != conversion.reading && candidate !in conversion.candidates) return null
        edit("")
        return candidate
    }
}
