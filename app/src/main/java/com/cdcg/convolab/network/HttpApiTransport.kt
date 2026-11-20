package com.cdcg.convolab.network

import com.cdcg.convolab.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class HttpApiTransport : ApiTransport {
    override suspend fun request(method: String, path: String, token: String?, body: String?): String =
        withContext(Dispatchers.IO) {
            require(path.startsWith("/api/") && !path.contains('#'))
            val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
            try {
                configure(connection, method, token)
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { stream -> stream.write(it.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) throw ApiFailure(status)
                val bytes = connection.inputStream.use(::readLimited)
                currentCoroutineContext().ensureActive()
                bytes.toString(Charsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }

    private fun configure(connection: HttpURLConnection, method: String, token: String?) {
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Response exceeds limit")
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
    }
}
