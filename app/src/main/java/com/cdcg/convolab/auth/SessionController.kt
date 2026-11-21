package com.cdcg.convolab.auth

import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Session generations prevent late requests from signing a different account back in. */
class SessionController(
    private val api: AuthGateway,
    private val storage: SessionStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var generation = 0L
    private var session: Session? = null
    private val mutableState = MutableStateFlow(AuthState())
    val state = mutableState.asStateFlow()

    suspend fun snapshot(): Session? = mutex.withLock { session }

    suspend fun restore() {
        val operation = begin()
        try {
            val cached = mutex.withLock {
                if (generation != operation) return
                withContext(io) { storage.read() }.also {
                    session = it
                    mutableState.value = AuthState(restoring = false, account = it?.account)
                }
            } ?: return
            commit(operation, Session(cached.token, api.verify(cached.token)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            restoreFailure(operation, failure)
        } finally {
            finish(operation)
        }
    }

    suspend fun login(email: String, password: String) = authenticate { api.login(email.trim(), password) }

    suspend fun register(registration: Registration) = authenticate { api.register(registration) }

    suspend fun resetPassword(email: String): Boolean {
        val operation = begin()
        var accepted = false
        try {
            api.resetPassword(email.trim())
            mutex.withLock {
                accepted = operation == generation
                if (accepted) mutableState.value = mutableState.value.copy(resetSent = true)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(operation, failure)
        } finally {
            finish(operation)
        }
        return accepted
    }

    suspend fun signOut() {
        val old = withContext(NonCancellable) { mutex.withLock {
            generation++
            try {
                withContext(io) { storage.clear() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(busy = false, error = "Could not remove the saved sign-in. Please try again.")
                return@withLock null
            }
            val previous = session
            session = null
            mutableState.value = AuthState(restoring = false)
            previous
        } }
        if (old != null) revoke(old.token)
    }

    suspend fun updateAccount(expected: Session, account: Account): Boolean = withContext(NonCancellable) { mutex.withLock {
        if (session?.token != expected.token || session?.account?.id != account.id) return@withLock false
        val updated = Session(expected.token, account)
        withContext(io) { storage.write(updated) }
        session = updated
        mutableState.value = mutableState.value.copy(account = account)
        true
    } }

    suspend fun clearMessage() = mutex.withLock {
        mutableState.value = mutableState.value.copy(error = null, resetSent = false)
    }

    private suspend fun authenticate(request: suspend () -> Session): Boolean {
        val operation = begin()
        var issued: Session? = null
        var committed = false
        try {
            issued = request()
            committed = commit(operation, issued)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(operation, failure)
        } finally {
            finish(operation)
            if (!committed) issued?.let { discard(it) }
        }
        return committed
    }

    private suspend fun begin(): Long = mutex.withLock {
        generation++
        mutableState.value = mutableState.value.copy(busy = true, error = null, resetSent = false)
        generation
    }

    private suspend fun commit(operation: Long, result: Session) = withContext(NonCancellable) { mutex.withLock {
        if (generation != operation) return@withLock false
        withContext(io) { storage.write(result) }
        session = result
        mutableState.value = AuthState(restoring = false, account = result.account)
        true
    } }

    private suspend fun discard(issued: Session) = withContext(NonCancellable) {
        val inUse = mutex.withLock { session?.token == issued.token }
        if (!inUse) revoke(issued.token)
    }

    private suspend fun report(operation: Long, failure: Exception) = mutex.withLock {
        if (operation == generation) mutableState.value = mutableState.value.copy(error = failure.userMessage())
    }

    private suspend fun finish(operation: Long) = withContext(NonCancellable) { mutex.withLock {
        if (operation == generation) mutableState.value = mutableState.value.copy(restoring = false, busy = false)
    } }

    private suspend fun restoreFailure(operation: Long, failure: Exception) = mutex.withLock {
        if (operation != generation) return@withLock
        if (failure is ApiFailure && failure.status == 401) {
            session = null
            mutableState.value = AuthState(restoring = false)
            try {
                withContext(io + NonCancellable) { storage.clear() }
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(error = "Your sign-in expired, but its saved copy could not be removed. Please sign in again.")
            }
        } else if (failure is InvalidSessionStorage) {
            resetInvalidStorage()
        } else if (session == null) {
            mutableState.value = AuthState(restoring = false, error = "Your saved sign-in could not be opened. Please sign in again.")
        }
        // Transport failures preserve a cached verified profile for offline writing.
    }

    private suspend fun resetInvalidStorage() {
        session = null
        val message = try {
            withContext(io + NonCancellable) { storage.resetInvalid() }
            "Your saved sign-in could not be opened and was reset. Please sign in again."
        } catch (_: Exception) {
            "Your saved sign-in could not be reset. Please try again."
        }
        mutableState.value = AuthState(restoring = false, error = message)
    }

    private suspend fun revoke(token: String) {
        try {
            api.revoke(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Local sign-out is complete; a failed best-effort revocation must not restore it.
        }
    }
}
