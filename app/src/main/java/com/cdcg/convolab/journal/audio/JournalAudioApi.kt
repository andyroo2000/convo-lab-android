package com.cdcg.convolab.journal.audio

import android.util.Base64
import com.cdcg.convolab.journal.*
import java.io.IOException
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

class JournalAudioApi(private val api: JournalApi) : JournalAudioGateway {
    override suspend fun voices(): List<NarrationVoice> = decode {
        val array = api.request(JournalRequest(JournalMethod.GET, "/api/journal/voices")).getJSONArray("data")
        (0 until array.length()).map { AudioJson.voice(array.getJSONObject(it)) }
    }
    override suspend fun selectedVoice(source: AudioSource): String = decode {
        val content = source.content as AudioContent.Final
        val query = "?analysisId=${URLEncoder.encode(content.analysisId.value, "UTF-8")}&expectedReplyRevision=${content.revision}"
        api.request(JournalRequest(JournalMethod.GET, JournalApi.path(source.entryId.value) + "/audio" + query))
            .getJSONObject("data").getString("voiceId")
    }
    override suspend fun generate(request: AudioRequest): AudioPayload = decode {
        require(request.source.content.text.codePointCount(0, request.source.content.text.length) <= 15_000)
        val content = request.source.content
        val path = JournalApi.path(request.source.entryId.value)
        val call = when (content) {
            is AudioContent.Final -> JournalRequest(JournalMethod.POST, "$path/audio/timed", JSONObject()
                .put("analysisId", content.analysisId.value).put("voiceId", request.voice).put("expectedReplyRevision", content.revision))
            is AudioContent.Question -> JournalRequest(JournalMethod.POST, "$path/questions/${content.questionId.value}/audio/timed")
        }
        val json = api.request(call).getJSONObject("data")
        val encoded = json.getString("audioBase64")
        if (encoded.length > 32 * 1024 * 1024) throw IOException("Audio exceeds the supported size")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.isEmpty()) throw IOException("Audio was empty")
        AudioPayload(bytes, AudioTimeline.validated(AudioJson.sentences(json), content.text))
    }
    private inline fun <T> decode(read: () -> T): T = try { read() }
        catch (failure: JSONException) { throw JournalResponseFailure(failure) }
}
