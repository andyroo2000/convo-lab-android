package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.network.ApiFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object JournalPolling {
    suspend fun <T> read(request: suspend () -> T): T {
        var failures = 0
        while (true) {
            try { return request() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                failures++
                if (!retryable(failure) || failures >= 3) throw failure
                delay(1_500L * failures)
            }
        }
    }

    private fun retryable(failure: Exception): Boolean = when (failure) {
        is ApiFailure -> failure.status == 429 || failure.status >= 500
        is IOException -> true
        else -> false
    }
}

class WorkflowFailure(message: String) : Exception(message)
