package com.cdcg.convolab.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.ui.JournalStyle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.cdcg.convolab.ui.JournalBrand
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

val LocalAuthInput = compositionLocalOf { false }

@Composable
fun AuthFields(page: AuthPage, form: AuthForm, enabled: Boolean, reset: (() -> Unit)? = null) {
    if (page == AuthPage.Register) AuthField(AuthInput("Name"), form.name, { form.name = it }, enabled)
    AuthField(AuthInput("Email", KeyboardType.Email), form.email, { form.email = it }, enabled)
    if (page == AuthPage.Register) AuthField(AuthInput("Invite code"), form.inviteCode, { form.inviteCode = it }, enabled)
    if (page != AuthPage.Reset) AuthPasswordField("Password", form.passwordState, enabled, reset)
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
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Text(input.label, style = fieldLabelStyle())
    OutlinedTextField(value = value, onValueChange = onEdit, enabled = enabled,
        shape = RoundedCornerShape(if (LocalAuthInput.current) 3.dp else 9.dp), colors = paperFieldColors(), textStyle = fieldTextStyle(),
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("auth-${input.label}").semantics { contentDescription = input.label },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = input.keyboard))
    }
}

data class AuthInput(val label: String, val keyboard: KeyboardType = KeyboardType.Text)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthPasswordField(label: String, state: TextFieldState, enabled: Boolean, reset: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = fieldLabelStyle())
        reset?.let { TextButton(onClick = it) { Text("Forgot password?", fontFamily = JournalBrand.Body, fontSize = 11.sp, color = JournalBrand.Blue) } }
    }
    OutlinedSecureTextField(state = state, enabled = enabled, shape = RoundedCornerShape(if (LocalAuthInput.current) 3.dp else 9.dp),
        colors = paperFieldColors(), textStyle = fieldTextStyle(),
        modifier = Modifier.fillMaxWidth().testTag("auth-$label").semantics { contentDescription = label })
    }
}

@Composable
private fun paperFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.White.copy(alpha = .7f), focusedContainerColor = Color.White,
    unfocusedBorderColor = Color(0xFFD1D1D1), focusedBorderColor = JournalStyle.Green,
)

@Composable
private fun fieldLabelStyle() = if (LocalAuthInput.current) TextStyle(fontFamily = JournalBrand.Body, fontSize = 12.sp) else JournalStyle.Body

@Composable
private fun fieldTextStyle() = if (LocalAuthInput.current) TextStyle(fontFamily = JournalBrand.Body, fontSize = 16.sp) else JournalStyle.Body
