package com.cdcg.convolab.auth.deletion

interface AccountCleanupStorage {
    suspend fun markConfirmed(accountId: Long)
    suspend fun pending(): Set<Long>
    suspend fun purge(accountId: Long)
    suspend fun complete(accountId: Long)
}
