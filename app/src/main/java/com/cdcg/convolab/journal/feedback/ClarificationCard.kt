package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.cdcg.convolab.ui.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

@Composable
fun ClarificationCard(presentation: FeedbackPresentation, clarification: Clarification) {
    val question = clarification.question ?: return
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var selected by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    var other by rememberSaveable(question.id) { mutableStateOf("") }
    val custom = "Something else"
    val answer = if (selected == custom) other else selected.orEmpty()
    val pending = presentation.state.snapshot?.pendingAnswer != null
    val enabled = !presentation.state.busy && !pending
    JournalCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question.prompt, style = JournalStyle.Body, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() })
            (question.choices + custom).distinct().forEach { choice ->
                ClarificationOption(choice, choice == selected, enabled) { selected = choice }
            }
            if (selected == custom) CustomAnswer(other, enabled) { other = it }
            JournalDraftButton("Answer", { focus.clearFocus(); keyboard?.hide(); presentation.controller.answer(answer) }, style = DraftButtonStyle(enabled = enabled && answer.isNotBlank()),
                modifier = Modifier.testTag("clarification-send"))
            TextButton(onClick = presentation.controller::cancel, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text("Back to draft", color = JournalStyle.Green, textDecoration = TextDecoration.Underline)
            }
            if (pending) Text("Your answer is saved. Retry to confirm it was received.")
        }
    }
}

@Composable
private fun CustomAnswer(value: String, enabled: Boolean, change: (String) -> Unit) {
    var invalid by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
        BasicTextField(value = value, onValueChange = {
            invalid = it.codePointCount(0, it.length) > 2_000 || it.contains('\u0000')
            if (!invalid) change(it)
        }, enabled = enabled, minLines = 3, maxLines = 6, textStyle = JournalStyle.Body,
            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, JournalStyle.Border, RoundedCornerShape(12.dp)).padding(12.dp)
                .focusRequester(focus).testTag("clarification-answer").semantics { contentDescription = "Your answer" },
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text("Your answer", color = JournalStyle.Ink.copy(alpha = .5f))
                    field()
                }
            })
        if (invalid) Text("Use up to 2,000 characters without null characters.", color = JournalStyle.Red)
    }
}

@Composable
private fun ClarificationOption(title: String, chosen: Boolean, enabled: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .background(if (chosen) JournalStyle.Sage else Color.White, RoundedCornerShape(12.dp))
        .border(1.dp, if (chosen) JournalStyle.Green else JournalStyle.Border, RoundedCornerShape(12.dp))
        .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = choose).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(JournalStyle.Ink, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            if (chosen) drawCircle(JournalStyle.Ink, radius = size.minDimension / 2 - 4.dp.toPx())
        }
        Text(title, Modifier.weight(1f))
    }
}
