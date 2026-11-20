package com.cdcg.convolab.auth

data class Account(val id: Long, val name: String, val email: String)

// Deliberately not a data class: generated toString must never print a bearer token.
class Session(val token: String, val account: Account)

class Registration(val name: String, val email: String, val password: String, val inviteCode: String)

data class AuthState(
    val restoring: Boolean = true,
    val account: Account? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val resetSent: Boolean = false,
)

interface AuthGateway {
    suspend fun login(email: String, password: String): Session
    suspend fun register(registration: Registration): Session
    suspend fun verify(token: String): Account
    suspend fun resetPassword(email: String)
    suspend fun revoke(token: String)
}

interface SessionStorage {
    fun read(): Session?
    fun write(session: Session)
    fun clear()
}
