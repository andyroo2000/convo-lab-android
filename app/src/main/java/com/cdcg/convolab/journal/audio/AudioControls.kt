package com.cdcg.convolab.journal.audio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private class AudioPresentation(val source: AudioSource, state: JournalAudioState, val playback: AudioPlayback,
    val coordinator: AudioCoordinator) {
    val active = state.active?.takeIf { it.request.source.key == source.key }
    val preparing = state.preparing?.key == source.key
    val loadingVoices = source.key in state.loadingVoices
    val voices = state.options[source.key]
    val playing = active != null && playback.playing
    val error = state.errors[source.key] ?: playback.error?.takeIf { active != null }
}

@Composable
fun AudioControls(source: AudioSource) {
    val coordinator = LocalJournalAudio.current ?: return
    val state by coordinator.state.collectAsStateWithLifecycle()
    val playback by coordinator.playback.collectAsStateWithLifecycle()
    LaunchedEffect(coordinator, source.key) { coordinator.loadVoices(source) }
    val activity = LocalContext.current.audioActivity()
    DisposableEffect(coordinator, source.key) { onDispose {
        if (activity?.isChangingConfigurations != true) coordinator.stopIfSource(source)
    } }
    SourceAudioControls(AudioPresentation(source, state, playback, coordinator))
}

@Composable
private fun SourceAudioControls(presentation: AudioPresentation) {
    val source = presentation.source
    val coordinator = presentation.coordinator
    var choosing by rememberSaveable(source.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AudioButtons(presentation) { choosing = true }
        if (presentation.preparing || presentation.loadingVoices) LinearProgressIndicator(Modifier.fillMaxWidth())
        presentation.active?.let { AudioTransport(presentation.playback, it, coordinator) }
        AudioError(source, presentation.error, presentation.voices, coordinator)
    }
    if (choosing) presentation.voices?.let { voices ->
        VoicePicker(voices, presentation.loadingVoices, { choosing = false }) {
            coordinator.chooseVoice(source, it); choosing = false
        }
    }
}

@Composable
private fun AudioButtons(presentation: AudioPresentation, choose: () -> Unit) {
    val source = presentation.source
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { presentation.coordinator.toggle(source) }, enabled = source.content is AudioContent.Question || presentation.voices != null) {
            Text(when { presentation.preparing -> "Cancel audio"; presentation.playing -> "Pause"; else -> "Listen" })
        }
        if (source.content is AudioContent.Final) TextButton(onClick = choose, enabled = presentation.voices != null) { Text("Voice") }
    }
}

@Composable
private fun AudioError(source: AudioSource, error: String?, voices: VoiceOptions?, coordinator: AudioCoordinator) {
    if (error == null) return
    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (voices == null && source.content is AudioContent.Final) TextButton(onClick = { coordinator.loadVoices(source, true) }) { Text("Retry voices") }
}

private tailrec fun Context.audioActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.audioActivity()
    else -> null
}

@Composable
private fun AudioTransport(playback: AudioPlayback, audio: CachedAudio, coordinator: AudioCoordinator) {
    var dragging by remember(audio.request.key) { mutableStateOf<Float?>(null) }
    val maximum = playback.duration.coerceAtLeast(1).toFloat()
    Slider(value = (dragging ?: playback.position.toFloat()).coerceIn(0f, maximum),
        onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { coordinator.seek(it / 1000.0) }; dragging = null },
        valueRange = 0f..maximum, enabled = playback.duration > 0,
        modifier = Modifier.semantics {
            contentDescription = "Audio position"
            stateDescription = "${audioTime(playback.position)} of ${audioTime(playback.duration)}"
        })
    Text("${audioTime(playback.position)} / ${audioTime(playback.duration)}")
    if (audio.sentences.isNotEmpty()) Row {
        TextButton(onClick = { coordinator.adjacent(-1) }) { Text("Previous sentence") }
        TextButton(onClick = { coordinator.adjacent(1) }) { Text("Next sentence") }
    }
}

private fun audioTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
