package com.cdcg.convolab.journal.input

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdcg.convolab.ui.JournalBrand

data class WritingOptions(val label: String, val enabled: Boolean = true, val style: TextStyle = TextStyle.Default, val placeholder: String = "")

@Composable
fun JournalWritingField(text: String, changed: (String) -> Unit, options: WritingOptions, modifier: Modifier = Modifier) {
    val session = LocalJournalInput.current
    val mode = session?.preference?.state?.collectAsStateWithLifecycle()?.value ?: JournalKeyboard.Device
    val editor = rememberJournalEditor(text, changed, session, mode)
    var choosing by remember { mutableStateOf(false) }
    if (mode == JournalKeyboard.Choose) {
        OutlinedButton(onClick = { choosing = true }, enabled = options.enabled) { Text("Choose keyboard") }
    } else InputEditorField(editor, options, modifier, mode)
    if (choosing && session != null) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text("What keyboard do you use?") }, text = { KeyboardChoices(session.preference, onboarding = true) { choosing = false } },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Close") } })
}

@Composable
private fun rememberJournalEditor(text: String, changed: (String) -> Unit, session: JournalInputSession?, mode: JournalKeyboard): JournalInputEditor {
    val host = LocalInputHost.current
    val onChange by rememberUpdatedState(changed)
    val editor = remember(session) { JournalInputEditor(text, session) { onChange(it) } }
    LaunchedEffect(text) { editor.external(text) }
    LaunchedEffect(mode) {
        editor.finish()
        editor.builtIn = mode == JournalKeyboard.BuiltIn
        if (editor.builtIn) session?.retry()
    }
    LaunchedEffect(editor) { snapshotFlow { editor.field.selection }.collect(editor::selectionChanged) }
    DisposableEffect(editor, host) { onDispose { host?.release(editor) } }
    return editor
}

@Composable
private fun InputEditorField(editor: JournalInputEditor, options: WritingOptions, modifier: Modifier, mode: JournalKeyboard) {
    val host = LocalInputHost.current
    BasicTextField(state = editor.field, inputTransformation = { editor.transform(this) }, enabled = options.enabled,
        outputTransformation = {
            editor.snapshot.composition?.let { addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it.first, it.last.coerceAtMost(length)) }
            editor.snapshot.section?.let { addStyle(SpanStyle(background = JournalBrand.Blue.copy(alpha = 0.12f)), it.first, it.last.coerceAtMost(length)) }
        },
        textStyle = options.style, cursorBrush = SolidColor(JournalBrand.Blue),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = if (mode == JournalKeyboard.BuiltIn) KeyboardType.Ascii else KeyboardType.Text),
        modifier = modifier.onFocusChanged { if (it.isFocused) host?.focus(editor) }
            .semantics { contentDescription = options.label },
        decorator = { inner ->
            Column {
                editor.validationMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                Box {
                if (editor.snapshot.text.isEmpty()) Text(options.placeholder, style = options.style, color = JournalBrand.Ink.copy(alpha = 0.4f))
                inner()
                }
            }
        })
}
