package com.cdcg.convolab.journal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coalesces queued keystrokes per entry, never across entries. A failed write stays pending. */
class DraftWriter(private val repository: JournalRepository, private val report: (String, Boolean) -> Unit) {
    private val pending = linkedMapOf<String, JournalDraft>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val writes = Mutex()

    fun start(scope: CoroutineScope) = scope.launch {
        try {
            for (ignored in wake) safelyFlush()
        } finally {
            withContext(NonCancellable) { safelyFlush() }
        }
    }

    @Synchronized fun enqueue(draft: JournalDraft) {
        pending[draft.id] = draft
        wake.trySend(Unit)
    }

    suspend fun flush() = writes.withLock {
        while (true) {
            val draft = next() ?: break
            repository.edit(draft)
            acknowledge(draft)
            report(draft.editId, true)
        }
    }

    @Synchronized private fun next() = pending.values.firstOrNull()
    @Synchronized private fun acknowledge(draft: JournalDraft) {
        if (pending[draft.id]?.editId == draft.editId) pending.remove(draft.id)
    }

    private suspend fun safelyFlush() {
        try { flush() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { next()?.let { report(it.editId, false) } }
    }
}
