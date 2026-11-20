package com.cdcg.convolab.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.input.TextFieldState

enum class AuthPage { SignIn, Register, Reset }

/** Password fields live only in memory and are never saved in activity instance state. */
class AuthForm {
    var email by mutableStateOf("")
    var name by mutableStateOf("")
    var inviteCode by mutableStateOf("")
    val passwordState = TextFieldState()
    val confirmationState = TextFieldState()
    val password: String get() = passwordState.text.toString()
    val confirmation: String get() = confirmationState.text.toString()

    fun valid(page: AuthPage): Boolean = when (page) {
        AuthPage.SignIn -> email.isNotBlank() && password.isNotEmpty()
        AuthPage.Reset -> email.isNotBlank()
        AuthPage.Register -> email.isNotBlank() && name.isNotBlank() && inviteCode.isNotBlank() &&
            password.codePointCount(0, password.length) >= 8 && password == confirmation
    }

    fun registration() = Registration(name.trim(), email.trim(), password, inviteCode.trim())

    fun clearSecrets() {
        passwordState.edit { replace(0, length, "") }
        confirmationState.edit { replace(0, length, "") }
        inviteCode = ""
    }
}
