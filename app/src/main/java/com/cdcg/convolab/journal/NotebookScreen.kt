package com.cdcg.convolab.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand

@Composable
fun NotebookScreen(state: NotebookState, controller: NotebookController, menu: @Composable () -> Unit) {
    BackHandler(!state.busy && !state.contents && !state.closePrompt) { controller.requestClose() }
    Column(Modifier.fillMaxSize().background(JournalBrand.Green).safeDrawingPadding().imePadding()) {
        NotebookHeader({ controller.showContents(true) }, menu)
        Column(Modifier.weight(1f).background(Color(0xFFFAF9F6))) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.draft.title?.let { Text(it, fontFamily = JournalBrand.Serif, fontSize = 24.sp) }
                key(state.draft.id) { NotebookPaper(state, controller::edit) }
                NotebookMessage(state, controller)
            }
            NotebookActions(state, controller)
        }
    }
    if (state.contents) NotebookContents(state, controller)
    if (state.closePrompt) CloseDraftSheet(state, controller)
}

@Composable
private fun NotebookHeader(contents: () -> Unit, menu: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("CONVO-LAB", fontFamily = JournalBrand.Anton, fontSize = 26.sp, color = JournalBrand.Yellow)
            Text("Journal", fontFamily = JournalBrand.Serif, fontSize = 17.sp, color = Color.White)
        }
        TextButton(onClick = contents) { Text("Contents", fontFamily = JournalBrand.Serif, fontSize = 20.sp, color = Color.White) }
        menu()
    }
}

@Composable
private fun NotebookActions(state: NotebookState, controller: NotebookController) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAF9F6)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = controller::requestClose, enabled = !state.busy) { Text("Close", fontFamily = JournalBrand.Serif, fontSize = 22.sp) }
        Button(onClick = { controller.save() }, enabled = !state.busy && !state.pendingDelete && state.draft.body.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF46764C)), modifier = Modifier.testTag("journal-save")) {
            Text(if (state.busy) "Saving…" else "Save draft", fontFamily = JournalBrand.Serif, fontSize = 22.sp)
        }
    }
}

@Composable
private fun NotebookMessage(state: NotebookState, controller: NotebookController) {
    if (state.pendingDelete) {
        Text("Deletion is awaiting confirmation. Your local copy is kept until it succeeds.")
        TextButton(onClick = { controller.delete(state.draft) }, enabled = !state.busy) { Text("Retry deletion") }
    }
    if (state.savedLocally) Text("Saved on this device", color = Color(0xFF586556), fontSize = 12.sp)
    if (state.recoveryCount > 0) Text("${state.recoveryCount} saved records need recovery. Their files have been kept.", color = Color(0xFF974634))
    state.error?.let {
        Text(it, color = Color(0xFF974634), fontWeight = FontWeight.Medium)
        Row {
            TextButton(onClick = { controller.flush() }, enabled = !state.busy) { Text("Retry local save") }
            if (state.canRecoverCopy) TextButton(onClick = { controller.recoverCopy() }) { Text("Save as new") }
        }
    }
}
