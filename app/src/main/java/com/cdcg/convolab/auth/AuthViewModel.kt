package com.cdcg.convolab.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cdcg.convolab.network.HttpApiTransport
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    val sessions = SessionController(AuthApi(HttpApiTransport()), KeystoreSessionStorage(application))

    init { viewModelScope.launch { sessions.restore() } }

    fun submit(page: AuthPage, form: AuthForm) {
        if (sessions.state.value.busy || !form.valid(page)) return
        val email = form.email
        val password = form.password
        val registration = form.registration()
        viewModelScope.launch {
            when (page) {
                AuthPage.SignIn -> sessions.login(email, password)
                AuthPage.Register -> sessions.register(registration)
                AuthPage.Reset -> sessions.resetPassword(email)
            }
            form.clearSecrets()
        }
    }

    fun clearMessage() { viewModelScope.launch { sessions.clearMessage() } }
    fun signOut() { viewModelScope.launch { sessions.signOut() } }
}
