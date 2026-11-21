package com.cdcg.convolab.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseDraftSheet(state: NotebookState, controller: NotebookController) {
    var confirmingDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = controller::dismissClose, containerColor = JournalBrand.Paper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Keep this draft?", fontFamily = JournalBrand.Serif, fontSize = 30.sp)
            Text("Your writing is saved on this device as you type. Keep the draft to save it to your account and return later.", fontFamily = JournalBrand.Serif, fontSize = 20.sp)
            state.error?.let { Text(it, color = Color(0xFF974634)) }
            Button(onClick = { controller.keepAndClose() }, enabled = !state.busy) { Text("Keep draft & close") }
            OutlinedButton(onClick = { confirmingDelete = true }, enabled = !state.busy) { Text("Delete draft") }
            TextButton(onClick = controller::dismissClose, enabled = !state.busy) { Text("Keep writing") }
        }
    }
    if (confirmingDelete) {
        val record = state.records.firstOrNull { it.id == state.draft.id } ?: JournalRecord(draft = state.draft)
        DeleteEntryDialog(record, { confirmingDelete = false }, !state.busy) {
            controller.delete(state.draft)
            confirmingDelete = false
        }
    }
}

@Composable
fun RenameEntryDialog(record: JournalRecord, dismiss: () -> Unit, enabled: Boolean, save: (String) -> Unit) {
    var title by remember(record.id) { mutableStateOf(record.displayTitle().takeUnless { it == "Untitled entry" }.orEmpty()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Rename entry") },
        text = { OutlinedTextField(value = title, onValueChange = { if (it.codePointCount(0, it.length) <= 255) title = it }, label = { Text("Title") }) },
        confirmButton = { TextButton(onClick = { save(title) }, enabled = enabled) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun DeleteEntryDialog(record: JournalRecord, dismiss: () -> Unit, enabled: Boolean, delete: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Delete entry?") },
        text = { Text("Delete “${record.displayTitle()}” and its conversation? This cannot be undone.") },
        confirmButton = { TextButton(onClick = delete, enabled = enabled) { Text("Delete") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
