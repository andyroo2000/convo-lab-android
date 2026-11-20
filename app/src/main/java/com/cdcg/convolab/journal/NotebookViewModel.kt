package com.cdcg.convolab.journal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.network.HttpApiTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotebookViewModel(context: Context, sessions: SessionController) : ViewModel() {
    private val mutableController = MutableStateFlow<NotebookController?>(null)
    val controller = mutableController.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessions.snapshot() ?: return@launch
            val api = JournalApi(HttpApiTransport(), session, sessions::snapshot)
            val storage = JournalDiskStore(context.noBackupFilesDir, session.account.id)
            val notebook = NotebookController(JournalRepository(api, storage), viewModelScope)
            mutableController.value = notebook
            notebook.start()
        }
    }
}
