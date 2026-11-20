package com.cdcg.convolab.journal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.ApiTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalApiTest {
    private val session = Session("fixture-token", Account(11, "Sample", "sample@example.invalid"))
    private val draft = JournalDraft.blank().edited("こんにちは。")

    @Test fun saveUsesCanonicalIdExplicitNullTitleAndVersion() = runBlocking {
        val transport = RecordingJournalTransport { entryResponse() }
        val api = JournalApi(transport, session) { session }
        val entry = api.save(draft.copy(id = draft.id.uppercase()))
        assertEquals("PUT", transport.method)
        assertEquals("/api/journal/entries/${draft.id}", transport.path)
        assertEquals(session.token, transport.token)
        val body = JSONObject(transport.body!!)
        assertTrue(body.has("title"))
        assertTrue(body.isNull("title"))
        assertEquals(0, body.getInt("expectedVersion"))
        assertEquals(draft.body, body.getString("body"))
        assertEquals(draft.id, entry.id)
    }

    @Test fun paginationEscapesCursorAndDeleteIncludesExpectedVersion() = runBlocking {
        val transport = RecordingJournalTransport { "{\"data\":[],\"nextCursor\":null}" }
        val api = JournalApi(transport, session) { session }
        assertNull(api.archive("a+/=?").nextCursor)
        assertEquals("/api/journal/entries?cursor=a%2B%2F%3D%3F", transport.path)
        transport.reply = { "" }
        api.delete(draft.id, 7)
        assertEquals("DELETE", transport.method)
        assertEquals(7, JSONObject(transport.body!!).getInt("expectedVersion"))
    }

    @Test fun accountChangeRejectsLateResponsesAndPreventsFurtherRequests() = runBlocking {
        var active: Session? = session
        val transport = RecordingJournalTransport { active = null; entryResponse() }
        val api = JournalApi(transport, session) { active }
        expectUnauthorized { api.entry(draft.id) }
        assertEquals(1, transport.calls)
        expectUnauthorized { api.entry(draft.id) }
        assertEquals(1, transport.calls)
    }

    private suspend fun expectUnauthorized(action: suspend () -> Unit) {
        try { action(); fail("Expected session rejection") }
        catch (failure: ApiFailure) { assertEquals(401, failure.status) }
    }

    private fun entryResponse() = JSONObject().put("data", JSONObject()
        .put("id", draft.id.uppercase()).put("title", JSONObject.NULL).put("body", draft.body)
        .put("entryDate", draft.entryDate).put("version", 1)
        .put("createdAt", "2026-11-20T12:00:00Z").put("updatedAt", "2026-11-20T12:00:00Z")).toString()
}

private class RecordingJournalTransport(var reply: () -> String) : ApiTransport {
    var method = ""
    var path = ""
    var token: String? = null
    var body: String? = null
    var calls = 0
    override suspend fun request(method: String, path: String, token: String?, body: String?): String {
        this.method = method
        this.path = path
        this.token = token
        this.body = body
        calls++
        return reply()
    }
}
