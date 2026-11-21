package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.FeedbackJson
import com.cdcg.convolab.network.ApiTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TutorContractTest {
    private val entry = JournalId(JournalIds.newId())
    private val analysis = JournalId(JournalIds.newId())
    private val reply = JournalId(JournalIds.newId())
    private val session = Session("fixture-token", Account(101, "Naomi", "naomi@example.invalid"))

    @Test fun questionUsesCurrentReplyAndPreservesHistoricalTutorAndAudioState() = runBlocking {
        val transport = TutorTransport()
        val api = TutorApi(JournalApi(transport, session) { session })
        val question = api.prepare(QuestionSource(entry, analysis, reply))
        assertEquals("POST", transport.method)
        assertEquals("/api/journal/entries/${entry.value}/question", transport.path)
        assertEquals(reply.value, JSONObject(transport.body!!).getString("replyId"))
        assertEquals(analysis.value, JSONObject(transport.body!!).getString("analysisId"))
        assertEquals("hana-v1", question.tutor?.avatar)
        assertEquals("processing", question.audioStatus)
        api.question(entry, JournalId(question.id))
        assertEquals("GET", transport.method)
        api.retryAudio(entry, JournalId(question.id))
        assertEquals("POST", transport.method)
        assertTrue(transport.path.endsWith("/questions/${question.id}/retry"))
        api.prepare(QuestionSource(entry, analysis))
        assertTrue(JSONObject(transport.body!!).isNull("replyId"))
    }

    @Test fun preferenceChangeUsesTutorIdAndAnalysisDecodesCanonicalReplyId() = runBlocking {
        val transport = TutorTransport()
        val api = TutorApi(JournalApi(transport, session) { session })
        val preferences = api.preferences()
        assertEquals(2, preferences.tutors.size)
        api.choose(preferences.tutors.last())
        assertEquals("PUT", transport.method)
        assertEquals("ren", JSONObject(transport.body!!).getString("tutorId"))
        val raw = JSONObject().put("id", analysis.value).put("sourceVersion", 1).put("status", "ready")
            .put("updatedAt", "updated").put("currentReply", JSONObject().put("id", reply.value.uppercase()))
        assertEquals(reply.value, FeedbackJson.analysis(raw).currentReplyId)
    }
}

internal class TutorTransport : ApiTransport {
    var method = ""
    var path = ""
    var body: String? = null
    private val questionId = JournalIds.newId()
    private var selected = "hana"
    override suspend fun request(method: String, path: String, token: String?, body: String?): String {
        this.method = method; this.path = path; this.body = body
        return if (path == "/api/journal/tutor") {
            if (method == "PUT") selected = JSONObject(body!!).getString("tutorId")
            """{"data":{"selectedId":"$selected","tutors":[$hana,$ren]}}"""
        } else """{"data":{"id":"$questionId","text":"今日は何をしましたか。","reading":"今日[きょう]は何[なに]をしましたか。","translation":"What did you do today?","tutor":$hana,"audio":{"status":"processing","error":null}}}"""
    }
    private val hana = """{"id":"hana","name":"Hana","gender":"F","avatar":"hana-v1","avatarUrl":"/api/journal/avatars/professor-female-v2"}"""
    private val ren = """{"id":"ren","name":"Ren","gender":"M","avatar":"ren-v1","avatarUrl":"/api/journal/avatars/professor-male-v2"}"""
}
