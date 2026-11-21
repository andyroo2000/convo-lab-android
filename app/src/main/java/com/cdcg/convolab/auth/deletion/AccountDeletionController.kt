package com.cdcg.convolab.auth.deletion

import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountDeletionState(val showing: Boolean = false, val busy: Boolean = false,
    val confirmed: Boolean = false, val cleanup: Boolean = false, val error: String? = null, val pending: Boolean = false)

/** Lives outside account-owned work so confirmed cleanup can await every account writer. */
class AccountDeletionController(private val sessions: SessionController, private val api: AccountDeletionGateway,
    private val storage: AccountCleanupStorage, private val scope: CoroutineScope, private val recovered: suspend () -> Unit = {}) {
    private val mutable = MutableStateFlow(AccountDeletionState())
    val state = mutable.asStateFlow()
    private val confirmed = mutableSetOf<Long>()

    fun open() {
        if (mutable.value.busy) return
        if (mutable.value.pending) openCleanup() else mutable.value = AccountDeletionState(showing = true)
    }
    fun openCleanup() { mutable.value = mutable.value.copy(showing = true, cleanup = true) }
    fun dismiss() { if (!mutable.value.busy) mutable.value = mutable.value.copy(showing = false) }
    fun delete(password: String) {
        if (mutable.value.busy || mutable.value.confirmed) return
        if (password.isEmpty() || password.length > 1024) {
            mutable.value = mutable.value.copy(error = "Enter your current password."); return
        }
        mutable.value = mutable.value.copy(busy = true, error = null)
        scope.launch {
            performDeletion(password)
        }
    }
    private suspend fun performDeletion(password: String) {
        try {
            val session = sessions.snapshot() ?: error("No signed-in account")
            api.delete(session, password)
            withContext(NonCancellable) {
                confirmed.add(session.account.id)
                mutable.value = mutable.value.copy(confirmed = true, cleanup = true, pending = true)
                clean(session.account.id)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun retry() {
        if (mutable.value.busy) return
        scope.launch { recover() }
    }
    suspend fun recover() {
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            confirmed.addAll(storage.pending())
            if (confirmed.isNotEmpty()) mutable.value = mutable.value.copy(confirmed = true, cleanup = true, pending = true)
            for (id in confirmed.toList()) withContext(NonCancellable) { clean(id) }
            recovered()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = mutable.value.copy(cleanup = true, pending = true,
            error = "Local account cleanup could not finish. Please retry before signing in.") }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }
    private suspend fun clean(id: Long) {
        // Even if persisting the marker fails, stop the deleted account immediately.
        val marker = runCatching { storage.markConfirmed(id) }
        sessions.work.stop(id)
        sessions.forgetDeletedAccount(id)
        marker.getOrThrow()
        storage.purge(id)
        storage.complete(id)
        confirmed.remove(id)
        mutable.value = mutable.value.copy(pending = confirmed.isNotEmpty())
    }
    private fun report(failure: Exception) {
        val cleanup = confirmed.isNotEmpty() || mutable.value.confirmed
        mutable.value = mutable.value.copy(pending = cleanup,
            error = if (cleanup) "Your account was deleted. Some local files still need to be removed. Please retry cleanup."
                else failure.userMessage())
    }
}
