package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
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
            Text(question.prompt, style = JournalStyle.Body, modifier = Modifier.padding(bottom = 12.dp))
            (question.choices + custom).distinct().forEach { choice ->
                ClarificationOption(choice, choice == selected, enabled) { selected = choice }
            }
            if (selected == custom) CustomAnswer(other, enabled) { other = it }
            JournalDraftButton("Answer", { focus.clearFocus(); keyboard?.hide(); presentation.controller.answer(answer) }, style = DraftButtonStyle(enabled = enabled && answer.isNotBlank()),
                modifier = Modifier.testTag("clarification-send"))
            TextButton(onClick = presentation.controller::cancel) { Text("Back to draft", color = JournalStyle.Green) }
            if (pending) Text("Your answer is saved. Retry to confirm it was received.")
        }
    }
}

@Composable
private fun CustomAnswer(value: String, enabled: Boolean, change: (String) -> Unit) {
    var invalid by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = {
        invalid = it.codePointCount(0, it.length) > 2_000 || it.contains('\u0000')
        if (!invalid) change(it)
    }, label = { Text("Your answer") }, isError = invalid,
        supportingText = { Text(if (invalid) "Use up to 2,000 characters without null characters." else "${value.codePointCount(0, value.length)} / 2,000") }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("clarification-answer"))
}

@Composable
private fun ClarificationOption(title: String, chosen: Boolean, enabled: Boolean, choose: () -> Unit) {
    OutlinedButton(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (chosen) JournalStyle.Green else JournalStyle.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (chosen) JournalStyle.Sage else Color.White, contentColor = JournalStyle.Ink)) {
        RadioButton(selected = chosen, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = JournalStyle.Green, unselectedColor = JournalStyle.Ink))
        Text(title, Modifier.weight(1f).padding(start = 8.dp))
    }
}
