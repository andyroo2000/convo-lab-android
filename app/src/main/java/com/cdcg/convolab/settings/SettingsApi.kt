package com.cdcg.convolab.settings

import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.journal.JournalApi
import org.json.JSONObject

class SettingsApi(private val api: JournalApi) : SettingsGateway {
    override suspend fun profile(change: ProfileChange): Account {
        val body = JSONObject().put("name", change.name.trim()).put("email", change.email.trim())
        val data = api.request("PUT", "/api/me", body).getJSONObject("data")
        return Account(data.getLong("id"), data.getString("name"), data.getString("email"))
    }

    override suspend fun password(change: PasswordChange) {
        val body = JSONObject().put("current_password", change.current).put("password", change.password)
            .put("password_confirmation", change.confirmation)
        api.request("PUT", "/api/me/password", body)
    }

    override suspend fun knownKanji() = KnownKanjiJson.decode(api.request("GET", "/api/study/known-kanji"))
    override suspend fun connect(token: String): KnownKanjiSnapshot {
        val clean = token.trim()
        require(clean.isNotEmpty() && clean.length <= 512)
        return KnownKanjiJson.decode(api.request("PUT", "/api/study/wanikani", JSONObject().put("apiToken", clean)))
    }
    override suspend fun sync() { api.request("POST", "/api/study/wanikani/sync") }
    override suspend fun disconnect() { api.request("DELETE", "/api/study/wanikani") }
}
