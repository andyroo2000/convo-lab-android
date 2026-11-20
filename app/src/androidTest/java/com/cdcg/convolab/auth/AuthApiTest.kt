package com.cdcg.convolab.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcg.convolab.network.ApiTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthApiTest {
    private val user = """{"id":42,"name":"Sample learner","email":"sample@example.invalid"}"""

    @Test fun loginUsesMobileTokenThenVerifiesThatSpecificToken() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = AuthApi(recording(calls) { call ->
            if (call.path == "/api/auth/tokens") """{"data":{"token":"fixture-token"}}"""
            else """{"data":$user}"""
        })
        val session = api.login("sample@example.invalid", "fixture-password")
        assertEquals(42L, session.account.id)
        assertEquals(listOf("POST", "GET"), calls.map { it.method })
        assertEquals(listOf("/api/auth/tokens", "/api/me"), calls.map { it.path })
        assertNull(calls[0].token)
        assertEquals("fixture-token", calls[1].token)
        val payload = JSONObject(calls[0].body!!)
        assertEquals("fixture-password", payload.getString("password"))
        assertEquals("ConvoLab Android", payload.getString("device_name"))
    }

    @Test fun registrationPreservesInviteCodeWireSpelling() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = AuthApi(recording(calls) { """{"data":{"token":"fixture-token","user":$user}}""" })
        api.register(Registration("Sample learner", "sample@example.invalid", "fixture-password", "INVITE"))
        assertEquals("/api/convolab/auth/register", calls.single().path)
        val payload = JSONObject(calls.single().body!!)
        assertEquals("INVITE", payload.getString("inviteCode"))
        assertFalse(payload.has("invite_code"))
    }

    @Test fun resetAndRevocationAcceptEmptySuccessfulResponses() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = AuthApi(recording(calls) { "" })
        api.resetPassword("sample@example.invalid")
        api.revoke("fixture-token")
        assertEquals("/api/auth/password/forgot", calls[0].path)
        assertEquals("sample@example.invalid", JSONObject(calls[0].body!!).getString("email"))
        assertNull(calls[0].token)
        assertEquals("DELETE", calls[1].method)
        assertEquals("/api/auth/tokens/current", calls[1].path)
        assertEquals("fixture-token", calls[1].token)
        assertNull(calls[1].body)
    }

    private fun recording(calls: MutableList<Call>, respond: (Call) -> String) = object : ApiTransport {
        override suspend fun request(method: String, path: String, token: String?, body: String?): String {
            val call = Call(method, path, token, body)
            calls += call
            return respond(call)
        }
    }
    private class Call(val method: String, val path: String, val token: String?, val body: String?)
}
