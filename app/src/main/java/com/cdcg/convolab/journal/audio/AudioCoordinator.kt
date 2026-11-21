package com.cdcg.convolab.journal.audio

import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudioAccess(val api: JournalAudioGateway, val storage: AudioStorage, val player: JournalAudioPlayer,
    val io: CoroutineDispatcher = Dispatchers.IO)
data class JournalAudioState(val active: CachedAudio? = null, val preparing: AudioSource? = null,
    val options: Map<String, VoiceOptions> = emptyMap(), val loadingVoices: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap())

/** One account, one active recording. Replacement requests cannot start playback after cancellation. */
class AudioCoordinator(private val access: AudioAccess, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(JournalAudioState())
    val state = mutableState.asStateFlow()
    val playback = access.player.state
    private var work: Job? = null
    private var generation = 0L

    fun loadVoices(source: AudioSource, retry: Boolean = false) = scope.launch {
        if (!needsVoices(source, retry)) return@launch
        mutableState.update { it.copy(loadingVoices = it.loadingVoices + source.key, errors = it.errors - source.key) }
        try {
            refreshVoices(source)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (source.key !in state.value.options) report(source, failure) }
        finally { mutableState.update { it.copy(loadingVoices = it.loadingVoices - source.key) } }
    }

    private fun needsVoices(source: AudioSource, retry: Boolean) = source.content is AudioContent.Final &&
        source.key !in state.value.loadingVoices && (retry || source.key !in state.value.options)

    fun chooseVoice(source: AudioSource, voice: NarrationVoice) {
        if (source.key in state.value.loadingVoices) return
        val options = state.value.options[source.key] ?: return
        if (options.voices.none { it.id == voice.id }) return
        stopIfSource(source)
        val selected = options.copy(selected = voice.id)
        publishOptions(source, selected)
        saveSelection(source, selected)
    }

    fun toggle(source: AudioSource) {
        if (state.value.preparing?.key == source.key) { stop(); return }
        if (state.value.active?.request?.source?.key == source.key) { access.player.toggle(); return }
        val voice = selectedVoice(source) ?: return
        stop()
        val epoch = generation
        val request = AudioRequest(source, voice)
        mutableState.update { it.copy(preparing = source, errors = it.errors - source.key) }
        work = scope.launch { prepare(request, epoch) }
    }
    private suspend fun prepare(request: AudioRequest, epoch: Long) {
        try {
            val cached = withContext(access.io) { access.storage.read(request) }
            val audio = cached ?: generate(request)
            currentCoroutineContext().ensureActive()
            if (epoch != generation) return
            access.player.play(audio)
            if (epoch == generation) mutableState.update { it.copy(active = audio, preparing = null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (epoch == generation) {
            report(request.source, failure)
            mutableState.update { it.copy(preparing = null) }
        } }
    }
    private fun selectedVoice(source: AudioSource): String? =
        if (source.content is AudioContent.Question) "tutor" else state.value.options[source.key]?.selected

    private suspend fun refreshVoices(source: AudioSource) {
        val cached = withContext(access.io) { access.storage.voices(source) }
        cached?.let { publishOptions(source, it) }
        val voices = access.api.voices()
        val selected = access.api.selectedVoice(source)
        check(voices.any { it.id == selected }) { "The saved narration voice is unavailable. Please retry." }
        val options = VoiceOptions(voices, selected)
        publishOptions(source, options)
        withContext(access.io + NonCancellable) { access.storage.saveVoices(source, options) }
    }
    private fun saveSelection(source: AudioSource, selected: VoiceOptions) = scope.launch {
        try { withContext(access.io + NonCancellable) { access.storage.saveVoices(source, selected) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(source, failure) }
    }
    fun seek(seconds: Double) { if (seconds.isFinite()) access.player.seek((seconds * 1000).toLong()) }
    fun adjacent(direction: Int) {
        val sentences = state.value.active?.sentences ?: return
        AudioTimeline.adjacent(sentences, playback.value.position / 1000.0, direction)?.let(::seek)
    }
    fun stop() { generation++; work?.cancel(); access.player.stop(); mutableState.update { it.copy(active = null, preparing = null) } }
    fun close() { stop(); access.player.close() }
    fun stopIfSource(source: AudioSource) {
        if (state.value.active?.request?.source?.key == source.key || state.value.preparing?.key == source.key) stop()
    }

    private suspend fun generate(request: AudioRequest): CachedAudio {
        val payload = access.api.generate(request)
        currentCoroutineContext().ensureActive()
        return withContext(access.io + NonCancellable) { access.storage.write(request, payload) }
    }
    private fun publishOptions(source: AudioSource, options: VoiceOptions) { mutableState.update { it.copy(options = it.options + (source.key to options)) } }
    private fun report(source: AudioSource, failure: Exception) {
        val message = when {
            failure is ApiFailure && failure.status == 409 -> "This audio changed or is still being prepared. Please retry shortly."
            failure is IllegalArgumentException -> "Audio supports text up to 15,000 characters."
            else -> failure.userMessage()
        }
        mutableState.update { it.copy(errors = it.errors + (source.key to message)) }
    }
}
