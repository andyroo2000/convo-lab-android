package com.cdcg.convolab.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.ui.JournalButton
import com.cdcg.convolab.auth.AuthField
import com.cdcg.convolab.auth.AuthInput
import com.cdcg.convolab.auth.AuthPasswordField

@Composable
fun ProfileEditor(state: SettingsState, controller: SettingsController, done: () -> Unit) {
    var name by rememberSaveable(state.account.id) { mutableStateOf(state.account.name) }
    var email by rememberSaveable(state.account.id) { mutableStateOf(state.account.email) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AuthField(AuthInput("Name"), name, { if (it.codePointCount(0, it.length) <= 255) name = it }, !state.busy)
        AuthField(AuthInput("Email", KeyboardType.Email), email, { if (it.length <= 255) email = it }, !state.busy)
        JournalButton("Save profile", onClick = { controller.updateProfile(name, email, done) }, enabled = !state.busy && name.isNotBlank() && email.isNotBlank(),
            modifier = Modifier.padding(top = 12.dp).testTag("settings-save-profile"))
    }
}

@Composable
fun PasswordEditor(state: SettingsState, controller: SettingsController, done: () -> Unit) {
    val form = remember { PasswordForm() }
    DisposableEffect(form) { onDispose { form.clear() } }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AuthPasswordField("Current password", form.current, !state.busy)
        AuthPasswordField("New password", form.password, !state.busy)
        AuthPasswordField("Confirm password", form.confirmation, !state.busy)
        Text("Use at least 8 characters. Your new passwords must match.")
        JournalButton("Update password", onClick = { controller.updatePassword(form.change()) { form.clear(); done() } }, enabled = !state.busy && form.valid,
            modifier = Modifier.padding(top = 12.dp).testTag("settings-save-password"))
    }
}

private class PasswordForm {
    val current = TextFieldState()
    val password = TextFieldState()
    val confirmation = TextFieldState()
    val valid: Boolean get() = current.text.isNotEmpty() && password.text.toString().codePointCount(0, password.text.length) >= 8 && password.text.toString() == confirmation.text.toString()
    fun change() = PasswordChange(current.text.toString(), password.text.toString(), confirmation.text.toString())
    fun clear() { listOf(current, password, confirmation).forEach { it.edit { replace(0, length, "") } } }
}
