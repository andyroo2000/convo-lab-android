package com.cdcg.convolab.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.auth.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(sessions: SessionController, create: (Session, CoroutineScope) -> SettingsController) : ViewModel() {
    private val mutableBinding = MutableStateFlow<SettingsBinding?>(null)
    val binding = mutableBinding.asStateFlow()
    init {
        viewModelScope.launch {
            sessions.state.map { if (it.account == null) null else sessions.snapshot() }
                .distinctUntilChanged { previous, next -> previous?.token == next?.token }
                .onEach { mutableBinding.value = null }
                .collectLatest { session ->
                    if (session != null) coroutineScope {
                        val controller = create(session, this)
                        mutableBinding.value = SettingsBinding(session.account.id, controller)
                        controller.start()
                        awaitCancellation()
                    }
                }
        }
    }
}

class SettingsBinding(val accountId: Long, val controller: SettingsController)
