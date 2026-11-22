package com.cdcg.convolab.journal.feedback

import android.content.Context
import com.cdcg.convolab.journal.audio.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** Supplies the real corrected-entry audio controls without accessing an account or generating media. */
internal fun feedbackAudioFixture(context: Context, scope: CoroutineScope): AudioCoordinator {
    val directory = File(context.cacheDir, "feedback-audio-${UUID.randomUUID()}")
    return AudioCoordinator(AudioAccess(PreviewAudioGateway(), AudioDiskStore(directory, 101), PreviewAudioPlayer()), scope)
}

private class PreviewAudioGateway : JournalAudioGateway {
    override suspend fun voices() = listOf(NarrationVoice("hana", "Hana", "/avatars/female-01-v2"))
    override suspend fun selectedVoice(source: AudioSource) = "hana"
    override suspend fun generate(request: AudioRequest): AudioPayload = error("Visual fixture does not generate audio")
}

private class PreviewAudioPlayer : JournalAudioPlayer {
    override val state = MutableStateFlow(AudioPlayback())
    override suspend fun play(audio: CachedAudio) = error("Visual fixture does not play audio")
    override fun toggle() = Unit
    override fun seek(milliseconds: Long) = Unit
    override fun stop() = Unit
    override fun close() = Unit
}
