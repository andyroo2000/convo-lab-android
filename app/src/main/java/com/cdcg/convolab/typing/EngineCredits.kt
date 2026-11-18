package com.cdcg.convolab.typing

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EngineCredits(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var credits by remember { mutableStateOf("Loading credits…") }
    LaunchedEffect(Unit) {
        credits = withContext(Dispatchers.IO) {
            context.assets.open("typing/Attributions.txt").bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Engine credits") },
        text = { Text(credits, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
