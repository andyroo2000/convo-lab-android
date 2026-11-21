package com.cdcg.convolab.journal

/** A validated canonical identifier at the persistence boundary. */
@JvmInline
value class JournalId(val value: String) {
    init { require(JournalIds.normalize(value) == value) { "Expected a canonical Journal identifier" } }
}

val JournalDraft.key: JournalId get() = JournalId(id)
val JournalRecord.key: JournalId get() = JournalId(id)
