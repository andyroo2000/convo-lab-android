package com.cdcg.convolab.journal.audio

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.SessionController

val LocalJournalAudio = compositionLocalOf<AudioCoordinator?> { null }

@Composable
fun AudioWorkspace(accountId: Long, sessions: SessionController, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context, sessions) { viewModelFactory { initializer { AudioViewModel(context, sessions) } } }
    val model: AudioViewModel = viewModel(key = "journal-audio", factory = factory)
    val binding by model.binding.collectAsStateWithLifecycle()
    val controller = binding?.takeIf { it.accountId == accountId }?.controller
    CompositionLocalProvider(LocalJournalAudio provides controller, content = content)
}
