package com.cdcg.convolab.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.journal.JournalApi
import com.cdcg.convolab.network.ApiTransport
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContractTest {
    private val session = Session("fixture-token", Account(101, "Sample", "sample@example.invalid"))

    @Test fun profileAndPasswordUseTheExistingAccountWireContract() = runBlocking {
        val transport = SettingsTransport { "{\"data\":{\"id\":101,\"name\":\"New\",\"email\":\"new@example.invalid\"}}" }
        val api = SettingsApi(JournalApi(transport, session) { session })
        assertEquals("New", api.profile(ProfileChange(" New ", "new@example.invalid")).name)
        assertEquals("PUT /api/me", transport.requests.last())
        assertEquals("New", transport.body().getString("name"))
        transport.reply = { "" }
        api.password(PasswordChange("fixture-current", "fixture-new-password", "fixture-new-password"))
        assertEquals("PUT /api/me/password", transport.requests.last())
        assertEquals("fixture-current", transport.body().getString("current_password"))
        assertEquals("fixture-new-password", transport.body().getString("password_confirmation"))
    }

    @Test fun connectionSnapshotIsUnwrappedAndNeverUsesTransferEndpoints() = runBlocking {
        val transport = SettingsTransport { snapshotJson().toString() }
        val api = SettingsApi(JournalApi(transport, session) { session })
        val connected = api.connect(" fixture-provider-token ")
        assertEquals("fixture-provider-token", transport.body().getString("apiToken"))
        assertTrue(connected.connected)
        assertEquals(setOf('映'.code, '画'.code, 0x20BB7), connected.kanji)
        api.sync()
        api.knownKanji()
        api.disconnect()
        assertEquals(listOf("PUT /api/study/wanikani", "POST /api/study/wanikani/sync", "GET /api/study/known-kanji", "DELETE /api/study/wanikani"), transport.requests)
    }

    @Test fun knowledgeCacheReopensSeparatelyForEachAccountWithoutProviderSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "settings-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val snapshot = KnownKanjiJson.decode(snapshotJson().put("apiToken", "fixture-secret-must-not-persist"))
            KnownKanjiDiskStore(directory, 101).write(snapshot)
            assertEquals(snapshot, KnownKanjiDiskStore(directory, 101).read())
            assertNull(KnownKanjiDiskStore(directory, 202).read())
            val file = File(directory, "known-kanji/101/snapshot.json")
            assertFalse(file.readText().contains("fixture-secret"))
            assertFalse(file.readText().contains("transferBridge"))
            assertTrue(file.renameTo(File(file.path + ".bak")))
            file.writeText("interrupted write")
            assertEquals(snapshot, KnownKanjiDiskStore(directory, 101).read())
        } finally { directory.deleteRecursively() }
    }

    private fun snapshotJson() = JSONObject().put("version", 8).put("kanji", JSONArray(listOf("映", "画", "𠮷", "invalid")))
        .put("wanikani", JSONObject().put("connected", true).put("lastSyncedAt", "2026-11-20T12:00:00Z")
            .put("transferBridge", JSONObject().put("enabled", true)))
}

internal class SettingsTransport(var reply: () -> String) : ApiTransport {
    val requests = mutableListOf<String>()
    private var payload: String? = null
    fun body() = JSONObject(requireNotNull(payload))
    override suspend fun request(method: String, path: String, token: String?, body: String?): String {
        requests += "$method $path"
        payload = body
        return reply()
    }
}
