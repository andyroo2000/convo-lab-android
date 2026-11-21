package com.cdcg.convolab.journal.audio

import kotlinx.coroutines.flow.StateFlow

data class AudioPlayback(val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val error: String? = null)
interface JournalAudioPlayer {
    val state: StateFlow<AudioPlayback>
    suspend fun play(audio: CachedAudio)
    fun toggle()
    fun seek(milliseconds: Long)
    fun stop()
    fun close()
}
