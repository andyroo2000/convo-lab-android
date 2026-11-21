package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.JournalRequest
import com.cdcg.convolab.journal.JournalMethod
import com.cdcg.convolab.journal.*
import org.json.JSONException
import org.json.JSONObject

class TutorApi(private val api: JournalApi) : TutorGateway {
    override suspend fun preferences() = decode { TutorJson.preferences(api.request(JournalRequest(JournalMethod.GET, "/api/journal/tutor")).getJSONObject("data")) }
    override suspend fun choose(tutor: Tutor) = decode {
        TutorJson.preferences(api.request(JournalRequest(JournalMethod.PUT, "/api/journal/tutor", JSONObject().put("tutorId", tutor.id))).getJSONObject("data"))
    }
    override suspend fun prepare(source: QuestionSource) = decode {
        val body = JSONObject().put("analysisId", source.analysisId.value).put("replyId", source.replyId?.value ?: JSONObject.NULL)
        TutorJson.question(api.request(JournalRequest(JournalMethod.POST, JournalApi.path(source.entryId.value) + "/question", body)).getJSONObject("data"))
    }
    override suspend fun question(entryId: JournalId, questionId: JournalId) = decode {
        TutorJson.question(api.request(JournalRequest(JournalMethod.GET, path(entryId, questionId))).getJSONObject("data"))
    }
    override suspend fun retryAudio(entryId: JournalId, questionId: JournalId) = decode {
        TutorJson.question(api.request(JournalRequest(JournalMethod.POST, path(entryId, questionId) + "/retry")).getJSONObject("data"))
    }
    private fun path(entryId: JournalId, questionId: JournalId) = JournalApi.path(entryId.value) + "/questions/" + questionId.value
    private inline fun <T> decode(read: () -> T): T = try { read() }
        catch (failure: JSONException) { throw JournalResponseFailure(failure) }
}

object TutorJson {
    fun tutor(json: JSONObject) = Tutor(json.getString("id"), json.getString("name"), json.getString("gender"),
        json.getString("avatar"), json.nullableString("avatarUrl"))
    fun question(json: JSONObject) = TutorQuestion(JournalIds.normalize(json.getString("id")), json.getString("text"),
        json.getString("reading"), json.getString("translation"), json.optJSONObject("tutor")?.let(::tutor),
        json.optJSONObject("audio")?.nullableString("status"), json.optJSONObject("audio")?.nullableString("error"))
    fun preferences(json: JSONObject): TutorPreferences {
        val tutors = json.getJSONArray("tutors")
        return TutorPreferences(json.nullableString("selectedId"), (0 until tutors.length()).map { tutor(tutors.getJSONObject(it)) })
    }
}
