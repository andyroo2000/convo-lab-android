package com.cdcg.convolab.journal.feedback

import android.util.AtomicFile
import androidx.compose.runtime.compositionLocalOf
import com.cdcg.convolab.journal.JournalIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Small account-owned display choices; its parent scope joins pending writes before cleanup. */
class JournalViewPreferences(root: File, accountId: Long, private val scope: CoroutineScope) {
    init { require(accountId > 0) }
    private val directory = File(root, "journal-preferences/$accountId")
    private val file = AtomicFile(File(directory, "versions.json"))
    private val mutable = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val versions = mutable.asStateFlow()
    private var work = scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) {
        val restored = withContext(Dispatchers.IO) { read() }
        mutable.update { restored + it }
    } }
    fun select(entryId: String, final: Boolean) {
        val id = JournalIds.normalize(entryId)
        mutable.update { it + (id to final) }
        val previous = work
        work = scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) {
            previous.join()
            val snapshot = mutable.value
            withContext(Dispatchers.IO) { write(snapshot) }
        } }
    }
    private fun read(): Map<String, Boolean> = try {
        val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
        json.keys().asSequence().mapNotNull { key ->
            runCatching { JournalIds.normalize(key) }.getOrNull()?.let { it to json.getBoolean(key) }
        }.toMap()
    } catch (_: IOException) { emptyMap() }
    catch (_: org.json.JSONException) { emptyMap() }

    private fun write(values: Map<String, Boolean>) {
        if (!directory.isDirectory && !directory.mkdirs()) return
        val json = JSONObject()
        values.forEach { (id, final) -> json.put(id, final) }
        try {
            val stream = file.startWrite()
            try { stream.write(json.toString().toByteArray()); file.finishWrite(stream) }
            catch (failure: IOException) { file.failWrite(stream) }
        } catch (_: IOException) { /* The visible selection stays usable if preferences cannot be saved. */ }
    }
}
val LocalJournalViewPreferences = compositionLocalOf<JournalViewPreferences?> { null }
