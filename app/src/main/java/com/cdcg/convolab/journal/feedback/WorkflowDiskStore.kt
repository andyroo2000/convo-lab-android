package com.cdcg.convolab.journal.feedback

import android.util.AtomicFile
import com.cdcg.convolab.journal.JournalId
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONObject
import org.json.JSONException
import java.util.UUID

/** Raw server JSON preserves fields used by later conversation/audio versions of the client. */
class WorkflowDiskStore(noBackupDirectory: File, accountId: Long) : WorkflowStorage {
    private val directory = File(noBackupDirectory, "journal-workflow/$accountId")

    @Synchronized override fun read(id: JournalId): WorkflowSnapshot? {
        val bytes = try { file(id).readFully() } catch (_: FileNotFoundException) { return null }
        return try { decode(id, bytes) }
        catch (_: JSONException) { throw WorkflowRecoveryFailure(WorkflowRecovery(null)) }
        catch (_: IllegalArgumentException) { throw WorkflowRecoveryFailure(WorkflowRecovery(null)) }
    }

    private fun decode(id: JournalId, bytes: ByteArray): WorkflowSnapshot {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("schemaVersion") == 1)
        require(json.getString("entryId") == id.value)
        return WorkflowSnapshot(id, json.getInt("sourceVersion"),
            json.optJSONObject("clarification")?.let(FeedbackJson::clarification),
            json.optJSONObject("analysis")?.let(FeedbackJson::analysis),
            json.optJSONObject("pendingAnswer")?.let { ClarificationAnswer(it.getString("clarificationId"), it.getString("questionId"), it.getString("text")) },
            json.getBoolean("submissionRequested"), json.optJSONObject("pendingFinalEdit")?.let {
                FinalEdit(it.getString("analysisId"), it.getInt("expectedRevision"), it.getString("text"))
            })
    }

    @Synchronized override fun archive(id: JournalId) {
        val source = file(id)
        if (!source.baseFile.exists() && !File(source.baseFile.path + ".bak").exists()) return
        val recovery = File(directory, "recovery")
        check(recovery.isDirectory || recovery.mkdirs())
        val archived = AtomicFile(File(recovery, "${id.value}-${UUID.randomUUID()}.json"))
        val output = archived.startWrite()
        try {
            source.openRead().use { it.copyTo(output) }
            archived.finishWrite(output)
        } catch (failure: Exception) {
            archived.failWrite(output)
            throw failure
        }
        source.delete()
        check(!source.baseFile.exists()) { "The recovery copy is saved, but the original could not be moved." }
    }

    @Synchronized override fun write(snapshot: WorkflowSnapshot) {
        read(snapshot.entryId) // Preserve unreadable/unknown-version work instead of overwriting it.
        check(directory.isDirectory || directory.mkdirs())
        val file = file(snapshot.entryId)
        val stream = file.startWrite()
        try {
            stream.write(encode(snapshot).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    private fun encode(snapshot: WorkflowSnapshot): String = JSONObject().put("schemaVersion", 1)
        .put("entryId", snapshot.entryId.value).put("sourceVersion", snapshot.sourceVersion)
        .put("clarification", snapshot.clarification?.let { JSONObject(it.encoded) } ?: JSONObject.NULL)
        .put("analysis", snapshot.analysis?.let { JSONObject(it.encoded) } ?: JSONObject.NULL)
        .put("pendingAnswer", snapshot.pendingAnswer?.let { JSONObject().put("clarificationId", it.clarificationId)
            .put("questionId", it.questionId).put("text", it.text) } ?: JSONObject.NULL)
        .put("submissionRequested", snapshot.submissionRequested)
        .put("pendingFinalEdit", snapshot.pendingFinalEdit?.let { JSONObject().put("analysisId", it.analysisId)
            .put("expectedRevision", it.expectedRevision).put("text", it.text) } ?: JSONObject.NULL).toString()

    private fun file(id: JournalId) = AtomicFile(File(directory, id.value + ".json"))
}
