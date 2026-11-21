package com.cdcg.convolab.journal.input

import android.content.Context
import com.cdcg.convolab.typing.EngineAssets
import com.cdcg.convolab.typing.NativeOperation
import com.cdcg.convolab.typing.NativeTyping
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** All native owners share one lock, including creation, learning and destruction. */
class NativeJournalTyping(context: Context, accountId: Long) : JournalTypingEngine {
    private val assets = EngineAssets(context.applicationContext)
    private val learning = File(context.noBackupFilesDir, "journal-typing/$accountId")
    private var handle = 0L
    private var closed = false
    init { require(accountId > 0) }

    override suspend fun prepare() = withContext(Dispatchers.IO) {
        NativeOperation.mutex.withLock {
            check(!closed)
            if (handle == 0L) {
                val config = JSONObject().put("directory", assets.prepare().absolutePath).put("learningDirectory", learning.absolutePath)
                handle = NativeTyping.create(config.toString().toByteArray(Charsets.UTF_8))
                check(handle != 0L) { "Japanese input could not start" }
            }
        }
    }
    override suspend fun candidates(query: CompositionQuery, expanded: Boolean): List<JapaneseCandidate> {
        val result = request(JSONObject().put("reading", query.reading).put("boundaries", JSONArray(query.boundaries)).put("expanded", expanded))
        val choices = result.getJSONArray("candidates")
        return List(choices.length()) { index ->
            val item = choices.getJSONObject(index)
            JapaneseCandidate(item.getString("text"), item.getString("reading"), parts(item.getJSONArray("sections")), parts(item.getJSONArray("units")))
        }
    }
    override suspend fun learn(choice: ConfirmedInput) {
        request(JSONObject().put("action", "learn").put("reading", choice.reading).put("text", choice.text).put("start", choice.start).put("end", choice.end))
    }
    override suspend fun reset() { request(JSONObject().put("action", "resetLearning")) }
    private suspend fun request(value: JSONObject): JSONObject = withContext(Dispatchers.Default) {
        NativeOperation.mutex.withLock {
            check(!closed && handle != 0L)
            NativeOperation.run(handle) {
                val bytes = checkNotNull(NativeTyping.convert(handle, value.toString().toByteArray(Charsets.UTF_8)))
                val result = JSONObject(bytes.toString(Charsets.UTF_8))
                check(!result.has("error")) { "Japanese input could not complete this request" }
                result
            }
        }
    }
    override suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        NativeOperation.mutex.withLock {
            if (handle != 0L) NativeTyping.destroy(handle)
            handle = 0L
            closed = true
        }
    }
    private fun parts(values: JSONArray) = List(values.length()) { index ->
        val value = values.getJSONObject(index)
        CandidatePart(value.getString("text"), value.getString("reading"))
    }
}
