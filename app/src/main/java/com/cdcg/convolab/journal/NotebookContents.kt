package com.cdcg.convolab.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookContents(state: NotebookState, controller: NotebookController) {
    var editing by remember { mutableStateOf<JournalRecord?>(null) }
    var deleting by remember { mutableStateOf<JournalRecord?>(null) }
    ModalBottomSheet(onDismissRequest = { controller.showContents(false) }, containerColor = JournalBrand.Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Contents", fontFamily = JournalBrand.Heading, fontSize = 26.sp)
                TextButton(onClick = { controller.newEntry() }, enabled = !state.busy) { Text("New entry") }
            }
            state.error?.let { Text(it, color = Color(0xFF974634)) }
            TextButton(onClick = { controller.refreshContents() }, enabled = !state.busy) { Text("Refresh") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.records.isEmpty()) item { Text("Your entries will appear here.", modifier = Modifier.padding(vertical = 28.dp)) }
                items(state.records, key = { it.id }) { record ->
                    ContentsRow(record, state.busy, { controller.select(record) }) { command ->
                        if (command == "Rename") editing = record else deleting = record
                    }
                }
                if (state.nextCursor != null) item {
                    TextButton(onClick = { controller.more() }, enabled = !state.busy) { Text("Load more") }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    editing?.let { RenameEntryDialog(it, { editing = null }) { title -> controller.rename(it, title); editing = null } }
    deleting?.let { DeleteEntryDialog(it, { deleting = null }) { controller.delete(it.editableDraft()); deleting = null } }
}

@Composable
private fun ContentsRow(record: JournalRecord, busy: Boolean, select: () -> Unit, command: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = select, enabled = !busy, colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EFE6))) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(record.displayTitle(), fontFamily = JournalBrand.Heading, fontSize = 20.sp)
                Text(displayDate(record.displayDate()), color = Color(0xFF747469))
                if (record.pendingDelete) Text("Deletion pending", color = Color(0xFF974634))
                else if (record.draft != null) Text("Draft", color = Color(0xFF46764C))
            }
            Box {
                TextButton(onClick = { menu = true }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Actions for ${record.displayTitle()}" }) { Text("•••") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (action in listOf("Rename", "Delete")) DropdownMenuItem(text = { Text(action) }, onClick = { menu = false; command(action) })
                }
            }
        }
    }
}

private fun displayDate(value: String): String = try {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { value }
