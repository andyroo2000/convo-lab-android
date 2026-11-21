package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.JournalRequest
import com.cdcg.convolab.journal.JournalMethod
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.Analysis
import com.cdcg.convolab.journal.feedback.FeedbackJson
import org.json.JSONException

data class ConversationNode(val entry: JournalEntry, val analysis: Analysis?, val question: TutorQuestion?, val parentId: String?)
fun interface ConversationGateway { suspend fun load(root: JournalId): List<ConversationNode> }

class ConversationApi(private val api: JournalApi) : ConversationGateway {
    override suspend fun load(root: JournalId): List<ConversationNode> = try {
        val nodes = api.request(JournalRequest(JournalMethod.GET, JournalApi.path(root.value) + "/conversation")).getJSONArray("data")
        (0 until nodes.length()).map { index ->
            val node = nodes.getJSONObject(index)
            ConversationNode(JournalJson.entry(node.getJSONObject("entry")), node.optJSONObject("analysis")?.let(FeedbackJson::analysis),
                node.optJSONObject("question")?.let(TutorJson::question), node.nullableString("parentEntryId")?.let(JournalIds::normalize))
        }
    } catch (failure: JSONException) { throw JournalResponseFailure(failure) }
}
