package com.cdcg.convolab.journal.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcg.convolab.auth.SessionController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class KeyboardBinding(val accountId: Long, val session: JournalInputSession)

/** Configuration changes retain the engine; account changes finish its writes before replacement. */
class KeyboardViewModel(sessions: SessionController, create: (Long) -> JournalInputSession) : ViewModel() {
    private val mutable = MutableStateFlow<KeyboardBinding?>(null)
    val binding = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            sessions.state.map { it.account?.id }.distinctUntilChanged().collectLatest { id ->
                mutable.value = null
                if (id != null) {
                    val input = create(id)
                    if (!sessions.work.own(id, input.lifetime)) {
                        input.close()
                        withContext(NonCancellable) { input.lifetime.join() }
                        return@collectLatest
                    }
                    mutable.value = KeyboardBinding(id, input)
                    try { awaitCancellation() }
                    finally {
                        input.close()
                        withContext(NonCancellable) { input.lifetime.join() }
                    }
                }
            }
        }
    }
}
