package com.cdcg.convolab.journal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account-owned retry loop; only normal drafts are eligible, never LLM submissions or answers. */
class JournalAutomaticSync(private val repository: JournalRepository,
    private val notify: suspend (String?) -> Unit) {
    private val attempts = Mutex()
    private val failures = mutableMapOf<String, Int>()
    private val retryAt = mutableMapOf<String, Long>()

    fun reconnect() { retryAt.clear() }

    suspend fun synchronize(now: Long = System.currentTimeMillis()) = attempts.withLock {
        val records = repository.load().records
        for (record in records) {
            if (!eligible(record) || now < (retryAt[record.id] ?: 0)) continue
            attempt(record, now)
        }
    }

    private suspend fun attempt(record: JournalRecord, now: Long) {
        notify(record.id)
        try {
            repository.save(JournalId(record.id), automatic = true)
            failures.remove(record.id)
            retryAt.remove(record.id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            val count = ((failures[record.id] ?: 0) + 1).coerceAtMost(5)
            failures[record.id] = count
            retryAt[record.id] = now + (1L shl count) * 1000
        } finally { notify(null) }
    }

    private fun eligible(record: JournalRecord) = record.rootEntryId == null && !record.pendingDelete &&
        record.syncRejection == null && (record.pendingSave ?: record.draft)?.body?.isNotBlank() == true
}
