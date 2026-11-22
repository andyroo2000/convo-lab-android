package com.cdcg.convolab.journal.audio

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdcg.convolab.journal.text.*

@Composable
fun AudioReading(source: AudioSource) {
    val coordinator = LocalJournalAudio.current
    val state = coordinator?.state?.collectAsStateWithLifecycle()?.value
    val playback = coordinator?.playback?.collectAsStateWithLifecycle()?.value
    val sentences = state?.active?.takeIf { it.request.source.key == source.key }?.sentences.orEmpty()
    val readings = remember(source.key, sentences) { AudioTimeline.readings(source.content.reading, sentences) }
    if (coordinator == null || readings.isNullOrEmpty()) { RubyText(source.content.reading); return }
    val selected = AudioTimeline.current(sentences, (playback?.position ?: 0) / 1000.0)
    RubyParagraph(readings.mapIndexed { index, text ->
        RubyRun(text, if (index == selected) Color(0xFF007AFF).copy(alpha = .12f) else Color.Transparent) { coordinator.seek(sentences[index].start) }
    })
}
