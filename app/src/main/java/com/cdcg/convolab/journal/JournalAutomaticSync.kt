package com.cdcg.convolab.journal

import com.cdcg.convolab.network.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account-owned retry loop; only normal drafts are eligible, never LLM submissions or answers. */
class JournalAutomaticSync(private val repository: JournalRepository,
    private val notify: suspend (String?) -> Unit) {
    private val attempts = Mutex()
    private val failures = mutableMapOf<String, Int>()
    private val retryAt = mutableMapOf<String, Long>()

    private val failedEdits = mutableMapOf<String, String?>()

    suspend fun reconnect() = attempts.withLock { retryAt.clear() }

    suspend fun synchronize(now: Long = System.currentTimeMillis()) = attempts.withLock {
        val records = repository.load().records
        val ids = records.map { it.id }.toSet()
        failures.keys.retainAll(ids)
        retryAt.keys.retainAll(ids)
        failedEdits.keys.retainAll(ids)
        for (record in records) {
            if (!record.isAutomaticSyncCandidate || isBackingOff(record, now)) continue
            attempt(record, now)
        }
    }

    private suspend fun attempt(record: JournalRecord, now: Long) {
        notify(record.id)
        try {
            repository.save(JournalId(record.id), automatic = true)
            failures.remove(record.id)
            retryAt.remove(record.id)
            failedEdits.remove(record.id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val count = ((failures[record.id] ?: 0) + 1).coerceAtMost(5)
            if (failure is ApiFailure && failure.status in setOf(400, 422)) failedEdits[record.id] = record.draft?.editId
            else failedEdits.remove(record.id)
            failures[record.id] = count
            retryAt[record.id] = now + (1L shl count) * 1000
        } finally { withContext(NonCancellable) { runCatching { notify(null) } } }
    }

    private fun isBackingOff(record: JournalRecord, now: Long): Boolean {
        if (failedEdits.containsKey(record.id) && failedEdits[record.id] != record.draft?.editId) return false
        return now < (retryAt[record.id] ?: 0)
    }
}

internal val JournalRecord.isAutomaticSyncCandidate: Boolean get() = when {
    rootEntryId != null -> false
    pendingDelete -> false
    syncRejection in setOf(400, 404, 409, 422) -> false
    else -> (pendingSave ?: draft)?.body?.isNotBlank() == true
}
