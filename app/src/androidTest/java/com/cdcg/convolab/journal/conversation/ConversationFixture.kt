package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.*
import com.cdcg.convolab.network.ApiTransport
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/** Stateful wire fixture: a lost response still creates only one child on exact retry. */
internal class ConversationFixture : ApiTransport {
    val root = JournalEntry(JournalIds.newId(), "週末の日記", "今日は映画を見ました。", "2026-11-20", 1, "created", "updated")
    val children = linkedMapOf<String, JournalEntry>()
    val sent = mutableListOf<String>()
    var loseNextAnswer = false
    private val questions = mutableMapOf<String, JSONObject>()
    private val analyses = mutableMapOf<String, String>()
    private val parents = mutableMapOf<String, String>()
    private val origins = mutableMapOf<String, String>()

    override suspend fun request(method: String, path: String, token: String?, body: String?): String {
        if (path == "/api/journal/entries") return JSONObject().put("data", JSONArray().put(entryJson(root))).toString()
        val id = path.removePrefix("/api/journal/entries/").substringBefore('/')
        val entry = children[id] ?: root
        return when {
            path.endsWith("/conversation") && method == "POST" -> createAnswer(JournalId(id), JSONObject(requireNotNull(body)))
            path.endsWith("/conversation") -> JSONObject().put("data", JSONArray(children.values.map(::node))).toString()
            path.endsWith("/analysis") -> JSONObject().put("entry", entryJson(entry)).put("data", analysis(entry)).toString()
            path.endsWith("/clarification") -> data(JSONObject().put("id", JournalIds.newId()).put("sourceVersion", entry.version)
                .put("revision", 1).put("status", "resolved").put("updatedAt", "updated"))
            path.endsWith("/submit") -> data(analysis(entry))
            path.endsWith("/question") -> data(question(JournalId(entry.id)))
            method == "PUT" -> updateEntry(entry, JSONObject(requireNotNull(body)))
            else -> data(entryJson(entry))
        }
    }

    private fun createAnswer(parent: JournalId, body: JSONObject): String {
        sent += body.toString()
        val id = body.getString("id")
        val child = children.getOrPut(id) {
            JournalEntry(id, null, body.getString("body"), root.entryDate, 1, "created-$id", "updated", root.id)
        }
        check(child.body == body.getString("body"))
        parents[id] = parent.value
        origins[id] = body.getString("questionId")
        if (loseNextAnswer) { loseNextAnswer = false; throw IOException("fixture response lost") }
        return data(entryJson(child))
    }

    private fun updateEntry(entry: JournalEntry, body: JSONObject): String {
        val next = entry.copy(body = body.getString("body"), version = entry.version + 1)
        children[next.id] = next
        return data(entryJson(next))
    }
    private fun node(entry: JournalEntry) = JSONObject().put("entry", entryJson(entry)).put("analysis", analysis(entry))
        .put("question", question(JournalId(requireNotNull(parents[entry.id])))).put("parentEntryId", parents[entry.id])
    private fun question(id: JournalId) = questions.getOrPut(id.value) {
        JSONObject().put("id", JournalIds.newId()).put("text", "どんな映画を見ましたか。")
            .put("reading", "どんな映画[えいが]を見[み]ましたか。").put("translation", "What kind of film did you watch?")
            .put("tutor", JSONObject().put("id", "hana").put("name", "Hana").put("gender", "F").put("avatar", "hana-v1"))
    }
    private fun analysis(entry: JournalEntry) = JSONObject().put("id", analyses.getOrPut(entry.id) { JournalIds.newId() })
        .put("sourceVersion", entry.version).put("status", "ready").put("replyRevision", 0).put("updatedAt", "updated")
        .put("correctedDocument", JSONObject().put("text", entry.body).put("reading", entry.body))
        .put("result", JSONObject().put("title", "映画の話").put("correctedText", entry.body).put("correctedReading", entry.body)
            .put("segments", JSONArray().put(JSONObject().put("text", entry.body).put("reading", entry.body).put("kind", "plain"))))
    fun entryJson(entry: JournalEntry) = JSONObject().put("id", entry.id).put("title", entry.title ?: JSONObject.NULL)
        .put("body", entry.body).put("entryDate", entry.entryDate).put("version", entry.version)
        .put("createdAt", entry.createdAt).put("updatedAt", entry.updatedAt).put("rootEntryId", entry.rootEntryId ?: JSONObject.NULL)
    private fun data(json: JSONObject) = JSONObject().put("data", json).toString()
}
