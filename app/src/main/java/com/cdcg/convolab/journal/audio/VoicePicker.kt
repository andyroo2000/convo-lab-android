package com.cdcg.convolab.journal.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun VoicePicker(options: VoiceOptions, busy: Boolean, dismiss: () -> Unit, choose: (NarrationVoice) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Narration voice") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a voice for this final entry.")
            options.voices.forEach { voice ->
                OutlinedButton(onClick = { choose(voice) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().semantics { selected = voice.id == options.selected }) {
                    VoicePortrait(voice, Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(voice.name + if (voice.id == options.selected) " · Selected" else "")
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
