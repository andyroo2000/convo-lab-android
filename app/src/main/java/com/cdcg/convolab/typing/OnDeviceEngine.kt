package com.cdcg.convolab.typing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OnDeviceEngine(context: Context) : TypingEngine {
    private val assets = EngineAssets(context.applicationContext)
    private var handle = 0L
    private var closed = false

    override suspend fun prepare() = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            if (handle == 0L) {
                handle = NativeTyping.create(assets.prepare().absolutePath.toByteArray(Charsets.UTF_8))
                check(handle != 0L) { "The on-device model could not start" }
                try {
                    convertLocked("konnichiha")
                } catch (failure: Exception) {
                    NativeTyping.destroy(handle)
                    handle = 0L
                    throw failure
                }
            }
        }
    }

    override suspend fun convert(text: String): Conversion = withContext(Dispatchers.Default) {
        require(text.isNotBlank() && TypingLimits.accepts(text))
        mutex.withLock {
            check(handle != 0L && !closed)
            convertLocked(text)
        }
    }

    private fun convertLocked(text: String): Conversion {
        val request = JSONObject().put("text", text).put("finalize", true)
        val bytes = checkNotNull(NativeTyping.convert(handle, request.toString().toByteArray(Charsets.UTF_8))) {
            "Conversion failed. Try a shorter phrase."
        }
        val result = JSONObject(bytes.toString(Charsets.UTF_8))
        check(!result.has("error")) { result.optString("error") }
        val candidates = result.getJSONArray("candidates")
        return Conversion(result.getString("reading"), List(candidates.length()) { candidates.getString(it) },
            result.getDouble("engineMs"), result.optDouble("resetMs", 0.0))
    }

    companion object {
        // Native backend configuration and asset installation are process-wide.
        private val mutex = Mutex()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) NativeTyping.destroy(handle)
            handle = 0L
            closed = true
        }
    }
}
