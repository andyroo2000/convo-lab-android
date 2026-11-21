package com.cdcg.convolab.journal.audio

import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.ApiTransport
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AudioContractTest {
    private val source = AudioSource(JournalId(JournalIds.newId()), AudioContent.Final(JournalId(JournalIds.newId()), 4, "日本。", "日本[にほん]。", "Journal"))
    private val session = Session("fixture-token", Account(101, "Naomi", "naomi@example.invalid"))
    @Test fun finalAudioUsesTheExactAnalysisRevisionAndQuestionAudioUsesItsParent() = runBlocking {
        val transport = AudioTransport()
        val api = JournalAudioApi(JournalApi(transport, session) { session })
        assertEquals("voice", api.voices().single().id)
        assertEquals("voice", api.selectedVoice(source))
        assertTrue(transport.path.endsWith("expectedReplyRevision=4"))
        val audio = api.generate(AudioRequest(source, "voice"))
        assertEquals("POST", transport.method)
        assertEquals("/api/journal/entries/${source.entryId.value}/audio/timed", transport.path)
        val body = JSONObject(transport.body!!)
        assertEquals(4, body.getInt("expectedReplyRevision"))
        assertEquals((source.content as AudioContent.Final).analysisId.value, body.getString("analysisId"))
        assertArrayEquals(byteArrayOf(1, 2, 3), audio.bytes)
        assertEquals(1, audio.sentences.size)
        val question = JournalId(JournalIds.newId())
        api.generate(AudioRequest(source.copy(content = AudioContent.Question(question, "日本。", "日本。", "Tutor")), "tutor"))
        assertEquals("/api/journal/entries/${source.entryId.value}/questions/${question.value}/audio/timed", transport.path)
    }
    @Test fun malformedOptionalTimingsKeepPlayableAudioAndExpiredSessionCannotGenerate() = runBlocking {
        val transport = AudioTransport().apply { malformed = true }
        var current: Session? = session
        val api = JournalAudioApi(JournalApi(transport, session) { current })
        val audio = api.generate(AudioRequest(source, "voice"))
        assertArrayEquals(byteArrayOf(1, 2, 3), audio.bytes)
        assertTrue(audio.sentences.isEmpty())
        current = null
        try { api.generate(AudioRequest(source, "voice")); fail("Must reject a stale session") }
        catch (failure: ApiFailure) { assertEquals(401, failure.status) }
        assertEquals(1, transport.requests)
    }
    @Test fun cacheRecoversAtomicBackupAndRejectsAnotherAccountOrCorruptAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "audio-contract-${UUID.randomUUID()}")
        try {
            val store = AudioDiskStore(directory, 101)
            val request = AudioRequest(source, "voice")
            val cached = store.write(request, AudioPayload(byteArrayOf(1, 2, 3), listOf(SentenceTiming("日本。", 0.0, 1.0))))
            assertNotNull(AudioDiskStore(directory, 101).read(request))
            assertNull(AudioDiskStore(directory, 102).read(request))
            val metadata = File(cached.file.parentFile, request.key + ".json")
            assertTrue(metadata.renameTo(File(metadata.path + ".bak")))
            assertNotNull(store.read(request))
            assertTrue(metadata.exists())
            cached.file.writeBytes(byteArrayOf(9))
            assertNull(store.read(request))
            val voices = VoiceOptions(listOf(NarrationVoice("voice", "Voice", null)), "voice")
            store.saveVoices(source, voices)
            assertEquals(voices, store.voices(source))
            assertNull(AudioDiskStore(directory, 102).voices(source))
        } finally { directory.deleteRecursively() }
    }
}

private class AudioTransport : ApiTransport {
    var method = ""
    var path = ""
    var body: String? = null
    var requests = 0
    var malformed = false
    override suspend fun request(method: String, path: String, token: String?, body: String?): String {
        this.method = method; this.path = path; this.body = body; requests++
        return when {
            path.endsWith("/voices") -> """{"data":[{"id":"voice","name":"Voice","avatarUrl":null}]}"""
            method == "GET" -> """{"data":{"voiceId":"voice"}}"""
            else -> """{"data":{"audioBase64":"AQID","sentences":${if (malformed) "[{\"text\":\"日本。\"}]" else "[{\"text\":\"日本。\",\"start\":0,\"end\":1}]"}}}"""
        }
    }
}
