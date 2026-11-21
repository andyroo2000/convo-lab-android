package com.cdcg.convolab.settings

import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsAccess(val session: Session, val api: SettingsGateway, val storage: KnownKanjiStorage,
    val sessions: SessionController, val io: CoroutineDispatcher = Dispatchers.IO)

/** Owned by one signed-in workspace, so old requests cannot update a replacement account. */
class SettingsController(private val access: SettingsAccess, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(SettingsState(access.session.account))
    val state = mutableState.asStateFlow()

    fun start() = action {
        withContext(access.io) { access.storage.read() }?.let { cached -> mutableState.update { it.copy(known = cached) } }
        refresh()
    }

    fun updateProfile(name: String, email: String, done: () -> Unit) = action {
        require(name.isNotBlank() && email.isNotBlank())
        val account = access.api.profile(ProfileChange(name, email))
        if (!access.sessions.updateAccount(access.session, account)) throw ApiFailure(401)
        mutableState.update { it.copy(account = account, notice = "Profile saved.") }
        done()
    }

    fun updatePassword(change: PasswordChange, done: () -> Unit) = action {
        require(change.current.isNotEmpty() && change.password.codePointCount(0, change.password.length) >= 8 && change.password == change.confirmation)
        access.api.password(change)
        mutableState.update { it.copy(notice = "Password updated.") }
        done()
    }

    fun connect(token: String, clearToken: () -> Unit) = action {
        apply(access.api.connect(token))
        clearToken()
        synchronize()
    }

    fun sync() = action { synchronize() }
    fun reload() = action { refresh() }
    fun disconnect() = action {
        access.api.disconnect()
        refresh()
        mutableState.update { it.copy(notice = "WaniKani disconnected. Your known kanji are kept.") }
    }
    fun clearMessage() { mutableState.update { it.copy(error = null, notice = null) } }

    private suspend fun synchronize() {
        access.api.sync()
        refresh()
        mutableState.update { it.copy(notice = "WaniKani synced.") }
    }

    private suspend fun refresh() { apply(access.api.knownKanji()) }

    private suspend fun apply(snapshot: KnownKanjiSnapshot) {
        if (snapshot.version < state.value.known.version) return
        withContext(access.io + NonCancellable) { access.storage.write(snapshot) }
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(known = snapshot) }
    }

    private fun action(operation: suspend () -> Unit) = scope.launch {
        if (state.value.busy) return@launch
        mutableState.update { it.copy(busy = true, error = null, notice = null) }
        try { operation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.update { it.copy(error = failure.userMessage()) } }
        finally { mutableState.update { it.copy(busy = false) } }
    }
}
