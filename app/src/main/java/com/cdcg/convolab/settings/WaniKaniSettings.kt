package com.cdcg.convolab.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.auth.AuthPasswordField

@Composable
fun WaniKaniSettings(state: SettingsState, controller: SettingsController) {
    var disconnect by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.known.connected) {
            Text("Connected", style = MaterialTheme.typography.titleLarge)
            Text("Known kanji: ${state.known.kanji.size}")
            state.known.lastSyncedAt?.let { Text("Last synced: $it") }
            Button(onClick = { controller.sync() }, enabled = !state.busy) { Text("Sync WaniKani") }
            TextButton(onClick = { disconnect = true }, enabled = !state.busy) { Text("Disconnect WaniKani") }
        } else WaniKaniConnect(state.busy, controller)
        Text("Furigana stays visible unless every kanji in a word is in your synced known-kanji set. Your WaniKani API token is used to connect your account and is never saved on this device.")
        TextButton(onClick = { controller.reload() }, enabled = !state.busy) { Text("Refresh connection") }
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect WaniKani?") },
        text = { Text("Your known kanji will be kept. You can reconnect later.") },
        confirmButton = { TextButton(onClick = { disconnect = false; controller.disconnect() }, enabled = !state.busy) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

@Composable
private fun WaniKaniConnect(busy: Boolean, controller: SettingsController) {
    val token = remember { TextFieldState() }
    val clear = { token.edit { replace(0, length, "") } }
    DisposableEffect(token) { onDispose(clear) }
    AuthPasswordField("WaniKani API token", token, !busy)
    if (token.text.length > 512) Text("API tokens must be 512 characters or fewer.", color = MaterialTheme.colorScheme.error)
    Button(onClick = { controller.connect(token.text.toString(), clear) },
        enabled = !busy && token.text.isNotBlank() && token.text.length <= 512,
        modifier = Modifier.testTag("settings-connect-wanikani")) { Text("Connect and Sync") }
}
