package com.cdcg.convolab.typing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PlaygroundHeader(state: TypingState, progress: String?, onBenchmark: () -> Unit, onCredits: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (state.ready) "Japanese typing lab" else "Loading typing engine…",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        TextButton(onClick = onCredits) { Text("Credits") }
        TextButton(onClick = onBenchmark, enabled = state.ready && progress == null) { Text("Benchmark") }
    }
}

@Composable
fun CandidateStrip(state: TypingState, onCommit: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(52.dp).horizontalScroll(rememberScrollState()).testTag("candidates")) {
        val conversion = state.conversion
        if (conversion != null) {
            TextButton(onClick = { onCommit(conversion.reading) }) { Text("かな") }
            conversion.candidates.forEachIndexed { index, candidate ->
                TextButton(onClick = { onCommit(candidate) }, modifier = Modifier.testTag("candidate-$index")) {
                    Text(candidate, maxLines = 1)
                }
            }
        } else {
            Text(if (state.busy) "Converting…" else "Candidates appear here",
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RomanInput(state: TypingState, enabled: Boolean, onEdit: (String) -> Unit, onCommit: () -> Unit) {
    OutlinedTextField(
        value = state.input, onValueChange = onEdit, enabled = enabled, singleLine = true,
        label = { Text("Romaji") }, placeholder = { Text("nihongowobenkyoushiteimasu") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        modifier = Modifier.fillMaxWidth().testTag("romaji"),
    )
}
