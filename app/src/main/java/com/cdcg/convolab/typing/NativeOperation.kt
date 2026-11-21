package com.cdcg.convolab.typing

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

/** The caller holds mutex through completion, including cancellation watcher teardown. */
internal object NativeOperation {
    val mutex = Mutex()
    suspend fun <T> run(handle: Long, operation: () -> T): T = coroutineScope {
        currentCoroutineContext().ensureActive()
        NativeTyping.beginOperation(handle)
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { NativeTyping.cancelOperation(handle) }
        }
        try { withContext(Dispatchers.Default + NonCancellable) { operation() } }
        finally { withContext(NonCancellable) { watcher.cancelAndJoin() } }
    }
}
