package com.cdcg.convolab.journal.feedback

import com.cdcg.convolab.journal.JournalEntry

enum class WorkStatus { Queued, Processing, Waiting, Resolved, Ready, Failed;
    val pending: Boolean get() = this == Queued || this == Processing
    companion object {
        fun parse(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown Journal status")
    }
}

data class ClarificationQuestion(val id: String, val prompt: String, val choices: List<String>)
data class Clarification(
    val id: String, val sourceVersion: Int, val revision: Int, val status: WorkStatus,
    val question: ClarificationQuestion?, val error: String?, val updatedAt: String, val encoded: String,
)
data class ClarificationAnswer(val clarificationId: String, val questionId: String, val text: String)

enum class SegmentKind { Plain, Correction, Optional, Keep }
data class FeedbackSegment(
    val text: String, val reading: String, val kind: SegmentKind, val suggestion: String?,
    val suggestionReading: String?, val explanation: String?, val explanationReading: String?,
    val replacement: String?, val replacementReading: String?,
)
data class Feedback(
    val title: String, val correctedText: String, val correctedReading: String,
    val question: String, val questionReading: String, val questionTranslation: String,
    val segments: List<FeedbackSegment>, val entryText: String?, val entryReading: String?, val previousReading: String?,
)
data class CorrectedDocument(val text: String, val reading: String)
data class Analysis(
    val id: String, val sourceVersion: Int, val status: WorkStatus, val result: Feedback?,
    val replyResult: Feedback?, val correctedDocument: CorrectedDocument?, val replyRevision: Int,
    val error: String?, val updatedAt: String, val encoded: String,
) {
    val feedback: Feedback? get() = replyResult ?: result
    val finalText: String? get() = correctedDocument?.text ?: replyResult?.entryText ?: result?.correctedText
    val finalReading: String? get() = correctedDocument?.reading ?: replyResult?.entryReading ?: result?.correctedReading
}
data class AnalysisResponse(val entry: JournalEntry, val analysis: Analysis?)
data class FinalEdit(val analysisId: String, val expectedRevision: Int, val text: String)

interface FeedbackGateway {
    suspend fun startClarification(entry: JournalEntry): Clarification
    suspend fun clarification(entry: JournalEntry): Clarification?
    suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification
    suspend fun submit(entry: JournalEntry, clarificationId: String): Analysis
    suspend fun analysis(entry: JournalEntry): AnalysisResponse
    suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis
}
