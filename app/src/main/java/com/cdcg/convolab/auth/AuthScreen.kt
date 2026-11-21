package com.cdcg.convolab.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.runtime.LaunchedEffect
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
    val scroll = rememberScrollState()
    LaunchedEffect(page, state.resetSent) { scroll.scrollTo(0) }
    val navigate: (AuthPage) -> Unit = { page = it; form.clearSecrets(); clearMessage() }
    BackHandler(page != AuthPage.SignIn && !state.busy) { navigate(AuthPage.SignIn) }
    CompositionLocalProvider(LocalAuthInput provides true) {
    ProvideTextStyle(TextStyle(fontFamily = JournalBrand.Body, fontSize = 14.sp)) {
    Box(Modifier.fillMaxSize().background(JournalBrand.Green)) {
    Box(Modifier.fillMaxSize().padding(8.dp).border(2.dp, Color(0xFFEFF3C7).copy(alpha = .55f), RoundedCornerShape(topStart = 26.dp, topEnd = 3.dp, bottomStart = 3.dp, bottomEnd = 26.dp)))
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        .verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (page != AuthPage.SignIn) TextButton(onClick = { navigate(AuthPage.SignIn) }, modifier = Modifier.align(Alignment.Start)) { Text("Back to sign in", color = Color.White) }
        JournalCover(Modifier.widthIn(max = 540.dp).fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        AuthPanel(state, page, form, AuthActions(submit, navigate))
    }
    }
    }
    }
}

private class AuthActions(val submit: (AuthPage, AuthForm) -> Unit, val navigate: (AuthPage) -> Unit)

@Composable
private fun AuthPanel(state: AuthState, page: AuthPage, form: AuthForm, actions: AuthActions) {
        Card(Modifier.widthIn(max = 540.dp).fillMaxWidth(), shape = RoundedCornerShape(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text(page.title(), fontFamily = JournalBrand.Heading, fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp, color = Color(0xFF242424))
                if (page == AuthPage.SignIn) AuthNavigation(page, !state.busy, actions.navigate)
                if (page == AuthPage.Reset && !state.resetSent) Text("Enter your email address and we’ll send you a link to reset your password.")
                if (state.resetSent && page == AuthPage.Reset) {
                    Text("If an account exists for that email, a reset link is on its way.",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    AuthFields(page, form, !state.busy, if (page == AuthPage.SignIn) { { actions.navigate(AuthPage.Reset) } } else null)
                    AuthError(state.error, page)
                    Button(onClick = { actions.submit(page, form) }, enabled = !state.busy && form.valid(page),
                        colors = ButtonDefaults.buttonColors(containerColor = JournalBrand.Blue, disabledContainerColor = JournalBrand.Blue.copy(alpha = .5f), disabledContentColor = Color.White),
                        modifier = Modifier.align(Alignment.End).testTag("auth-submit")) { Text(page.action(), fontFamily = JournalBrand.Body, fontSize = 13.sp) }
                }
                if (state.busy) CircularProgressIndicator()
            }
        }
}

@Composable
private fun AuthNavigation(page: AuthPage, enabled: Boolean, navigate: (AuthPage) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("New user?", fontFamily = JournalBrand.Body, fontSize = 12.sp)
        TextButton(onClick = { navigate(AuthPage.Register) }, enabled = enabled) {
            Text("Create an account", fontFamily = JournalBrand.Body, fontSize = 12.sp, color = JournalBrand.Blue)
        }
    }
}

private fun AuthPage.title() = when (this) {
    AuthPage.SignIn -> "Sign in"
    AuthPage.Register -> "Create an account"
    AuthPage.Reset -> "Reset password"
}

private fun AuthPage.action() = if (this == AuthPage.Reset) "Send reset link" else title()

@Composable
private fun AuthError(error: String?, page: AuthPage) {
    if (error == null) return
    Text(error, color = Color(0xFFB34332), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (page == AuthPage.Register) {
        Text("If you already submitted this form, try signing in with the same email and password.")
    }
}
