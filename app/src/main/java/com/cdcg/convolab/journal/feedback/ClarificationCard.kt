package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
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
    var selected by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    var other by rememberSaveable(question.id) { mutableStateOf("") }
    val custom = "Write my own answer"
    val answer = if (selected == custom) other else selected.orEmpty()
    val pending = presentation.state.snapshot?.pendingAnswer != null
    val enabled = !presentation.state.busy && !pending
    Surface(color = Color(0xFFEDF5FF), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question.prompt, fontSize = 21.sp)
            (question.choices + custom).distinct().forEach { choice ->
                OutlinedButton(onClick = { selected = choice }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = choice == selected, onClick = null)
                    Text(choice, Modifier.weight(1f).padding(start = 8.dp))
                }
            }
            if (selected == custom) CustomAnswer(other, enabled) { other = it }
            Button(onClick = { presentation.controller.answer(answer) }, enabled = enabled && answer.isNotBlank(),
                modifier = Modifier.testTag("clarification-send")) { Text("Answer") }
            if (pending) Text("Your answer is saved. Retry to confirm it was received.")
        }
    }
}

@Composable
private fun CustomAnswer(value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = {
        if (it.codePointCount(0, it.length) <= 2_000 && !it.contains('\u0000')) change(it)
    }, label = { Text("Your answer") }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("clarification-answer"))
}
