package com.cdcg.convolab.journal.feedback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.network.ApiTransport
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackContractTest {
    private val entry = JournalEntry(JournalIds.newId(), null, "今日は晴れ。", "2026-11-20", 3, "created", "updated")
    private val clarificationId = JournalIds.newId()
    private val questionId = JournalIds.newId()
    private val analysisId = JournalIds.newId()
    private val session = Session("fixture-token", Account(101, "Sample", "sample@example.invalid"))

    @Test fun clarificationAnswerAndSubmitPreserveVersionAndRetryIdentifiers() = runBlocking {
        val transport = FeedbackTransport { JSONObject().put("data", clarificationJson()).toString() }
        val api = FeedbackApi(JournalApi(transport, session) { session })
        assertEquals(WorkStatus.Waiting, api.startClarification(entry).status)
        assertEquals(3, transport.json().getInt("expectedVersion"))
        val answer = ClarificationAnswer(clarificationId.uppercase(), questionId.uppercase(), "昨日です。")
        api.answer(entry, answer)
        assertEquals("/api/journal/entries/${entry.id}/clarification/$clarificationId/answer", transport.path)
        assertEquals(questionId, transport.json().getString("questionId"))
        assertEquals(answer.text, transport.json().getString("answer"))
        transport.reply = { JSONObject().put("data", analysisJson()).toString() }
        api.submit(entry, clarificationId)
        assertEquals("POST", transport.method)
        assertEquals(clarificationId, transport.json().getString("clarificationId"))
        assertEquals(3, transport.json().getInt("expectedVersion"))
    }

    @Test fun finalEditingUsesOpeningRevisionAndDecodesCanonicalDocument() = runBlocking {
        val transport = FeedbackTransport { JSONObject().put("data", analysisJson()).toString() }
        val api = FeedbackApi(JournalApi(transport, session) { session })
        val edit = FinalEdit(analysisId.uppercase(), 4, "昨日は晴れでした。")
        val result = api.editFinal(entry, edit)
        assertEquals("PUT", transport.method)
        assertTrue(transport.path.endsWith("/corrected"))
        assertEquals(4, transport.json().getInt("expectedRevision"))
        assertEquals(analysisId, transport.json().getString("analysisId"))
        assertEquals(edit.text, result.finalText)
        assertEquals("昨日[きのう]は晴[は]れでした。", result.finalReading)
        assertEquals(5, result.replyRevision)
        assertEquals(SegmentKind.Correction, result.feedback?.segments?.single()?.kind)
    }

    @Test fun interruptedAnswerAndFinalEditSurviveAtomicReopenAndRemainAccountScoped() = isolated { directory ->
        val pending = WorkflowSnapshot(JournalId(entry.id), 3, FeedbackJson.clarification(clarificationJson()),
            FeedbackJson.analysis(analysisJson()), ClarificationAnswer(clarificationId, questionId, "昨日です。"),
            true, FinalEdit(analysisId, 4, "自分の修正。"))
        WorkflowDiskStore(directory, 101).write(pending)
        assertEquals(pending, WorkflowDiskStore(directory, 101).read(pending.entryId))
        assertNull(WorkflowDiskStore(directory, 202).read(pending.entryId))
        val file = File(directory, "journal-workflow/101/${entry.id}.json")
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("interrupted replacement")
        assertEquals(pending, WorkflowDiskStore(directory, 101).read(pending.entryId))
    }

    @Test fun unknownSchemaCannotOverwriteAStoredAnswer() = isolated { directory ->
        val storage = WorkflowDiskStore(directory, 101)
        val pending = WorkflowSnapshot(JournalId(entry.id), 3)
        storage.write(pending)
        val file = File(directory, "journal-workflow/101/${entry.id}.json")
        val original = "{\"schemaVersion\":99,\"answer\":\"kept\"}"
        file.writeText(original)
        assertThrows(Exception::class.java) { storage.write(pending) }
        assertEquals(original, file.readText())
        storage.archive(pending.entryId)
        assertNull(storage.read(pending.entryId))
        val archived = File(directory, "journal-workflow/101/recovery").listFiles()!!.single()
        assertEquals(original, archived.readText())
        storage.write(pending)
        assertEquals(pending, storage.read(pending.entryId))
    }

    private fun clarificationJson() = JSONObject().put("id", clarificationId.uppercase()).put("sourceVersion", 3)
        .put("revision", 2).put("status", "waiting").put("updatedAt", "updated")
        .put("question", JSONObject().put("id", questionId.uppercase()).put("prompt", "When did this happen?")
            .put("choices", org.json.JSONArray(listOf("Yesterday", "Today"))))

    private fun analysisJson() = JSONObject().put("id", analysisId.uppercase()).put("sourceVersion", 3)
        .put("status", "ready").put("replyRevision", 5).put("updatedAt", "updated")
        .put("correctedDocument", JSONObject().put("text", "昨日は晴れでした。").put("reading", "昨日[きのう]は晴[は]れでした。"))
        .put("result", JSONObject().put("title", "晴れた日").put("correctedText", "昨日は晴れでした。")
            .put("correctedReading", "昨日[きのう]は晴[は]れでした。")
            .put("segments", org.json.JSONArray().put(JSONObject().put("text", "晴れ").put("reading", "晴[は]れ")
                .put("kind", "correction").put("suggestion", "晴れでした").put("explanation", "Use the past tense."))))

    private fun isolated(test: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "workflow-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        try { test(directory) } finally { directory.deleteRecursively() }
    }
}

private class FeedbackTransport(var reply: () -> String) : ApiTransport {
    var path = ""
    var method = ""
    private var body: String? = null
    fun json() = JSONObject(requireNotNull(body))
    override suspend fun request(method: String, path: String, token: String?, body: String?): String {
        this.method = method
        this.path = path
        this.body = body
        return reply()
    }
}
