package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalIds
import com.cdcg.convolab.journal.nullableString
import org.json.JSONArray
import org.json.JSONObject

object FeedbackJson {
    fun clarification(json: JSONObject) = Clarification(
        JournalIds.normalize(json.getString("id")), json.getInt("sourceVersion"), json.getInt("revision"),
        WorkStatus.parse(json.getString("status")), json.optJSONObject("question")?.let(::question),
        json.nullableString("error"), json.getString("updatedAt"), json.toString(),
    )

    fun analysis(json: JSONObject) = Analysis(
        JournalIds.normalize(json.getString("id")), json.getInt("sourceVersion"), WorkStatus.parse(json.getString("status")),
        json.optJSONObject("result")?.let(::feedback), json.optJSONObject("currentReply")?.optJSONObject("result")?.let(::feedback),
        json.optJSONObject("correctedDocument")?.let { CorrectedDocument(it.getString("text"), it.getString("reading")) },
        json.optInt("replyRevision", 0), json.nullableString("error"), json.getString("updatedAt"), json.toString(),
        json.optJSONObject("currentReply")?.nullableString("id")?.let(JournalIds::normalize),
    )

    private fun question(json: JSONObject) = ClarificationQuestion(JournalIds.normalize(json.getString("id")),
        json.getString("prompt"), json.getJSONArray("choices").strings())

    private fun feedback(json: JSONObject): Feedback {
        val segments = json.getJSONArray("segments")
        return Feedback(json.getString("title"), json.getString("correctedText"), json.getString("correctedReading"),
            json.optString("question", ""), json.optString("questionReading", ""), json.optString("questionTranslation", ""),
            (0 until segments.length()).map { segment(segments.getJSONObject(it)) },
            json.nullableString("entryText"), json.nullableString("entryReading"), json.nullableString("previousReading"))
    }

    private fun segment(json: JSONObject) = FeedbackSegment(json.getString("text"), json.getString("reading"),
        SegmentKind.entries.firstOrNull { it.name.equals(json.getString("kind"), ignoreCase = true) } ?: SegmentKind.Plain,
        json.nullableString("suggestion"), json.nullableString("suggestionReading"), json.nullableString("explanation"),
        json.nullableString("explanationReading"), json.nullableString("replacement"), json.nullableString("replacementReading"))
}

private fun JSONArray.strings() = (0 until length()).map(::getString)
