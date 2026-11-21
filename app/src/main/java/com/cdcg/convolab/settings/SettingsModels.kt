package com.cdcg.convolab.settings

import com.cdcg.convolab.auth.Account

data class KnownKanjiSnapshot(val version: Long = 0, val kanji: Set<Int> = emptySet(),
    val connected: Boolean = false, val lastSyncedAt: String? = null)

data class SettingsState(val account: Account, val known: KnownKanjiSnapshot = KnownKanjiSnapshot(),
    val busy: Boolean = false, val error: String? = null, val notice: String? = null)

// Credentials deliberately have no generated toString and never enter saved instance state.
data class ProfileChange(val name: String, val email: String)

class PasswordChange(val current: String, val password: String, val confirmation: String)

interface SettingsGateway {
    suspend fun profile(change: ProfileChange): Account
    suspend fun password(change: PasswordChange)
    suspend fun knownKanji(): KnownKanjiSnapshot
    suspend fun connect(token: String): KnownKanjiSnapshot
    suspend fun sync()
    suspend fun disconnect()
}

interface KnownKanjiStorage {
    fun read(): KnownKanjiSnapshot?
    fun write(snapshot: KnownKanjiSnapshot)
}
