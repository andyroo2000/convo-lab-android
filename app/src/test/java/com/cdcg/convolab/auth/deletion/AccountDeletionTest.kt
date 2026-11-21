package com.cdcg.convolab.auth.deletion

import com.cdcg.convolab.auth.*
import com.cdcg.convolab.network.ApiFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionTest {
    @Test fun wrongPasswordDoesNotTouchCredentialsOrLocalData() = runTest {
        val fixture = fixture()
        val controller = fixture.controller(this) { _, _ -> throw ApiFailure(422) }
        controller.open(); controller.delete("wrong"); advanceUntilIdle()
        assertNotNull(fixture.sessions.snapshot())
        assertTrue(fixture.disk.marked.isEmpty())
        assertFalse(controller.state.value.confirmed)
        assertNotNull(controller.state.value.error)
    }
    @Test fun acknowledgedDeletionWaitsForLastFlushAndBlocksNewWriters() = runTest {
        val fixture = fixture()
        val release = CompletableDeferred<Unit>()
        var flushed = false
        val writer = launch {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { release.await(); flushed = true } }
        }
        fixture.sessions.work.own(1, writer)
        val controller = fixture.controller(this)
        controller.open(); controller.delete("fixture"); runCurrent()
        assertTrue(fixture.disk.purged.isEmpty())
        val late = Job()
        assertFalse(fixture.sessions.work.own(1, late)); late.complete()
        release.complete(Unit); advanceUntilIdle()
        assertTrue(flushed)
        assertEquals(listOf(1L), fixture.disk.purged)
        assertNull(fixture.sessions.snapshot())
        assertNull(fixture.credentials.saved)
        assertFalse(controller.state.value.pending)
    }
    @Test fun cleanupFailureIsRetriedFromDurableMarkerAfterRestart() = runTest {
        val fixture = fixture()
        fixture.disk.failPurge = true
        val controller = fixture.controller(this)
        controller.open(); controller.delete("fixture"); advanceUntilIdle()
        assertNull(fixture.sessions.snapshot())
        assertTrue(controller.state.value.pending)
        assertEquals(setOf(1L), fixture.disk.marked)
        fixture.disk.failPurge = false
        val restarted = fixture.controller(this) { _, _ -> error("Must not delete remotely twice") }
        restarted.recover()
        assertTrue(fixture.disk.marked.isEmpty())
        assertFalse(restarted.state.value.pending)
    }
    @Test fun lateDeletionDoesNotEraseReplacementAccountOrItsWork() = runTest {
        val fixture = fixture()
        val acknowledged = CompletableDeferred<Unit>()
        val controller = fixture.controller(this) { _, _ -> acknowledged.await() }
        controller.open(); controller.delete("fixture"); runCurrent()
        fixture.auth.result = deletionSession(2)
        fixture.sessions.login("second@example.invalid", "fixture")
        val other = Job()
        fixture.sessions.work.own(2, other)
        acknowledged.complete(Unit); advanceUntilIdle()
        assertEquals(2L, fixture.sessions.snapshot()?.account?.id)
        assertEquals(2L, fixture.credentials.saved?.account?.id)
        assertTrue(other.isActive); other.complete()
        assertEquals(listOf(1L), fixture.disk.purged)
    }
    @Test fun failedIntentWriteStillSignsOutButNeverClaimsCleanupComplete() = runTest {
        val fixture = fixture()
        fixture.disk.failMarker = true
        val controller = fixture.controller(this)
        controller.open(); controller.delete("fixture"); advanceUntilIdle()
        assertNull(fixture.sessions.snapshot())
        assertTrue(controller.state.value.pending)
        assertEquals(listOf(1L), fixture.disk.purged)
        fixture.disk.failMarker = false
        controller.retry(); advanceUntilIdle()
        assertFalse(controller.state.value.pending)
    }
    @Test fun credentialClearFailureDoesNotPreventConfirmedFileRemoval() = runTest {
        val fixture = fixture()
        fixture.credentials.failClear = true
        val controller = fixture.controller(this)
        controller.open(); controller.delete("fixture"); advanceUntilIdle()
        assertNull(fixture.sessions.snapshot())
        assertNotNull(fixture.credentials.saved)
        assertEquals(listOf(1L), fixture.disk.purged)
        assertTrue(controller.state.value.pending)
        fixture.credentials.failClear = false
        controller.retry(); advanceUntilIdle()
        assertNull(fixture.credentials.saved)
        assertFalse(controller.state.value.pending)
    }
    @Test fun unreadableCleanupMarkersBlockStartupRestoreUntilRetry() = runTest {
        val fixture = fixture()
        fixture.disk.failRead = true
        var restored = false
        val controller = AccountDeletionController(fixture.sessions, AccountDeletionGateway { _, _ -> }, fixture.disk, this) { restored = true }
        controller.recover()
        assertFalse(restored)
        assertTrue(controller.state.value.pending)
        fixture.disk.failRead = false
        controller.retry(); advanceUntilIdle()
        assertTrue(restored)
    }
    private suspend fun TestScope.fixture(): DeletionFixture {
        val credentials = DeletionCredentials()
        val auth = DeletionAuth()
        val sessions = SessionController(auth, credentials, StandardTestDispatcher(testScheduler))
        sessions.restore()
        return DeletionFixture(sessions, credentials, auth, DeletionDisk())
    }
}

private class DeletionFixture(val sessions: SessionController, val credentials: DeletionCredentials, val auth: DeletionAuth, val disk: DeletionDisk) {
    fun controller(scope: CoroutineScope, delete: suspend (Session, String) -> Unit = { _, _ -> }) =
        AccountDeletionController(sessions, AccountDeletionGateway(delete), disk, scope)
}
private fun deletionSession(id: Long) = Session("fixture-$id", Account(id, "Fixture", "$id@example.invalid"))
private class DeletionCredentials : SessionStorage {
    var saved: Session? = deletionSession(1)
    var failClear = false
    override fun read() = saved
    override fun write(session: Session) { saved = session }
    override fun clear() { if (failClear) throw IOException(); saved = null }
}
private class DeletionAuth : AuthGateway {
    var result = deletionSession(1)
    override suspend fun verify(token: String) = result.account
    override suspend fun login(email: String, password: String) = result
    override suspend fun register(registration: Registration) = result
    override suspend fun resetPassword(email: String) = Unit
    override suspend fun revoke(token: String) = Unit
}
private class DeletionDisk : AccountCleanupStorage {
    val marked = mutableSetOf<Long>()
    val purged = mutableListOf<Long>()
    var failPurge = false
    var failMarker = false
    var failRead = false
    override suspend fun markConfirmed(accountId: Long) {
        if (failMarker) throw IOException()
        marked.add(accountId)
    }
    override suspend fun pending(): Set<Long> {
        if (failRead) throw IOException()
        return marked.toSet()
    }
    override suspend fun purge(accountId: Long) {
        if (failPurge) throw IOException()
        purged.add(accountId)
    }
    override suspend fun complete(accountId: Long) { marked.remove(accountId) }
}
