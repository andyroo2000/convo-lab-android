package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalApi
import com.cdcg.convolab.journal.JournalEntry
import com.cdcg.convolab.journal.JournalIds
import com.cdcg.convolab.journal.JournalJson
import com.cdcg.convolab.journal.JournalResponseFailure
import org.json.JSONException
import org.json.JSONObject

class FeedbackApi(private val api: JournalApi) : FeedbackGateway {
    override suspend fun startClarification(entry: JournalEntry): Clarification = decode {
        FeedbackJson.clarification(api.request("POST", path(entry) + "/clarification", version(entry)).getJSONObject("data"))
    }

    override suspend fun clarification(entry: JournalEntry): Clarification? = decode {
        api.request("GET", path(entry) + "/clarification").optJSONObject("data")?.let(FeedbackJson::clarification)
    }

    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification = decode {
        val body = version(entry).put("questionId", JournalIds.normalize(answer.questionId)).put("answer", answer.text)
        val route = path(entry) + "/clarification/" + JournalIds.normalize(answer.clarificationId) + "/answer"
        FeedbackJson.clarification(api.request("POST", route, body).getJSONObject("data"))
    }

    override suspend fun submit(entry: JournalEntry, clarificationId: String): Analysis = decode {
        val body = version(entry).put("clarificationId", JournalIds.normalize(clarificationId))
        FeedbackJson.analysis(api.request("POST", path(entry) + "/submit", body).getJSONObject("data"))
    }

    override suspend fun analysis(entry: JournalEntry): AnalysisResponse = decode {
        val json = api.request("GET", path(entry) + "/analysis")
        AnalysisResponse(JournalJson.entry(json.getJSONObject("entry")), json.optJSONObject("data")?.let(FeedbackJson::analysis))
    }

    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis = decode {
        require(edit.text.isNotBlank() && edit.text.length <= 30_000 && !edit.text.contains('\u0000'))
        val body = JSONObject().put("analysisId", JournalIds.normalize(edit.analysisId))
            .put("expectedRevision", edit.expectedRevision).put("text", edit.text)
        FeedbackJson.analysis(api.request("PUT", path(entry) + "/corrected", body).getJSONObject("data"))
    }

    private fun path(entry: JournalEntry) = JournalApi.path(entry.id)
    private fun version(entry: JournalEntry) = JSONObject().put("expectedVersion", entry.version)
    private inline fun <T> decode(read: () -> T): T = try { read() }
        catch (failure: JSONException) { throw JournalResponseFailure(failure) }
}
