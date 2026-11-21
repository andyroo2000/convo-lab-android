package com.cdcg.convolab.journal

import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.network.ApiTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JournalSessionExpiryTest {
    @Test fun unauthorizedTransportExpiresOnlyItsCapturedSession() = runBlocking {
        val session = Session("fixture", Account(101, "Fixture", "fixture@example.invalid"))
        val expired = mutableListOf<Session>()
        for (status in listOf(401, 403, 422, 503)) {
            val api = JournalApi(ExpiryTransport(status), session, { session }, { expired.add(it) })
            try { api.archive(); fail("Must reject") }
            catch (failure: ApiFailure) { assertEquals(status, failure.status) }
        }
        assertEquals(listOf(session), expired)
    }
}
private class ExpiryTransport(private val status: Int) : ApiTransport {
    override suspend fun request(method: String, path: String, token: String?, body: String?): String = throw ApiFailure(status)
}
