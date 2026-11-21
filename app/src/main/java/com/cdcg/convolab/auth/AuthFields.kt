package com.cdcg.convolab.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AuthFields(page: AuthPage, form: AuthForm, enabled: Boolean) {
    if (page == AuthPage.Register) AuthField(AuthInput("Name"), form.name, { form.name = it }, enabled)
    AuthField(AuthInput("Email", KeyboardType.Email), form.email, { form.email = it }, enabled)
    if (page == AuthPage.Register) AuthField(AuthInput("Invite code"), form.inviteCode, { form.inviteCode = it }, enabled)
    if (page != AuthPage.Reset) AuthPasswordField("Password", form.passwordState, enabled)
    if (page == AuthPage.Register) {
        AuthPasswordField("Confirm password", form.confirmationState, enabled)
        RegistrationHints(form)
    }
}

@Composable
private fun RegistrationHints(form: AuthForm) {
    Column {
        if (form.password.isNotEmpty() && form.password.codePointCount(0, form.password.length) < 8) {
            Text("Use at least 8 characters for your password.")
        }
        if (form.confirmation.isNotEmpty() && form.password != form.confirmation) Text("Passwords do not match.")
    }
}

@Composable
fun AuthField(input: AuthInput, value: String, onEdit: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value = value, onValueChange = onEdit, label = { Text(input.label) }, enabled = enabled,
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("auth-${input.label}"),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = input.keyboard))
}

data class AuthInput(val label: String, val keyboard: KeyboardType = KeyboardType.Text)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthPasswordField(label: String, state: TextFieldState, enabled: Boolean) {
    OutlinedSecureTextField(state = state, enabled = enabled, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag("auth-$label"))
}
