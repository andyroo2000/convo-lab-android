package com.cdcg.convolab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcg.convolab.auth.AuthScreen
import com.cdcg.convolab.auth.AuthViewModel

@Composable
fun JournalApp(openTypingLab: (() -> Unit)?, model: AuthViewModel = viewModel()) {
    val state by model.sessions.state.collectAsStateWithLifecycle()
    when {
        state.restoring -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.account == null -> AuthScreen(state, model::submit, model::clearMessage)
        else -> JournalShell(state.account!!.name, state.error, model::signOut, openTypingLab)
    }
}

@Composable
private fun JournalShell(name: String, error: String?, signOut: () -> Unit, openTypingLab: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().background(JournalBrand.Paper).safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().background(JournalBrand.Green).padding(20.dp)) {
            Text("CONVO-LAB", fontFamily = JournalBrand.Anton, fontSize = 30.sp, color = JournalBrand.Yellow)
            Text("Journal", color = JournalBrand.Paper, fontSize = 22.sp)
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Signed in as $name", fontFamily = JournalBrand.Serif, fontSize = 24.sp)
            error?.let { Text(it) }
            TextButton(onClick = signOut) { Text("Sign out") }
            openTypingLab?.let { TextButton(onClick = it) { Text("Japanese typing lab") } }
        }
    }
}
