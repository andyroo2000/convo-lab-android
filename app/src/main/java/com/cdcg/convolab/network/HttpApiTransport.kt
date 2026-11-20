package com.cdcg.convolab.network

import com.cdcg.convolab.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext

class HttpApiTransport(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : ApiTransport {
    override suspend fun request(method: String, path: String, token: String?, body: String?): String =
        withContext(Dispatchers.IO) {
            require(path.startsWith("/api/") && !path.contains('#'))
            val connection = openConnection(URL(BuildConfig.API_BASE_URL + path))
            configure(connection, method, token)
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { connection.disconnect() }
                try {
                    if (continuation.isActive) continuation.resume(execute(connection, body))
                } catch (failure: Exception) {
                    continuation.resumeWithException(failure)
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun execute(connection: HttpURLConnection, body: String?): String {
        body?.let {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { stream -> stream.write(it.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        if (status !in 200..299) throw ApiFailure(status)
        val limit = if (connection.url.path.startsWith("/api/journal/")) JOURNAL_RESPONSE_BYTES else ACCOUNT_RESPONSE_BYTES
        return connection.inputStream.use { readLimited(it, limit) }.toString(Charsets.UTF_8)
    }

    private fun configure(connection: HttpURLConnection, method: String, token: String?) {
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        val revoking = connection.url.path == "/api/auth/tokens/current"
        connection.connectTimeout = if (revoking) 5_000 else 15_000
        connection.readTimeout = if (revoking) 5_000 else 30_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
    }

    private fun readLimited(stream: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) throw IOException("Response exceeds limit")
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val ACCOUNT_RESPONSE_BYTES = 1024 * 1024
        const val JOURNAL_RESPONSE_BYTES = 32 * 1024 * 1024
    }
}
