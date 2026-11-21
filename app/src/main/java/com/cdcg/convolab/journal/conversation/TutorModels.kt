package com.cdcg.convolab.journal.conversation

import com.cdcg.convolab.journal.JournalId

data class Tutor(val id: String, val name: String, val gender: String, val avatar: String, val avatarUrl: String?)
data class TutorPreferences(val selectedId: String?, val tutors: List<Tutor>)
data class TutorQuestion(val id: String, val text: String, val reading: String, val translation: String,
    val tutor: Tutor?, val audioStatus: String?, val audioError: String?)
data class QuestionSource(val entryId: JournalId, val analysisId: JournalId, val replyId: JournalId? = null)

interface TutorGateway {
    suspend fun preferences(): TutorPreferences
    suspend fun choose(tutor: Tutor): TutorPreferences
    suspend fun prepare(source: QuestionSource): TutorQuestion
    suspend fun question(entryId: JournalId, questionId: JournalId): TutorQuestion
    suspend fun retryAudio(entryId: JournalId, questionId: JournalId): TutorQuestion
}
