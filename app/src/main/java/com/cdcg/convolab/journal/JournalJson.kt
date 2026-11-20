package com.cdcg.convolab.journal

import org.json.JSONObject

object JournalJson {
    fun entry(json: JSONObject) = JournalEntry(
        JournalIds.normalize(json.getString("id")), json.nullableString("title"), json.getString("body"),
        json.getString("entryDate"), json.getInt("version"), json.getString("createdAt"), json.getString("updatedAt"),
        json.nullableString("rootEntryId")?.let(JournalIds::normalize),
    )

    fun draft(json: JSONObject) = JournalDraft(
        JournalIds.normalize(json.getString("id")), json.nullableString("title"), json.getString("body"),
        json.getString("entryDate"), json.getInt("expectedVersion"), json.getString("editID"),
        json.nullableString("rootEntryId")?.let(JournalIds::normalize),
    )

    fun savePayload(draft: JournalDraft): JSONObject = JSONObject()
        .put("title", draft.title ?: JSONObject.NULL).put("body", draft.body)
        .put("entryDate", draft.entryDate).put("expectedVersion", draft.expectedVersion)

    fun encode(record: JournalRecord): String = JSONObject().put("schemaVersion", 1)
        .put("entry", record.entry?.let(::encodeEntry) ?: JSONObject.NULL)
        .put("draft", record.draft?.let(::encodeDraft) ?: JSONObject.NULL)
        .put("pendingSave", record.pendingSave?.let(::encodeDraft) ?: JSONObject.NULL)
        .put("pendingDelete", record.pendingDelete).put("acknowledgedVersion", record.acknowledgedVersion).toString()

    fun decode(text: String): JournalRecord {
        val json = JSONObject(text)
        require(json.getInt("schemaVersion") == 1) { "Unsupported Journal record version" }
        return JournalRecord(json.optJSONObject("entry")?.let(::entry), json.optJSONObject("draft")?.let(::draft),
            json.optJSONObject("pendingSave")?.let(::draft), json.optBoolean("pendingDelete", false), json.optInt("acknowledgedVersion", 0))
    }

    private fun encodeEntry(entry: JournalEntry): JSONObject = JSONObject()
        .put("id", entry.id).put("title", entry.title ?: JSONObject.NULL).put("body", entry.body)
        .put("entryDate", entry.entryDate).put("version", entry.version)
        .put("createdAt", entry.createdAt).put("updatedAt", entry.updatedAt)
        .put("rootEntryId", entry.rootEntryId ?: JSONObject.NULL)

    private fun encodeDraft(draft: JournalDraft): JSONObject = savePayload(draft)
        .put("id", draft.id).put("editID", draft.editId).put("rootEntryId", draft.rootEntryId ?: JSONObject.NULL)
}

fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
