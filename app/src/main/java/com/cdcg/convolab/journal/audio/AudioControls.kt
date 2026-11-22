package com.cdcg.convolab.journal.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.cdcg.convolab.ui.*
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
    Column(Modifier.fillMaxWidth().zIndex(1f).background(Color(0xFFE4EEFC)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        AudioButtons(presentation) { choosing = true }
        if (presentation.preparing || presentation.loadingVoices) LinearProgressIndicator(Modifier.fillMaxWidth())
        presentation.active?.let { AudioTransport(presentation.playback, it, coordinator) }
        if (source.content is AudioContent.Final && presentation.active == null) IdleAudioRibbon()
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
    val final = source.content is AudioContent.Final
    Row(Modifier.padding(horizontal = if (final) 14.dp else 0.dp, vertical = if (final) 10.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (source.content is AudioContent.Final) {
            Surface(onClick = choose, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(88.dp).semantics { contentDescription = "Voice" }) {
                presentation.voices?.let { voices -> voices.voices.firstOrNull { it.id == voices.selected }?.let { VoicePortrait(it, Modifier.size(88.dp)) } }
            }
        }
        OutlinedButton(onClick = { presentation.coordinator.toggle(source) }, enabled = source.content is AudioContent.Question || presentation.voices != null,
            modifier = Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            shape = CircleShape, border = BorderStroke(1.dp, Color(0xFFBDD1E8)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF6393EF))) {
            JournalIcon(if (presentation.playing) JournalSymbol.Pause else JournalSymbol.Speaker, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(when { presentation.preparing -> "Cancel audio"; presentation.playing -> "Pause"; else -> "Listen" })
        }
    }
}

@Composable
private fun IdleAudioRibbon() {
    Canvas(Modifier.fillMaxWidth().height(3.dp).semantics {
        contentDescription = "Corrected version audio position"
        progressBarRangeInfo = ProgressBarRangeInfo(0f, 0f..1f)
        disabled()
    }) {
        drawRect(Color(0xFFCFDEED))
        val radius = 5.dp.toPx()
        drawCircle(Color.White, radius, Offset(radius, size.height / 2))
        drawCircle(Color(0xFF6393EF), radius - 2.dp.toPx(), Offset(radius, size.height / 2))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioTransport(playback: AudioPlayback, audio: CachedAudio, coordinator: AudioCoordinator) {
    var dragging by remember(audio.request.key) { mutableStateOf<Float?>(null) }
    val maximum = playback.duration.coerceAtLeast(1).toFloat()
    val slider = remember(maximum) { SliderState(trackRange = 0f..maximum) }
    SideEffect { slider.value = (dragging ?: playback.position.toFloat()).coerceIn(0f, maximum) }
    Slider(state = slider,
        onValueChange = { slider.value = it; dragging = it }, onValueChangeFinished = { dragging?.let { coordinator.seek(it / 1000.0) }; dragging = null },
        enabled = playback.duration > 0,
        thumb = { PaperSliderThumb(10.dp) }, track = { PaperSliderTrack(it, Color(0xFF6393EF)) },
        modifier = Modifier.semantics {
            contentDescription = "Audio position"
            stateDescription = "${audioTime(playback.position)} of ${audioTime(playback.duration)}"
        })
    PlaybackDetails(playback, audio, coordinator)
}

private fun audioTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun PlaybackDetails(playback: AudioPlayback, audio: CachedAudio, coordinator: AudioCoordinator) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = "Playback details" }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
            Text("${audioTime(playback.position)} / ${audioTime(playback.duration)}", fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
        }
        PaperMenu(expanded, { expanded = false }) {
            if (audio.sentences.isNotEmpty()) {
                PaperMenuItem("Previous sentence", JournalSymbol.Chevron) { expanded = false; coordinator.adjacent(-1) }
                PaperMenuItem("Next sentence", JournalSymbol.Chevron) { expanded = false; coordinator.adjacent(1) }
            }
        }
    }
}
