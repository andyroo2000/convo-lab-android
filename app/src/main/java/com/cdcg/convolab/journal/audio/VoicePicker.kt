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

import com.cdcg.convolab.ui.*
import androidx.compose.ui.semantics.clearAndSetSemantics

@Composable
fun VoicePicker(options: VoiceOptions, busy: Boolean, dismiss: () -> Unit, choose: (NarrationVoice) -> Unit) {
    JournalSheet("Narration voice", dismiss) {
        Text("Used when you play the corrected version.", color = JournalStyle.Ink.copy(alpha = .5f))
        options.voices.chunked(2).forEach { voices ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                voices.forEach { voice ->
                    JournalAvatarChoice(voice.id == options.selected, voice.name, !busy, Modifier.weight(1f), { choose(voice) }) {
                        VoicePortrait(voice, Modifier.size(80.dp).clearAndSetSemantics {})
                    }
                }
                if (voices.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
