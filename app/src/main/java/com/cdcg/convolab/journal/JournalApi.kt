package com.cdcg.convolab.journal

import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiTransport
import com.cdcg.convolab.network.ApiFailure
import java.net.URLEncoder
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
        return page(request("GET", "/api/journal/entries$query"))
    }

    override suspend fun unfinished(): JournalPage = page(request("GET", "/api/journal/entries?unfinished=1"))

    override suspend fun entry(id: String): JournalEntry = JournalJson.entry(request("GET", path(id)).getJSONObject("data"))

    override suspend fun save(draft: JournalDraft): JournalEntry {
        require(draft.body.isNotBlank())
        return JournalJson.entry(request("PUT", path(draft.id), JournalJson.savePayload(draft)).getJSONObject("data"))
    }

    override suspend fun delete(id: String, version: Int) {
        request("DELETE", path(id), JSONObject().put("expectedVersion", version))
    }

    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        checkSession()
        val response = transport.request(method, path, session.token, body?.toString())
        checkSession()
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private suspend fun checkSession() {
        currentCoroutineContext().ensureActive()
        val active = currentSession() ?: throw ApiFailure(401)
        val matches = active.account.id == session.account.id && active.token == session.token
        if (!matches) throw ApiFailure(401)
    }

    private fun page(json: JSONObject): JournalPage {
        val data = json.getJSONArray("data")
        return JournalPage((0 until data.length()).map { JournalJson.entry(data.getJSONObject(it)) }, json.nullableString("nextCursor"))
    }

    companion object {
        fun path(id: String) = "/api/journal/entries/" + JournalIds.normalize(id)
    }
}
