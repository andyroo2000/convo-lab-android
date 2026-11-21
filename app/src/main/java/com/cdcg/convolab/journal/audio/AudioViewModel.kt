package com.cdcg.convolab.journal.audio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.journal.JournalApi
import com.cdcg.convolab.network.HttpApiTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudioBinding(val accountId: Long, val controller: AudioCoordinator)

/** The account lease and player survive rotation; sign-out cancels generation and releases playback. */
class AudioViewModel(context: Context, sessions: SessionController) : ViewModel() {
    private val mutableBinding = MutableStateFlow<AudioBinding?>(null)
    val binding = mutableBinding.asStateFlow()
    init {
        viewModelScope.launch {
            sessions.state.map { if (it.account == null) null else sessions.snapshot() }
                .distinctUntilChanged { previous, next -> previous?.token == next?.token }
                .onEach { mutableBinding.value = null }
                .collectLatest { session ->
                    if (session != null) coroutineScope {
                        val lease = PlaybackLease(session.account.id)
                        PlaybackOwner.active = lease
                        val api = JournalAudioApi(JournalApi(HttpApiTransport(), session, sessions::snapshot))
                        val player = AndroidAudioPlayer(context, lease, this)
                        val coordinator = AudioCoordinator(AudioAccess(api, AudioDiskStore(context.noBackupFilesDir, lease.accountId), player), this)
                        mutableBinding.value = AudioBinding(lease.accountId, coordinator)
                        try { awaitCancellation() }
                        finally { coordinator.close(); if (PlaybackOwner.active == lease) PlaybackOwner.active = null }
                    }
                }
        }
    }
}
