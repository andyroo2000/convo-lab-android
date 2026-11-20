package com.cdcg.convolab.auth

import com.cdcg.convolab.network.ApiFailure
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private val original = Session("fixture-original", Account(1, "Original", "first@example.invalid"))
    private val newer = Session("fixture-new", Account(2, "New", "second@example.invalid"))

    @Test fun offlineRestoreKeepsVerifiedProfileAndCredentials() = runTest {
        val storage = MemoryStorage(original)
        val api = FakeAuth().apply { verifyResult = { throw IOException() } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        controller.restore()
        assertEquals(original.account, controller.state.value.account)
        assertSame(original, storage.saved)
        assertFalse(controller.state.value.restoring)
    }

    @Test fun unreadableStorageResetsOnlyNonTransientFailures() = runTest {
        for (failure in listOf(IOException("temporarily unavailable"), InvalidSessionStorage(IllegalArgumentException()))) {
            val storage = MemoryStorage(original).apply { readFailure = failure }
            val controller = SessionController(FakeAuth(), storage, StandardTestDispatcher(testScheduler))
            controller.restore()
            assertNull(controller.state.value.account)
            assertNotNull(controller.state.value.error)
            assertEquals(failure is InvalidSessionStorage, storage.saved == null)
        }
    }

    @Test fun unauthorizedRestoreClearsBothCachedProfileAndToken() = runTest {
        val storage = MemoryStorage(original)
        val api = FakeAuth().apply { verifyResult = { throw ApiFailure(401) } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        controller.restore()
        assertNull(controller.state.value.account)
        assertNull(controller.snapshot())
        assertNull(storage.saved)
    }

    @Test fun loginCompletingAfterSignOutCannotRecreateCredentials() = runTest {
        val response = CompletableDeferred<Session>()
        val storage = MemoryStorage()
        val api = FakeAuth().apply { loginResult = { response.await() } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        val login = launch { controller.login("first@example.invalid", "password") }
        runCurrent()
        controller.signOut()
        response.complete(original)
        login.join()
        assertEquals(original.token, api.revokedToken)
        assertNull(storage.saved)
        assertNull(controller.state.value.account)
    }

    @Test fun failedReauthenticationPreservesCurrentSession() = runTest {
        val storage = MemoryStorage(original)
        val api = FakeAuth().apply { verifyResult = { original.account }; loginResult = { throw ApiFailure(401) } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        controller.restore()
        assertFalse(controller.login("second@example.invalid", "wrong"))
        assertEquals(original.account, controller.state.value.account)
        assertEquals(original.token, storage.saved?.token)
        assertNotNull(controller.state.value.error)
    }

    @Test fun storageFailureDoesNotPublishAnUnpersistedAccount() = runTest {
        val storage = MemoryStorage().apply { failWrites = true }
        val api = FakeAuth().apply { loginResult = { original } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        controller.login("first@example.invalid", "password")
        assertNull(controller.state.value.account)
        assertNull(controller.snapshot())
        assertFalse(controller.state.value.busy)
        assertNotNull(controller.state.value.error)
        assertEquals(original.token, api.revokedToken)
    }

    @Test fun revocationFailureDoesNotUndoLocalSignOut() = runTest {
        val storage = MemoryStorage(original)
        val api = FakeAuth().apply { verifyResult = { original.account }; revokeResult = { throw IOException() } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        controller.restore()
        controller.signOut()
        assertNull(controller.state.value.account)
        assertNull(storage.saved)
        assertEquals(original.token, api.revokedToken)
    }

    @Test fun failedLocalRemovalDoesNotClaimSuccessfulSignOut() = runTest {
        val storage = MemoryStorage(original)
        val api = FakeAuth().apply { verifyResult = { original.account } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        controller.restore()
        storage.failClear = true
        controller.signOut()
        assertEquals(original.account, controller.state.value.account)
        assertNotNull(controller.state.value.error)
        assertNull(api.revokedToken)
    }

    @Test fun delayedRestoreCannotOverrideLoginOrSignOut() = runTest {
        for (replacement in listOf(null, newer)) {
            for (unauthorized in listOf(false, true)) checkRestoreRace(replacement, unauthorized)
        }
    }

    private suspend fun TestScope.checkRestoreRace(replacement: Session?, unauthorized: Boolean) {
        val response = CompletableDeferred<Account>()
        val storage = MemoryStorage(original)
        val api = FakeAuth().apply { verifyResult = { response.await() }; loginResult = { newer } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        val restore = launch { controller.restore() }
        runCurrent()
        if (replacement == null) controller.signOut() else controller.login("second@example.invalid", "password")
        if (unauthorized) response.completeExceptionally(ApiFailure(401)) else response.complete(original.account)
        restore.join()
        assertEquals(replacement?.account, controller.state.value.account)
        assertEquals(replacement?.token, storage.saved?.token)
    }

    @Test fun staleRegistrationRevokesOnlyItsUnusedToken() = runTest {
        val response = CompletableDeferred<Session>()
        val storage = MemoryStorage()
        val api = FakeAuth().apply { loginResult = { response.await() } }
        val controller = SessionController(api, storage, StandardTestDispatcher(testScheduler))
        val registration = launch { controller.register(Registration("Sample", "sample@example.invalid", "password", "INVITE")) }
        runCurrent()
        api.loginResult = { newer }
        controller.login("second@example.invalid", "password")
        response.complete(original)
        registration.join()
        assertSame(newer, storage.saved)
        assertEquals(original.token, api.revokedToken)
    }

    @Test fun passwordResetReportsSuccessOnlyAfterServerAcknowledgement() = runTest {
        val response = CompletableDeferred<Unit>()
        val api = FakeAuth().apply { resetResult = { response.await() } }
        val controller = SessionController(api, MemoryStorage(), StandardTestDispatcher(testScheduler))
        val reset = launch { controller.resetPassword("first@example.invalid") }
        runCurrent()
        assertTrue(controller.state.value.busy)
        assertFalse(controller.state.value.resetSent)
        response.complete(Unit)
        reset.join()
        assertTrue(controller.state.value.resetSent)
        assertFalse(controller.state.value.busy)
    }
}

internal class MemoryStorage(var saved: Session? = null) : SessionStorage {
    var failWrites = false
    var failClear = false
    var readFailure: Exception? = null
    override fun read(): Session? { readFailure?.let { throw it }; return saved }
    override fun write(session: Session) {
        if (failWrites) throw IOException("fixture disk failure")
        saved = session
    }
    override fun clear() {
        if (failClear) throw IOException("fixture disk failure")
        saved = null
    }
}

internal class FakeAuth : AuthGateway {
    var loginResult: suspend () -> Session = { error("Unexpected login") }
    var verifyResult: suspend () -> Account = { error("Unexpected verification") }
    var revokeResult: suspend () -> Unit = {}
    var resetResult: suspend () -> Unit = {}
    var revokedToken: String? = null
    override suspend fun login(email: String, password: String) = loginResult()
    override suspend fun register(registration: Registration) = loginResult()
    override suspend fun verify(token: String) = verifyResult()
    override suspend fun resetPassword(email: String) = resetResult()
    override suspend fun revoke(token: String) { revokedToken = token; revokeResult() }
}
