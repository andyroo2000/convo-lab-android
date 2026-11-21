package com.cdcg.convolab.journal.audio

import com.cdcg.convolab.journal.JournalId
import java.io.File
import java.security.MessageDigest

sealed interface AudioContent {
    val text: String
    val reading: String
    val title: String
    data class Final(val analysisId: JournalId, val revision: Int, override val text: String,
        override val reading: String, override val title: String) : AudioContent
    data class Question(val questionId: JournalId, override val text: String,
        override val reading: String, override val title: String) : AudioContent
}
data class AudioSource(val entryId: JournalId, val content: AudioContent) {
    val key: String = audioHash((entryId.value + "|" + content.toString()).toByteArray(Charsets.UTF_8))
}
data class NarrationVoice(val id: String, val name: String, val avatarUrl: String?)
data class VoiceOptions(val voices: List<NarrationVoice>, val selected: String)
data class SentenceTiming(val text: String, val start: Double, val end: Double)
class AudioPayload(val bytes: ByteArray, val sentences: List<SentenceTiming>)
data class AudioRequest(val source: AudioSource, val voice: String) {
    val key: String = audioHash((source.key + "|" + voice).toByteArray(Charsets.UTF_8))
}
data class CachedAudio(val request: AudioRequest, val file: File, val sentences: List<SentenceTiming>)

interface JournalAudioGateway {
    suspend fun voices(): List<NarrationVoice>
    suspend fun selectedVoice(source: AudioSource): String
    suspend fun generate(request: AudioRequest): AudioPayload
}
interface AudioStorage {
    fun protect(request: AudioRequest?) = Unit
    fun read(request: AudioRequest): CachedAudio?
    fun write(request: AudioRequest, payload: AudioPayload): CachedAudio
    fun voices(source: AudioSource): VoiceOptions?
    fun saveVoices(source: AudioSource, options: VoiceOptions)
}
fun audioHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
