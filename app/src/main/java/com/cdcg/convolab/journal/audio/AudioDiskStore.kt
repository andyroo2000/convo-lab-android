package com.cdcg.convolab.journal.audio

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

/** Metadata is published after audio, and its digest prevents partial/mismatched cache playback. */
class AudioDiskStore(noBackupDirectory: File, accountId: Long, private val budgetBytes: Long = 128L * 1024 * 1024) : AudioStorage {
    private val directory = File(noBackupDirectory, "journal-audio/$accountId")

    @Synchronized override fun read(request: AudioRequest): CachedAudio? = cacheRead {
        val metadata = json(request.key + ".json") ?: return@cacheRead null
        if (metadata.getInt("schemaVersion") != 1 || metadata.getString("key") != request.key) return@cacheRead null
        val file = File(directory, request.key + ".mp3")
        val bytes = AtomicFile(file).readFully()
        if (bytes.size.toLong() !in 1..MAX_AUDIO_BYTES) return@cacheRead null
        if (audioHash(bytes) != metadata.getString("digest")) return@cacheRead null
        file.setLastModified(System.currentTimeMillis())
        CachedAudio(request, file, AudioTimeline.validated(AudioJson.sentences(metadata), request.source.content.text))
    }

    @Synchronized override fun write(request: AudioRequest, payload: AudioPayload): CachedAudio {
        require(payload.bytes.size.toLong() in 1..MAX_AUDIO_BYTES)
        val audio = File(directory, request.key + ".mp3")
        atomic(audio, payload.bytes)
        val timings = AudioTimeline.validated(payload.sentences, request.source.content.text)
        val metadata = JSONObject().put("schemaVersion", 1).put("key", request.key).put("digest", audioHash(payload.bytes))
            .put("sentences", AudioJson.encodeSentences(timings))
        // A missing optional sidecar must not discard already generated, playable audio.
        try { atomic(File(directory, request.key + ".json"), metadata.toString().toByteArray(Charsets.UTF_8)) }
        catch (_: IOException) { File(directory, request.key + ".json").delete() }
        AudioCacheBudget.prune(directory, budgetBytes, setOf(audio.name, request.key + ".json", request.source.key + "-voices.json"))
        return CachedAudio(request, audio, timings)
    }

    @Synchronized override fun voices(source: AudioSource): VoiceOptions? = cacheRead {
        val json = json(source.key + "-voices.json") ?: return@cacheRead null
        if (json.getInt("schemaVersion") != 1) return@cacheRead null
        AudioJson.options(json)
    }
    @Synchronized override fun saveVoices(source: AudioSource, options: VoiceOptions) {
        val json = AudioJson.encodeOptions(options).put("schemaVersion", 1)
        val name = source.key + "-voices.json"
        atomic(File(directory, name), json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun json(name: String): JSONObject? {
        val file = File(directory, name)
        val bytes = AtomicFile(file).readFully()
        if (bytes.size > 512 * 1024) return null
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }
    private fun atomic(file: File, bytes: ByteArray) {
        check(directory.isDirectory || directory.mkdirs())
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (failure: Exception) { atomic.failWrite(output); throw failure }
    }
    private inline fun <T> cacheRead(read: () -> T?): T? = try { read() }
        catch (_: IOException) { null }
        catch (_: JSONException) { null }
        catch (_: IllegalArgumentException) { null }

    private companion object { const val MAX_AUDIO_BYTES = 24L * 1024 * 1024 }
}
