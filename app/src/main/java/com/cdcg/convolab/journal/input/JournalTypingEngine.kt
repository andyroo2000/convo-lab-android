package com.cdcg.convolab.journal.input

interface JournalTypingEngine {
    suspend fun prepare()
    suspend fun candidates(query: CompositionQuery, expanded: Boolean = false): List<JapaneseCandidate>
    suspend fun learn(choice: ConfirmedInput)
    suspend fun reset()
    suspend fun close()
}
