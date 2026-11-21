package com.cdcg.convolab.journal.audio

import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.JournalIds
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioCoordinatorTest {
    @Test fun cachedRecordingCanPlayOfflineAndVoiceChangesStopThePreviousTrack() = runTest {
        val fixture = AudioFixture(this)
        fixture.coordinator.loadVoices(fixture.source).join()
        fixture.coordinator.toggle(fixture.source)
        runCurrent()
        assertEquals(1, fixture.api.generated)
        fixture.coordinator.stop()
        fixture.api.offline = true
        fixture.coordinator.toggle(fixture.source)
        runCurrent()
        assertEquals(1, fixture.api.generated)
        assertNotNull(fixture.coordinator.state.value.active)
        fixture.coordinator.chooseVoice(fixture.source, fixture.api.voices.last())
        assertNull(fixture.coordinator.state.value.active)
        assertEquals("second", fixture.coordinator.state.value.options[fixture.source.key]?.selected)
        runCurrent()
        assertEquals("second", fixture.storage.options?.selected)
    }
    @Test fun cancelledNonCooperativeGenerationCannotBeginPlayback() = runTest {
        val fixture = AudioFixture(this)
        fixture.coordinator.loadVoices(fixture.source).join()
        val gate = CompletableDeferred<Unit>()
        fixture.api.gate = gate
        fixture.coordinator.toggle(fixture.source)
        runCurrent()
        fixture.coordinator.stopIfSource(fixture.source)
        gate.complete(Unit)
        runCurrent()
        assertNull(fixture.player.played)
        assertNull(fixture.coordinator.state.value.active)
        assertTrue(fixture.storage.audio.isEmpty())
    }
    @Test fun cachedVoiceSelectionSurvivesOfflineReloadAndCloseReleasesPlayback() = runTest {
        val fixture = AudioFixture(this)
        fixture.storage.options = VoiceOptions(fixture.api.voices, "second")
        fixture.api.offline = true
        fixture.coordinator.loadVoices(fixture.source).join()
        assertEquals("second", fixture.coordinator.state.value.options[fixture.source.key]?.selected)
        fixture.coordinator.toggle(fixture.source)
        runCurrent()
        assertNotNull(fixture.coordinator.state.value.errors[fixture.source.key])
        fixture.api.offline = false
        fixture.coordinator.toggle(fixture.source)
        runCurrent()
        fixture.coordinator.close()
        assertTrue(fixture.player.closed)
        assertNull(fixture.player.played)
    }
    @Test fun chosenButNotYetPlayedVoiceSurvivesReopeningAgainstAnOlderServerPreference() = runTest {
        val fixture = AudioFixture(this)
        fixture.storage.options = VoiceOptions(fixture.api.voices, "second")
        fixture.coordinator.loadVoices(fixture.source).join()
        assertEquals("second", fixture.coordinator.state.value.options[fixture.source.key]?.selected)
    }

    @Test fun tutorRecordingUsesItsHistoricalQuestionWithoutLoadingNarrationVoices() = runTest {
        val fixture = AudioFixture(this)
        val source = fixture.source.copy(content = AudioContent.Question(JournalId(JournalIds.newId()), "質問。", "質問[しつもん]。", "Tutor"))
        fixture.coordinator.toggle(source)
        runCurrent()
        assertEquals("tutor", fixture.player.played?.request?.voice)
        assertEquals(source, fixture.player.played?.request?.source)
        fixture.coordinator.seek(2.5)
        assertEquals(2500L, fixture.player.state.value.position)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class AudioFixture(test: TestScope) {
    val source = AudioSource(JournalId(JournalIds.newId()), AudioContent.Final(JournalId(JournalIds.newId()), 2, "日本。", "日本[にほん]。", "My entry"))
    val api = FakeAudioApi()
    val storage = FakeAudioStorage()
    val player = FakeAudioPlayer()
    val coordinator = AudioCoordinator(AudioAccess(api, storage, player, StandardTestDispatcher(test.testScheduler)), test.backgroundScope)
}
private class FakeAudioApi : JournalAudioGateway {
    val voices = listOf(NarrationVoice("first", "First", null), NarrationVoice("second", "Second", null))
    var offline = false
    var generated = 0
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun voices(): List<NarrationVoice> { if (offline) throw IOException("offline"); return voices }
    override suspend fun selectedVoice(source: AudioSource) = "first"
    override suspend fun generate(request: AudioRequest): AudioPayload {
        if (offline) throw IOException("offline")
        generated++
        withContext(NonCancellable) { gate?.await() }
        return AudioPayload(byteArrayOf(1), listOf(SentenceTiming(request.source.content.text, 0.0, 5.0)))
    }
}
private class FakeAudioStorage : AudioStorage {
    val audio = mutableMapOf<String, CachedAudio>()
    var options: VoiceOptions? = null
    override fun read(request: AudioRequest) = audio[request.key]
    override fun write(request: AudioRequest, payload: AudioPayload) = CachedAudio(request, File("fixture"), payload.sentences).also { audio[request.key] = it }
    override fun voices(source: AudioSource) = options
    override fun saveVoices(source: AudioSource, options: VoiceOptions) { this.options = options }
}
private class FakeAudioPlayer : JournalAudioPlayer {
    override val state = MutableStateFlow(AudioPlayback())
    var played: CachedAudio? = null
    var closed = false
    override suspend fun play(audio: CachedAudio) { played = audio; state.value = AudioPlayback(playing = true, duration = 5000) }
    override fun toggle() { state.value = state.value.copy(playing = !state.value.playing) }
    override fun seek(milliseconds: Long) { state.value = state.value.copy(position = milliseconds) }
    override fun stop() { played = null; state.value = AudioPlayback() }
    override fun close() { stop(); closed = true }
}
