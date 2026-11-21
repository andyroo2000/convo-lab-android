package com.cdcg.convolab.auth.deletion

import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    fun openCleanup() { mutable.update { it.copy(showing = true, cleanup = true) } }
    fun dismiss() { if (!mutable.value.busy) mutable.update { it.copy(showing = false) } }
    fun delete(password: String) {
        if (mutable.value.busy || mutable.value.confirmed) return
        if (password.isEmpty() || password.length > 1024) {
            mutable.update { it.copy(error = "Enter your current password.") }; return
        }
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            performDeletion(password)
        }
    }
    private suspend fun performDeletion(password: String) {
        try {
            val session = sessions.snapshot() ?: error("No signed-in account")
            withContext(NonCancellable) {
                api.delete(session, password)
                confirmed.add(session.account.id)
                mutable.update { it.copy(confirmed = true, cleanup = true, pending = true) }
                clean(session.account.id)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure) }
        finally { mutable.update { it.copy(busy = false) } }
    }

    fun retry() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        scope.launch { recover() }
    }
    suspend fun recover() {
        mutable.update { it.copy(busy = true, error = null) }
        try {
            confirmed.addAll(storage.pending())
            if (confirmed.isNotEmpty()) mutable.update { it.copy(confirmed = true, cleanup = true, pending = true) }
            for (id in confirmed.toList()) withContext(NonCancellable) { clean(id) }
            recovered()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(cleanup = true, pending = true,
            error = "Local account cleanup could not finish. Please retry before signing in.") } }
        finally { mutable.update { it.copy(busy = false) } }
    }
    private suspend fun clean(id: Long) {
        // Even if persisting the marker fails, stop the deleted account immediately.
        val marker = runCatching { storage.markConfirmed(id) }
        sessions.work.stop(id)
        val credentials = runCatching { sessions.forgetDeletedAccount(id) }
        val files = runCatching { storage.purge(id) }
        marker.getOrThrow()
        credentials.getOrThrow()
        files.getOrThrow()
        storage.complete(id)
        confirmed.remove(id)
        mutable.update { it.copy(pending = confirmed.isNotEmpty()) }
    }
    private fun report(failure: Exception) {
        val cleanup = confirmed.isNotEmpty() || mutable.value.confirmed
        mutable.update { it.copy(pending = cleanup,
            error = if (cleanup) "Your account was deleted. Some local account data still needs cleanup. Please retry cleanup."
                else failure.userMessage()) }
    }
}
