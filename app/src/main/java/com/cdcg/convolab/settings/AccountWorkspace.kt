package com.cdcg.convolab.settings

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.SessionController
import com.cdcg.convolab.journal.JournalApi
import com.cdcg.convolab.journal.NotebookRoute
import com.cdcg.convolab.journal.text.LocalKnownKanji
import com.cdcg.convolab.network.HttpApiTransport
import com.cdcg.convolab.ui.AccountMenu

@Composable
fun AccountWorkspace(account: Account, sessions: SessionController, actions: SettingsActions) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context, sessions) { viewModelFactory { initializer {
        SettingsViewModel(sessions) { session, scope ->
            val api = SettingsApi(JournalApi(HttpApiTransport(), session, sessions::snapshot))
            SettingsController(SettingsAccess(session, api, KnownKanjiDiskStore(context.noBackupFilesDir, session.account.id), sessions), scope)
        }
    } } }
    val model: SettingsViewModel = viewModel(key = "active-settings", factory = factory)
    val binding by model.binding.collectAsStateWithLifecycle()
    val controller = binding?.takeIf { it.accountId == account.id }?.controller
    val settings = controller?.state?.collectAsStateWithLifecycle()?.value
    var showing by rememberSaveable(account.id) { mutableStateOf(false) }
    CompositionLocalProvider(LocalKnownKanji provides settings?.known?.kanji.orEmpty()) {
        NotebookRoute(account, sessions) { AccountMenu(account.name, actions.signOut, actions.typingLab) { showing = true } }
    }
    if (showing) {
        if (controller != null && settings != null) SettingsScreen(settings, controller,
            SettingsActions({ showing = false }, actions.signOut, actions.typingLab))
        else AlertDialog(onDismissRequest = { showing = false }, title = { Text("Loading Settings…") },
            text = { CircularProgressIndicator() }, confirmButton = { TextButton(onClick = { showing = false }) { Text("Close") } })
    }
}
