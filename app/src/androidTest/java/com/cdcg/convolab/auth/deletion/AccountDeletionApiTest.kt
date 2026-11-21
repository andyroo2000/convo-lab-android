package com.cdcg.convolab.auth.deletion

import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiTransport
import com.cdcg.convolab.network.ApiFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountDeletionApiTest {
    private val session = Session("fixture-token", Account(101, "Fixture", "fixture@example.invalid"))
    @Test fun passwordDeletionUsesTheAuthenticatedAccountEndpoint() = runBlocking {
        var calls = 0
        val api = AccountDeletionApi(object : ApiTransport {
            override suspend fun request(method: String, path: String, token: String?, body: String?): String {
                calls++
                assertEquals("DELETE", method)
                assertEquals("/api/me", path)
                assertEquals(session.token, token)
                val payload = JSONObject(requireNotNull(body))
                assertEquals(1, payload.length())
                assertEquals("fixture-password", payload.getString("current_password"))
                return ""
            }
        })
        api.delete(session, "fixture-password")
        assertEquals(1, calls)
    }
    @Test fun rejectedPasswordPropagatesWithoutAcknowledgement() = runBlocking {
        val api = AccountDeletionApi(object : ApiTransport {
            override suspend fun request(method: String, path: String, token: String?, body: String?): String = throw ApiFailure(422)
        })
        try { api.delete(session, "wrong"); fail("Must reject") }
        catch (failure: ApiFailure) { assertEquals(422, failure.status) }
    }
}
