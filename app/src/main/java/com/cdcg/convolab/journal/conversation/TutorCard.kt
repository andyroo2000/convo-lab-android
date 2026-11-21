package com.cdcg.convolab.journal.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.R
import com.cdcg.convolab.journal.text.RubyText
import com.cdcg.convolab.ui.JournalBrand

class TutorPresentation(val state: TutorState, val controller: TutorController)

@Composable
fun TutorCard(presentation: TutorPresentation) {
    val state = presentation.state
    val question = state.question
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (question != null) TeacherQuestion(question)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = presentation.controller::showPicker, enabled = !state.busy) { Text("Choose your tutor") }
        state.notice?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = presentation.controller::retry, enabled = !state.busy) { Text("Retry tutor") }
        }
    }
    if (state.choosing) TutorPicker(presentation)
}

@Composable
fun TeacherQuestion(question: TutorQuestion, audio: @Composable RowScope.() -> Unit = {}) {
    var english by rememberSaveable(question.id) { mutableStateOf(false) }
    Surface(color = Color.White, contentColor = JournalBrand.Ink, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9E6F2))) {
        Column {
            Row(Modifier.fillMaxWidth().background(Color(0xFFE4EFFC)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                question.tutor?.let { TutorPortrait(it, Modifier.size(80.dp)) }
                audio()
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { english = !english }) { Text(if (english) "Hide English" else "Show English") }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RubyText(question.reading.ifBlank { question.text })
                if (english) Text(question.translation, fontFamily = JournalBrand.Serif)
            }
        }
    }
}

@Composable
fun TutorPortrait(tutor: Tutor, modifier: Modifier = Modifier) {
    val artwork = when (tutor.avatar) {
        "hana-v1" -> R.drawable.tutor_female_v2
        "ren-v1" -> R.drawable.tutor_male_v2
        else -> null
    }
    if (artwork != null) Image(painterResource(artwork), "${tutor.name}, your tutor", modifier.clip(CircleShape))
    else Box(modifier.background(Color(0xFFE4EFFC), CircleShape), contentAlignment = Alignment.Center) { Text(tutor.name) }
}

@Composable
private fun TutorPicker(presentation: TutorPresentation) {
    val state = presentation.state
    AlertDialog(onDismissRequest = presentation.controller::dismissPicker, title = { Text("Choose your tutor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your choice applies to future questions.")
                state.preferences?.tutors?.forEach { tutor ->
                    OutlinedButton(onClick = { presentation.controller.choose(tutor) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        TutorPortrait(tutor, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(tutor.name + if (state.preferences.selectedId == tutor.id) " · Selected" else "")
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = presentation.controller::retry, enabled = !state.busy) { Text("Retry tutor") } }
            }
        }, confirmButton = { TextButton(onClick = presentation.controller::dismissPicker, enabled = !state.busy) { Text("Done") } })
}
