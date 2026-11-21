package com.cdcg.convolab.journal.input

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange

class JournalInputEditor(initial: String, private val session: JournalInputSession?, private val changed: (String) -> Unit) {
    val field = TextFieldState(initial, TextRange(initial.length))
    private val model = JournalComposition(initial)
    var snapshot by mutableStateOf(model.snapshot()); private set
    var validationMessage by mutableStateOf<String?>(null); private set
    private var lastQuery: CompositionQuery? = null
    var builtIn = false

    fun transform(buffer: TextFieldBuffer) {
        val proposed = buffer.asCharSequence().toString()
        if (!valid(proposed)) { buffer.revertAllChanges(); return }
        if (builtIn) {
            val value = model.reconcile(proposed, buffer.selection.input())
            if (proposed != value.text) buffer.replace(0, buffer.length, value.text)
            buffer.selection = value.selection.range()
            publish(value)
        } else {
            model.external(proposed)
            publish(model.moveSelection(buffer.selection.input()))
        }
    }
    fun selectionChanged(selection: TextRange) {
        if (selection == snapshot.selection.range()) return
        publish(model.moveSelection(selection.input()))
    }
    fun external(text: String) {
        if (text == snapshot.text) return
        render(model.external(text))
    }
    fun choose(index: Int) { render(model.choose(index)) }
    fun finish() { render(model.commit()) }
    fun section(direction: Int) { render(model.focusSection(direction)) }
    fun resize(direction: Int) { render(model.resize(direction)) }
    fun more() { request(expanded = true) }
    private fun render(value: InputSnapshot) {
        field.edit { replace(0, length, value.text); selection = value.selection.range() }
        publish(value)
    }
    private fun publish(value: InputSnapshot) {
        val textChanged = snapshot.text != value.text
        snapshot = value
        if (textChanged) changed(value.text)
        session?.confirm(model.takeConfirmed())
        if (value.query != lastQuery) { lastQuery = value.query; request() }
    }
    private fun request(expanded: Boolean = false) {
        val query = snapshot.query
        session?.query(query, expanded) { candidates ->
            if (query != null) snapshot = model.receive(query, candidates)
        }
    }
    private fun valid(value: String): Boolean {
        val count = value.codePointCount(0, value.length)
        val previous = snapshot.text.codePointCount(0, snapshot.text.length)
        validationMessage = when {
            value.contains('\u0000') -> "This character cannot be included in a journal entry."
            count > 20_000 -> "Journal entries can contain up to 20,000 characters."
            else -> null
        }
        return !value.contains('\u0000') && (count <= 20_000 || count <= previous)
    }
    private fun TextRange.input() = InputSelection(start, end)
    private fun InputSelection.range() = TextRange(start, end)
}

class JournalInputHost {
    var editor by mutableStateOf<JournalInputEditor?>(null); private set
    fun focus(value: JournalInputEditor) {
        if (editor === value) return
        editor?.finish()
        editor = value
    }
    fun release(value: JournalInputEditor) { if (editor === value) { value.finish(); editor = null } }
    fun finish() { editor?.finish() }
}
val LocalInputHost = compositionLocalOf<JournalInputHost?> { null }
