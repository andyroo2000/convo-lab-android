package com.cdcg.convolab.auth

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

/** Completion includes child jobs and their final disk flushes. Deleted owners cannot restart. */
class AccountWorkRegistry {
    private val lock = Any()
    private val work = mutableMapOf<Long, MutableSet<Job>>()
    private val stopped = mutableSetOf<Long>()

    fun own(accountId: Long, job: Job): Boolean = synchronized(lock) {
        if (accountId in stopped) return@synchronized false
        work.getOrPut(accountId) { mutableSetOf() }.add(job)
        job.invokeOnCompletion { synchronized(lock) { work[accountId]?.remove(job) } }
        true
    }
    suspend fun stop(accountId: Long) {
        val jobs = synchronized(lock) {
            stopped.add(accountId)
            work[accountId].orEmpty().toList()
        }
        jobs.forEach { it.cancel() }
        jobs.joinAll()
    }
}
