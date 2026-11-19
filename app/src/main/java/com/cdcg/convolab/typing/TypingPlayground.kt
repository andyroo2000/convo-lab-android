package com.cdcg.convolab.typing

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TypingPlayground(model: TypingViewModel = viewModel()) {
    val state by model.session.state.collectAsStateWithLifecycle()
    val progress by model.progress.collectAsStateWithLifecycle()
    val report by model.report.collectAsStateWithLifecycle()
    val notebook by model.notebook.collectAsStateWithLifecycle()
    var showCredits by rememberSaveable { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 12.dp)) {
            val compact = maxHeight < 280.dp
            Column(Modifier.fillMaxSize()) {
                if (!compact) {
                    PlaygroundToolbar(state, progress, model) { action ->
                        showReport = action == PlaygroundAction.Benchmark
                        showCredits = action == PlaygroundAction.Credits
                    }
                    OutlinedTextField(
                        value = notebook, onValueChange = { model.notebook.value = it },
                        label = { Text("Scratchpad · not saved") },
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("notebook"),
                    )
                }
                val commit: (String) -> Unit = { candidate ->
                    model.session.commit(candidate)?.let { model.notebook.value += it }
                }
                CandidateStrip(state, commit, Modifier.fillMaxWidth())
                RomanInput(state, enabled = state.ready && progress == null, onEdit = model.session::edit,
                    onCommit = { state.conversion?.candidates?.firstOrNull()?.let(commit) })
                if (!compact) {
                    Text(state.error ?: state.conversion?.reading ?: "Type romaji with an English keyboard.",
                        style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        modifier = Modifier.padding(vertical = 4.dp).testTag("reading"))
                }
            }
        }
    }
    if (showCredits) EngineCredits { showCredits = false }
    if (showReport) BenchmarkDialog(report, progress) { showReport = false }
}
