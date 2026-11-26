package com.cdcg.convolab.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.ui.JournalStyle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun WaniKaniSettings(state: SettingsState, controller: SettingsController) {
    var disconnect by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.known.connected) {
            ConnectedWaniKani(state, onSync = { controller.sync() }, onDisconnect = { disconnect = true })
        } else WaniKaniConnect(state.busy, controller)
        Text("Furigana stays visible unless every kanji in a word is in your synced known-kanji set. Your WaniKani API token is used to connect your account and is never saved on this device.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = JournalBrand.Ink.copy(alpha = .6f))
        if (state.error != null) WaniKaniAction("Refresh connection", { controller.reload() }, enabled = !state.busy)
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect WaniKani?") },
        text = { Text("Your known kanji will be kept. You can reconnect later.") },
        confirmButton = { TextButton(onClick = { disconnect = false; controller.disconnect() }, enabled = !state.busy) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

@Composable
private fun ConnectedWaniKani(state: SettingsState, onSync: () -> Unit, onDisconnect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = JournalStyle.Sage, contentColor = JournalStyle.Green) {
            Text("Connected", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = JournalStyle.Body.copy(fontSize = 16.sp))
        }
        Text("via API key", style = JournalStyle.Body, color = JournalBrand.Ink.copy(alpha = .7f))
    }
    ConnectionValue("Last sync", state.known.lastSyncedAt?.let(::connectionDate) ?: "Waiting for first sync")
    ConnectionValue("Known kanji", state.known.kanji.size.toString())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSync, enabled = !state.busy, colors = ButtonDefaults.buttonColors(containerColor = JournalStyle.Green)) {
            Text("Sync now", style = JournalStyle.Body)
        }
        OutlinedButton(onClick = onDisconnect, enabled = !state.busy, border = BorderStroke(1.dp, JournalStyle.Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JournalStyle.Green)) {
            Text("Disconnect", style = JournalStyle.Body)
        }
    }
}

@Composable
private fun WaniKaniConnect(busy: Boolean, controller: SettingsController) {
    val token = remember { TextFieldState() }
    val clear = { token.edit { replace(0, length, "") } }
    DisposableEffect(token) { onDispose(clear) }
    SettingsPasswordField("WaniKani API token", token, !busy, placeholder = true)
    if (token.text.length > 512) Text("API tokens must be 512 characters or fewer.", color = MaterialTheme.colorScheme.error)
    WaniKaniAction("Connect and Sync", onClick = { controller.connect(token.text.toString(), clear) },
        enabled = !busy && token.text.isNotBlank() && token.text.length <= 512,
        modifier = Modifier.testTag("settings-connect-wanikani"))
}

@Composable
private fun WaniKaniAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = JournalBrand.Blue), contentPadding = PaddingValues(vertical = 8.dp)) {
        Text(text, style = JournalStyle.Body)
    }
}

@Composable
private fun ConnectionValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = JournalBrand.Ink.copy(alpha = .6f))
    }
}

internal fun connectionDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}.getOrDefault(value)
