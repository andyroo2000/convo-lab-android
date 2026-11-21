package com.cdcg.convolab.network

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HttpApiTransportTest {
    @Test fun cancellationDisconnectsAnInFlightRead() = runBlocking {
        val connection = BlockingConnection()
        val transport = HttpApiTransport { connection }
        val request = launch(kotlinx.coroutines.Dispatchers.Default) { transport.request("GET", "/api/me") }
        assertTrue(connection.started.await(5, TimeUnit.SECONDS))
        request.cancelAndJoin()
        assertEquals(0L, connection.disconnected.count)
    }

    @Test fun accountResponseHasASmallerBound() = runBlocking {
        val connection = object : HttpURLConnection(URL("https://example.invalid/api/me")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream() = ByteArrayInputStream(ByteArray(1024 * 1024 + 1))
        }
        try {
            HttpApiTransport { connection }.request("GET", "/api/me")
            fail("Oversized account response accepted")
        } catch (_: IOException) { /* expected */ }
    }
}

private class BlockingConnection : HttpURLConnection(URL("https://example.invalid/api/me")) {
    val started = CountDownLatch(1)
    val disconnected = CountDownLatch(1)
    override fun connect() = Unit
    override fun disconnect() { disconnected.countDown() }
    override fun usingProxy() = false
    override fun getResponseCode(): Int {
        started.countDown()
        check(disconnected.await(5, TimeUnit.SECONDS)) { "Cancellation did not interrupt the request" }
        throw IOException("Disconnected")
    }
}
