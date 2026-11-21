package com.cdcg.convolab.settings

import com.cdcg.convolab.auth.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsControllerTest {
    private val session = Session("fixture-first", Account(1, "Sample", "sample@example.invalid"))
    private val known = KnownKanjiSnapshot(5, setOf('映'.code, '画'.code), true, "2026-11-20T12:00:00Z")

    @Test fun successfulConnectionClearsTokenEvenIfItsInitialSyncFails() = runTest {
        val api = SettingsFixture().apply { snapshot = known; failSync = true }
        val storage = KnownMemory()
        val controller = controller(api, storage)
        var cleared = false
        controller.connect("fixture-provider-token") { cleared = true }.join()
        assertTrue(cleared)
        assertTrue(controller.state.value.known.connected)
        assertEquals(known, storage.snapshot)
        assertNotNull(controller.state.value.error)
        api.failSync = false
        controller.sync().join()
        assertNull(controller.state.value.error)
        assertEquals(listOf("connect", "sync", "sync", "known"), api.calls)
    }

    @Test fun unreadableCacheDoesNotPreventServerRefresh() = runTest {
        val api = SettingsFixture().apply { snapshot = known }
        val storage = KnownMemory().apply { failRead = true }
        val controller = controller(api, storage)
        controller.start().join()
        assertEquals(known, controller.state.value.known)
        assertEquals(known, storage.snapshot)
        assertNull(controller.state.value.error)
    }

    @Test fun cacheWriteFailureStillShowsConnectionAndClearsProviderToken() = runTest {
        val api = SettingsFixture().apply { snapshot = known }
        val storage = KnownMemory().apply { failWrite = true }
        val controller = controller(api, storage)
        var cleared = false
        controller.connect("fixture-token") { cleared = true }.join()
        assertTrue(cleared)
        assertEquals(known, controller.state.value.known)
        assertTrue(controller.state.value.error!!.contains("offline"))
        storage.failWrite = false
        controller.reload().join()
        assertEquals(known, storage.snapshot)
        assertNull(controller.state.value.error)
    }

    @Test fun olderServerSnapshotsNeverReplaceNewerCachedKnowledge() = runTest {
        val storage = KnownMemory().apply { snapshot = known }
        val api = SettingsFixture().apply { snapshot = KnownKanjiSnapshot(4) }
        val controller = controller(api, storage)
        controller.start().join()
        assertEquals(known, controller.state.value.known)
        assertEquals(known, storage.snapshot)
    }

    @Test fun disconnectRefreshesConnectionWhileKeepingKnownKanji() = runTest {
        val api = SettingsFixture().apply { snapshot = known }
        val storage = KnownMemory()
        val controller = controller(api, storage)
        controller.start().join()
        controller.disconnect().join()
        assertFalse(controller.state.value.known.connected)
        assertEquals(known.kanji, controller.state.value.known.kanji)
        assertEquals(controller.state.value.known, storage.snapshot)
    }

    @Test fun profileUpdatePersistsTheNewAccountWithoutReplacingTheSessionToken() = runTest {
        val auth = FakeAuth().apply { loginResult = { session } }
        val credentials = MemoryStorage()
        val sessions = SessionController(auth, credentials, StandardTestDispatcher(testScheduler))
        sessions.login("sample@example.invalid", "fixture-password")
        val api = SettingsFixture().apply { profileResult = { session.account.copy(name = "Updated", email = "updated@example.invalid") } }
        val controller = SettingsController(SettingsAccess(session, api, KnownMemory(), sessions, StandardTestDispatcher(testScheduler)), backgroundScope)
        var done = false
        controller.updateProfile("Updated", "updated@example.invalid") { done = true }.join()
        assertTrue(done)
        assertEquals(session.token, sessions.snapshot()?.token)
        assertEquals("Updated", credentials.saved?.account?.name)
        assertEquals("Updated", controller.state.value.account.name)
    }

    @Test fun delayedProfileResponseCannotChangeAReplacementSession() = runTest {
        val auth = FakeAuth().apply { loginResult = { session } }
        val credentials = MemoryStorage()
        val sessions = SessionController(auth, credentials, StandardTestDispatcher(testScheduler))
        sessions.login("sample@example.invalid", "fixture-password")
        val response = CompletableDeferred<Account>()
        val api = SettingsFixture().apply { profileResult = { response.await() } }
        val controller = SettingsController(SettingsAccess(session, api, KnownMemory(), sessions, StandardTestDispatcher(testScheduler)), backgroundScope)
        var done = false
        val update = controller.updateProfile("Old", "old@example.invalid") { done = true }
        runCurrent()
        sessions.signOut()
        val replacement = Session("fixture-next", Account(2, "Next", "next@example.invalid"))
        auth.loginResult = { replacement }
        sessions.login("next@example.invalid", "fixture-password")
        response.complete(session.account.copy(name = "Old"))
        update.join()
        assertFalse(done)
        assertEquals(replacement.account, credentials.saved?.account)
        assertEquals(replacement.token, sessions.snapshot()?.token)
    }
    private fun TestScope.controller(api: SettingsGateway, storage: KnownKanjiStorage): SettingsController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sessions = SessionController(FakeAuth(), MemoryStorage(), dispatcher)
        return SettingsController(SettingsAccess(session, api, storage, sessions, dispatcher), backgroundScope)
    }

}

private class KnownMemory : KnownKanjiStorage {
    var snapshot: KnownKanjiSnapshot? = null
    var failRead = false
    var failWrite = false
    override fun read(): KnownKanjiSnapshot? { if (failRead) throw IOException("cache unavailable"); return snapshot }
    override fun write(snapshot: KnownKanjiSnapshot) { if (failWrite) throw IOException("disk full"); this.snapshot = snapshot }
}

private class SettingsFixture : SettingsGateway {
    var snapshot = KnownKanjiSnapshot()
    var failSync = false
    var profileResult: suspend () -> Account = { error("Unexpected profile request") }
    val calls = mutableListOf<String>()
    override suspend fun profile(change: ProfileChange) = profileResult()
    override suspend fun password(change: PasswordChange) = Unit
    override suspend fun knownKanji(): KnownKanjiSnapshot { calls += "known"; return snapshot }
    override suspend fun connect(token: String): KnownKanjiSnapshot { calls += "connect"; return snapshot }
    override suspend fun sync() { calls += "sync"; if (failSync) throw IOException("fixture offline") }
    override suspend fun disconnect() { calls += "disconnect"; snapshot = snapshot.copy(connected = false) }
}
