package com.cdcg.convolab.typing

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

/** The caller holds mutex through completion, including cancellation watcher teardown. */
internal object NativeOperation {
    val mutex = Mutex()
    suspend fun <T> run(handle: Long, operation: () -> T): T = runCancellableNative(
        { NativeTyping.beginOperation(handle) }, { NativeTyping.cancelOperation(handle) }, operation)
}

internal suspend fun <T> runCancellableNative(begin: () -> Unit, cancel: () -> Unit, operation: () -> T): T = coroutineScope {
    currentCoroutineContext().ensureActive()
    begin()
    val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { cancel() }
    }
    try {
        val result = withContext(Dispatchers.Default + NonCancellable) { operation() }
        currentCoroutineContext().ensureActive()
        result
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        throw failure
    } finally { withContext(NonCancellable) { watcher.cancelAndJoin() } }
}
