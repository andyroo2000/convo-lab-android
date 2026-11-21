package com.cdcg.convolab.auth.deletion

import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiTransport
import com.cdcg.convolab.network.ApiFailure
import org.json.JSONObject
import java.io.IOException

fun interface AccountDeletionGateway { suspend fun delete(session: Session, password: String) }
class AccountDeletionApi(private val transport: ApiTransport, private val expired: suspend (Session) -> Unit = {}) : AccountDeletionGateway {
    override suspend fun delete(session: Session, password: String) {
        val response = try { transport.request("DELETE", "/api/me", session.token, JSONObject().put("current_password", password).toString()) }
        catch (failure: ApiFailure) {
            if (failure.status == 401) expired(session)
            throw failure
        }
        if (response.isNotBlank()) throw IOException("Unexpected account deletion response")
    }
}
