package com.cdcg.convolab.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import com.cdcg.convolab.ui.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookContents(state: NotebookState, controller: NotebookController) {
    var editing by remember { mutableStateOf<JournalRecord?>(null) }
    var deleting by remember { mutableStateOf<JournalRecord?>(null) }
    ModalBottomSheet(onDismissRequest = { controller.showContents(false) }, containerColor = JournalBrand.Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides JournalStyle.Green) { JournalCloseButton("Close contents") { controller.showContents(false) } }
                Text("Contents", fontSize = 18.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { controller.newEntry() }, enabled = !state.busy,
                    modifier = Modifier.size(44.dp).background(JournalStyle.Sage, CircleShape).semantics { contentDescription = "New entry" }) { JournalIcon(JournalSymbol.Compose, color = JournalStyle.Green) }
            }
            state.error?.let { Text(it, color = Color(0xFF974634)) }
            Spacer(Modifier.height(18.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.records.isEmpty()) item { Text("Your entries will appear here.", modifier = Modifier.padding(vertical = 28.dp)) }
                items(state.records, key = { it.id }) { record ->
                    ContentsRow(record, state, { controller.select(record) }) { command ->
                        if (command == "Rename") editing = record else deleting = record
                    }
                }
                if (state.nextCursor != null) item {
                    TextButton(onClick = { controller.more() }, enabled = !state.busy) { Text("Load more") }
                }
                item { TextButton(onClick = { controller.refreshContents() }, enabled = !state.busy) { Text("Refresh") } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    editing?.let { RenameEntryDialog(it, { editing = null }, !state.busy) { title -> controller.rename(it, title); editing = null } }
    deleting?.let { DeleteEntryDialog(it, { deleting = null }, !state.busy) { controller.delete(it.editableDraft()); deleting = null } }
}

@Composable
private fun ContentsRow(record: JournalRecord, state: NotebookState, select: () -> Unit, command: (String) -> Unit) {
    val busy = state.busy
    val selected = record.id == state.draft.id
    var menu by remember { mutableStateOf(false) }
    Card(onClick = select, enabled = !busy, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFF1EFE6) else Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.displayTitle(), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color(0xFF222222))
                Text(displayDate(record.displayDate()), fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = Color(0xFF747469))
                if (record.pendingDelete) Text("Deletion pending", color = Color(0xFF974634))
                else if (record.draft != null) Text("Draft", color = Color(0xFF46764C))
            }
            if (selected) JournalIcon(JournalSymbol.Check, color = JournalStyle.Green)
            Box {
                TextButton(onClick = { menu = true }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Actions for ${record.displayTitle()}" }) { Text("•••", color = JournalStyle.Green) }
                PaperMenu(menu, { menu = false }) {
                    PaperMenuItem("Rename", JournalSymbol.Appearance) { menu = false; command("Rename") }
                    PaperMenuItem("Delete", JournalSymbol.Trash) { menu = false; command("Delete") }
                }
            }
        }
    }
}

private fun displayDate(value: String): String = try {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { value }
