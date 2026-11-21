package com.cdcg.convolab.auth

import com.cdcg.convolab.network.ApiTransport
import org.json.JSONObject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class AuthApi(private val api: ApiTransport) : AuthGateway {
    override suspend fun login(email: String, password: String): Session {
        val body = JSONObject().put("email", email).put("password", password).put("device_name", "ConvoLab Android")
        val data = envelope(api.request("POST", "/api/auth/tokens", body = body.toString()))
        return issuedSession(data) { verify(it) }
    }

    override suspend fun register(registration: Registration): Session {
        val body = JSONObject().put("name", registration.name).put("email", registration.email)
            .put("password", registration.password).put("inviteCode", registration.inviteCode)
            .put("device_name", "ConvoLab Android")
        val data = envelope(api.request("POST", "/api/convolab/auth/register", body = body.toString()))
        return issuedSession(data) { account(data.getJSONObject("user")) }
    }

    override suspend fun verify(token: String): Account = account(envelope(api.request("GET", "/api/me", token)))

    override suspend fun resetPassword(email: String) {
        api.request("POST", "/api/auth/password/forgot", body = JSONObject().put("email", email).toString())
    }

    override suspend fun revoke(token: String) {
        api.request("DELETE", "/api/auth/tokens/current", token)
    }

    private suspend fun issuedSession(data: JSONObject, resolve: suspend (String) -> Account): Session {
        val token = data.getString("token")
        require(token.isNotBlank())
        try {
            return Session(token, resolve(token))
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                try { revoke(token) } catch (_: Exception) { /* Best effort; preserve the original failure. */ }
            }
            throw failure
        }
    }

    private fun envelope(json: String) = JSONObject(json).getJSONObject("data")

    private fun account(json: JSONObject) = Account(json.getLong("id"), json.getString("name"), json.getString("email"))
}
