package com.cdcg.convolab.journal

import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiTransport
import com.cdcg.convolab.network.ApiFailure
import java.net.URLEncoder
import java.io.IOException
import org.json.JSONException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Each request uses one captured session, with checks on both sides of the network operation. */
class JournalApi(
    private val transport: ApiTransport,
    private val session: Session,
    private val currentSession: suspend () -> Session?,
) : JournalGateway {
    override suspend fun archive(cursor: String?): JournalPage {
        val query = cursor?.let { "?cursor=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return page(request(JournalRequest(JournalMethod.GET, "/api/journal/entries$query")))
    }

    override suspend fun unfinished(): JournalPage = page(request(JournalRequest(JournalMethod.GET, "/api/journal/entries?unfinished=1")))

    override suspend fun entry(id: String): JournalEntry = decode { JournalJson.entry(request(JournalRequest(JournalMethod.GET, path(id))).getJSONObject("data")) }

    override suspend fun save(draft: JournalDraft): JournalEntry {
        require(draft.body.isNotBlank())
        return decode { JournalJson.entry(request(JournalRequest(JournalMethod.PUT, path(draft.id), JournalJson.savePayload(draft))).getJSONObject("data")) }
    }

    override suspend fun answer(draft: JournalDraft, origin: AnswerOrigin): JournalEntry = decode {
        require(draft.body.isNotBlank())
        val body = answerPayload(draft, origin)
        JournalJson.entry(request(JournalRequest(JournalMethod.POST, path(origin.parentId.value) + "/conversation", body)).getJSONObject("data"))
    }

    private fun answerPayload(draft: JournalDraft, origin: AnswerOrigin) = JSONObject()
        .put("id", draft.key.value).put("questionId", origin.questionId.value).put("body", draft.body)

    override suspend fun delete(id: String, version: Int) {
        request(JournalRequest(JournalMethod.DELETE, path(id), JSONObject().put("expectedVersion", version)))
    }

    suspend fun request(call: JournalRequest): JSONObject {
        checkSession()
        val response = transport.request(call.method.name, call.path, session.token, call.body?.toString())
        checkSession()
        return decode { if (response.isBlank()) JSONObject() else JSONObject(response) }
    }

    private suspend fun checkSession() {
        currentCoroutineContext().ensureActive()
        val active = currentSession() ?: throw ApiFailure(401)
        val matches = active.account.id == session.account.id && active.token == session.token
        if (!matches) throw ApiFailure(401)
    }

    private fun page(json: JSONObject): JournalPage = decode {
        val data = json.getJSONArray("data")
        JournalPage((0 until data.length()).map { JournalJson.entry(data.getJSONObject(it)) }, json.nullableString("nextCursor"))
    }

    private inline fun <T> decode(read: () -> T): T = try { read() }
        catch (failure: JSONException) { throw JournalResponseFailure(failure) }

    companion object {
        fun path(id: String) = "/api/journal/entries/" + JournalIds.normalize(id)
    }
}

class JournalResponseFailure(cause: JSONException) : IOException("The Journal response could not be read. Please try again.", cause)
