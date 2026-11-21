package com.cdcg.convolab.journal.input

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdcg.convolab.ui.JournalBrand

typealias JournalInputActions = @Composable () -> Unit

@Composable
fun JournalInputFooter(compact: Boolean, actionWidth: Dp = 260.dp, actions: JournalInputActions) {
    val composing = LocalInputHost.current?.editor?.snapshot?.composition != null
    if (compact && composing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { JournalInputDock(compact = true) }
        Box(Modifier.width(actionWidth)) { actions() }
    } else Column { actions(); JournalInputDock(compact) }
}

@Composable
fun JournalInputDock(compact: Boolean = false) {
    val session = LocalJournalInput.current ?: return
    val editor = LocalInputHost.current?.editor ?: return
    val mode by session.preference.state.collectAsStateWithLifecycle()
    if (mode != JournalKeyboard.BuiltIn || editor.snapshot.composition == null) return
    Column(Modifier.fillMaxWidth().background(JournalBrand.Paper).testTag("journal-candidates")) {
        HorizontalDivider()
        if (compact) CompactCandidates(editor) else { CandidateChoices(editor); CandidateActions(editor) }
        if (session.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
private fun CompactCandidates(editor: JournalInputEditor) {
    var tools by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        if (tools) CandidateActions(editor, Modifier.weight(1f)) else CandidateChoices(editor, Modifier.weight(1f))
        TextButton(onClick = { tools = !tools }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = if (tools) "Show Japanese candidates" else "Show Japanese input tools" }) {
            Text(if (tools) "あ" else "⋯")
        }
    }
}

@Composable
private fun CandidateChoices(editor: JournalInputEditor, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val selection = editor.snapshot.highlighted
    LaunchedEffect(selection, editor.snapshot.section, editor.snapshot.query?.reading) {
        list.scrollToItem(selection.coerceAtLeast(0))
    }
    LazyRow(modifier.fillMaxWidth(), state = list) {
        itemsIndexed(editor.snapshot.choices, key = { _, text -> text }) { index, text ->
            TextButton(onClick = { editor.choose(index) }, modifier = Modifier.heightIn(min = 48.dp)
                .semantics { selected = selection == index }) { Text(text) }
        }
    }
}

@Composable
private fun CandidateActions(editor: JournalInputEditor, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        DockAction("Previous section", "〈") { editor.section(-1) }
        DockAction("Next section", "〉") { editor.section(1) }
        DockAction("Shorten section", "−") { editor.resize(-1) }
        DockAction("Lengthen section", "+") { editor.resize(1) }
        DockAction("More candidates", "More") { editor.more() }
        DockAction("Finish Japanese input", "Done") { editor.finish(); keyboard?.hide() }
    }
}

@Composable
private fun DockAction(label: String, text: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .semantics { contentDescription = label }) { Text(text) }
}
