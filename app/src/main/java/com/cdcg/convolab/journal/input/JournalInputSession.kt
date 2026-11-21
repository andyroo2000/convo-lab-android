package com.cdcg.convolab.journal.input

import androidx.compose.runtime.*
import kotlinx.coroutines.*

/** Account-owned worker. Document updates never wait for this worker. */
class JournalInputSession(val preference: KeyboardPreference, private val engine: JournalTypingEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) {
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var preparation: Deferred<Unit>? = null
    private var conversion: Job? = null
    private var learning: Job? = null
    private var generation = 0L
    private var closed = false

    val lifetime: Job get() = scope.coroutineContext.job
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { engine.close() } }
        }
    }

    fun prepare() {
        if (preparation != null || closed) return
        preparation = scope.async {
            withContext(Dispatchers.Default) { Romaji.hiragana("nihongo") }
            engine.prepare()
            ready = true
        }
        scope.launch { attempt("Japanese input could not start. Your writing is safe; you can use your device keyboard.") { preparation?.await() } }
    }
    fun query(query: CompositionQuery?, expanded: Boolean = false, receive: (List<JapaneseCandidate>) -> Unit) {
        conversion?.cancel()
        val ticket = ++generation
        busy = false
        if (query == null || closed) return
        prepare()
        conversion = scope.launch {
            try {
                delay(35)
                busy = true
                preparation?.await()
                learning?.join()
                val choices = engine.candidates(query, expanded)
                if (ticket == generation) receive(choices)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Suggestions could not load. Keep writing, or use your device keyboard." }
            finally { if (ticket == generation) busy = false }
        }
    }
    fun confirm(choices: List<ConfirmedInput>) {
        if (choices.isEmpty() || closed) return
        conversion?.cancel()
        val previous = learning
        learning = scope.launch {
            attempt("Your text is saved, but this choice could not be remembered.") {
                previous?.join()
                preparation?.await()
                choices.forEach { engine.learn(it) }
            }
        }
    }
    fun reset() {
        conversion?.cancel()
        val previous = learning
        learning = scope.launch {
            attempt("Learning could not be reset. Please try again.") {
                prepare(); previous?.join(); preparation?.await(); engine.reset()
                error = "Typing learning was reset on this device."
            }
        }
    }
    fun retry() {
        if (preparation?.isCancelled == true) preparation = null
        error = null
        prepare()
    }
    fun dismissError() { error = null }
    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
    }

    private suspend fun attempt(message: String, operation: suspend () -> Unit) {
        try { operation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = message }
    }
}

val LocalJournalInput = compositionLocalOf<JournalInputSession?> { null }
