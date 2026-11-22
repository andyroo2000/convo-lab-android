package com.cdcg.convolab.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.ui.LocalJournalButtonInk
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.ui.JournalButton
import com.cdcg.convolab.auth.AuthInput

@Composable
fun ProfileEditor(state: SettingsState, controller: SettingsController, done: () -> Unit) {
    var name by rememberSaveable(state.account.id) { mutableStateOf(state.account.name) }
    var email by rememberSaveable(state.account.id) { mutableStateOf(state.account.email) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SettingsField(AuthInput("Name"), name, { if (it.codePointCount(0, it.length) <= 255) name = it }, !state.busy)
        SettingsField(AuthInput("Email", KeyboardType.Email), email, { if (it.length <= 255) email = it }, !state.busy)
        SettingsSubmitButton("Save profile", onClick = { controller.updateProfile(name, email, done) }, enabled = !state.busy && name.isNotBlank() && email.isNotBlank(),
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp).testTag("settings-save-profile"))
    }
}

@Composable
fun PasswordEditor(state: SettingsState, controller: SettingsController, done: () -> Unit) {
    val form = remember { PasswordForm() }
    DisposableEffect(form) { onDispose { form.clear() } }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SettingsPasswordField("Current password", form.current, !state.busy)
        SettingsPasswordField("New password", form.password, !state.busy)
        SettingsPasswordField("Confirm password", form.confirmation, !state.busy)
        SettingsSubmitButton("Update password", onClick = { controller.updatePassword(form.change()) { form.clear(); done() } }, enabled = !state.busy && form.valid,
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp).testTag("settings-save-password"))
    }
}

@Composable
private fun SettingsSubmitButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    CompositionLocalProvider(LocalJournalButtonInk provides JournalBrand.Ink) {
        JournalButton(text, onClick, modifier, enabled)
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
