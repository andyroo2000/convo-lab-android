package com.cdcg.convolab.auth

import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class SessionExpiryTest {
    private val original = Session("fixture-old", Account(1, "First", "first@example.invalid"))
    private val replacement = Session("fixture-new", Account(2, "Second", "second@example.invalid"))
    @Test fun expiredRequestClearsMatchingCredentialsAndAllowsSameAccountToSignInAgain() = runTest {
        val storage = MemoryStorage(original)
        val auth = FakeAuth().apply { verifyResult = { original.account }; loginResult = { original } }
        val sessions = SessionController(auth, storage, StandardTestDispatcher(testScheduler))
        sessions.restore()
        sessions.expire(original)
        assertNull(sessions.snapshot())
        assertNull(storage.saved)
        assertTrue(sessions.state.value.error!!.contains("writing is kept"))
        assertTrue(sessions.login(original.account.email, "fixture"))
        assertEquals(original.account, sessions.state.value.account)
    }
    @Test fun anOldUnauthorizedResponseCannotSignOutTheReplacementAccount() = runTest {
        val storage = MemoryStorage()
        val auth = FakeAuth().apply { loginResult = { replacement } }
        val sessions = SessionController(auth, storage, StandardTestDispatcher(testScheduler))
        sessions.login(replacement.account.email, "fixture")
        sessions.expire(original)
        assertSame(replacement, sessions.snapshot())
        assertSame(replacement, storage.saved)
    }
    @Test fun failedCredentialRemovalStillReturnsToSignInWithoutClaimingRemoval() = runTest {
        val storage = MemoryStorage(original).apply { failClear = true }
        val auth = FakeAuth().apply { verifyResult = { original.account } }
        val sessions = SessionController(auth, storage, StandardTestDispatcher(testScheduler))
        sessions.restore()
        sessions.expire(original)
        assertNull(sessions.state.value.account)
        assertNotNull(storage.saved)
        assertTrue(sessions.state.value.error!!.contains("could not be removed"))
    }
}
