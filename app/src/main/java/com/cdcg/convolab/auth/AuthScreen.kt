package com.cdcg.convolab.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.ui.JournalCover

@Composable
fun AuthScreen(state: AuthState, submit: (AuthPage, AuthForm) -> Unit, clearMessage: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(AuthPage.SignIn) }
    val form = remember { AuthForm() }
    val navigate: (AuthPage) -> Unit = { page = it; form.clearSecrets(); clearMessage() }
    BackHandler(page != AuthPage.SignIn && !state.busy) { navigate(AuthPage.SignIn) }
    Column(Modifier.fillMaxSize().background(JournalBrand.Green).safeDrawingPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        JournalCover(Modifier.widthIn(max = 540.dp).fillMaxWidth())
        Card(Modifier.widthIn(max = 540.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(page.title(), fontFamily = JournalBrand.Heading, fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp, color = Color(0xFF242424))
                if (state.resetSent && page == AuthPage.Reset) {
                    Text("If an account exists for that email, a reset link is on its way.",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    AuthFields(page, form, !state.busy)
                    state.error?.let { Text(it, color = Color(0xFFB34332), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    Button(onClick = { submit(page, form) }, enabled = !state.busy && form.valid(page),
                        colors = ButtonDefaults.buttonColors(containerColor = JournalBrand.Blue),
                        modifier = Modifier.align(Alignment.End).testTag("auth-submit")) { Text(page.action()) }
                }
                if (state.busy) CircularProgressIndicator()
                AuthNavigation(page, !state.busy, navigate)
            }
        }
    }
}

@Composable
private fun AuthNavigation(page: AuthPage, enabled: Boolean, navigate: (AuthPage) -> Unit) {
    if (page == AuthPage.SignIn) {
        TextButton(onClick = { navigate(AuthPage.Register) }, enabled = enabled) { Text("Create an account") }
        TextButton(onClick = { navigate(AuthPage.Reset) }, enabled = enabled) { Text("Forgot password?") }
    } else {
        TextButton(onClick = { navigate(AuthPage.SignIn) }, enabled = enabled) { Text("Back to sign in") }
    }
}

private fun AuthPage.title() = when (this) {
    AuthPage.SignIn -> "Sign in"
    AuthPage.Register -> "Create an account"
    AuthPage.Reset -> "Reset password"
}

private fun AuthPage.action() = if (this == AuthPage.Reset) "Send reset link" else title()
