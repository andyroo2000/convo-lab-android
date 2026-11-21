package com.cdcg.convolab.auth.deletion

import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiTransport
import org.json.JSONObject
import java.io.IOException

fun interface AccountDeletionGateway { suspend fun delete(session: Session, password: String) }
class AccountDeletionApi(private val transport: ApiTransport) : AccountDeletionGateway {
    override suspend fun delete(session: Session, password: String) {
        val response = transport.request("DELETE", "/api/me", session.token, JSONObject().put("current_password", password).toString())
        if (response.isNotBlank()) throw IOException("Unexpected account deletion response")
    }
}
