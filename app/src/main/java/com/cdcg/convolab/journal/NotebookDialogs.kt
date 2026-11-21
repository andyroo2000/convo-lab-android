package com.cdcg.convolab.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.ui.*
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

@Composable
fun CloseDraftSheet(state: NotebookState, controller: NotebookController) {
    var confirmingDelete by remember { mutableStateOf(false) }
    JournalSheet("Keep this draft?", controller::dismissClose) {
        Text("Your writing is saved automatically. You can come back and finish it later.")
        state.error?.let { Text(it, color = JournalStyle.Red) }
        JournalDraftButton("Keep draft & close", { controller.keepAndClose() }, style = DraftButtonStyle(enabled = !state.busy))
        JournalDraftButton("Delete draft", { confirmingDelete = true }, style = DraftButtonStyle(enabled = !state.busy, filled = false, destructive = true))
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
    JournalSheet("Rename entry", dismiss) {
        OutlinedTextField(value = title, onValueChange = { if (it.codePointCount(0, it.length) <= 255) title = it },
            label = { Text("Title") }, textStyle = LocalJournalTypography.current.style, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, unfocusedBorderColor = JournalStyle.Border), modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            JournalDraftButton("Cancel", dismiss, style = DraftButtonStyle(filled = false))
            JournalDraftButton("Save", { save(title) }, style = DraftButtonStyle(enabled = enabled))
        }
    }
}

@Composable
fun DeleteEntryDialog(record: JournalRecord, dismiss: () -> Unit, enabled: Boolean, delete: () -> Unit) {
    JournalSheet("Delete this entry?", dismiss) {
        Text("This entry will be permanently deleted. You can’t undo this action.")
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, JournalStyle.Border)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.displayTitle(), style = LocalJournalTypography.current.style)
                Text(record.displayDate())
                Text(record.editableDraft().body.ifEmpty { "Empty draft" }, style = LocalJournalTypography.current.style, maxLines = 3)
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            JournalDraftButton("Keep entry", dismiss, style = DraftButtonStyle(filled = false))
            JournalDraftButton("Delete entry", delete, style = DraftButtonStyle(enabled = enabled, destructive = true))
        }
    }
}
