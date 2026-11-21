package com.cdcg.convolab.journal.input

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun KeyboardPanel() {
    val session = LocalJournalInput.current ?: return
    val mode by session.preference.state.collectAsStateWithLifecycle()
    KeyboardChoices(session.preference)
    if (mode == JournalKeyboard.BuiltIn) KeyboardLearning(session)
}

@Composable
fun KeyboardChoices(preference: KeyboardPreference, onboarding: Boolean = false, done: () -> Unit = {}) {
    val current by preference.state.collectAsStateWithLifecycle()
    var selected by remember(current) { mutableStateOf(current) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyboardChoice(JournalKeyboard.BuiltIn, selected, { selected = it },
            KeyboardDescription(if (onboarding) "I use an English keyboard" else "Built-in Japanese input", "Convert romaji into Japanese as I write."))
        KeyboardChoice(JournalKeyboard.Device, selected, { selected = it },
            KeyboardDescription(if (onboarding) "I use a Japanese keyboard" else "Use my keyboard", "Let my keyboard handle the conversion."))
        Text("With built-in input, type with your English keyboard. Tap a suggestion to choose kanji. Space cycles choices; Enter accepts. Use the arrows to choose a section and −/+ to adjust it.")
        Button(onClick = { preference.change(selected); done() }, enabled = selected != JournalKeyboard.Choose) {
            Text(if (onboarding) "Start writing" else "Save keyboard choice")
        }
    }
}

private data class KeyboardDescription(val title: String, val detail: String)

@Composable
private fun KeyboardChoice(mode: JournalKeyboard, selection: JournalKeyboard, choose: (JournalKeyboard) -> Unit, description: KeyboardDescription) {
    Row(Modifier.fillMaxWidth().selectable(selected = mode == selection, role = Role.RadioButton, onClick = { choose(mode) }).padding(8.dp)) {
        RadioButton(selected = mode == selection, onClick = null)
        Column(Modifier.padding(start = 8.dp)) { Text(description.title); Text(description.detail, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun KeyboardLearning(session: JournalInputSession) {
    var confirm by remember { mutableStateOf(false) }
    Text("Your confirmed choices are remembered on this device for this account. Your writing and learning are not sent to a typing service.")
    TextButton(onClick = { confirm = true }) { Text("Reset typing learning") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Reset typing learning?") },
        text = { Text("This clears remembered conversion choices on this device. Your journal entries stay saved.") },
        confirmButton = { TextButton(onClick = { confirm = false; session.reset() }) { Text("Reset learning") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
