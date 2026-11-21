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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.journal.JournalId
import com.cdcg.convolab.journal.audio.*
import com.cdcg.convolab.R
import com.cdcg.convolab.journal.text.RubyText
import com.cdcg.convolab.ui.*

class TutorPresentation(val state: TutorState, val controller: TutorController)

@Composable
fun TutorCard(presentation: TutorPresentation) {
    val state = presentation.state
    val question = state.question
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (question?.tutor == null && state.preferences != null) JournalCard {
            Text("Choose your tutor", fontFamily = JournalBrand.Serif, fontSize = 30.sp)
            TutorChoices(presentation)
        }
        if (question != null) TeacherQuestion(question, state.source?.entryId)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        state.error?.takeUnless { state.choosing }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = presentation.controller::retry, enabled = !state.busy) { Text("Retry tutor") }
        }
    }
    if (state.choosing && question?.tutor != null) TutorPicker(presentation)
}

@Composable
fun TeacherQuestion(question: TutorQuestion, parentId: JournalId? = null) {
    val source = remember(parentId, question) { parentId?.let { AudioSource(it,
        AudioContent.Question(JournalId(question.id), question.text, question.reading.ifBlank { question.text }, "${question.tutor?.name ?: "Tutor"}'s question")) } }
    var english by rememberSaveable(question.id) { mutableStateOf(false) }
    Surface(color = Color.White, contentColor = JournalBrand.Ink, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9E6F2))) {
        Column {
            Row(Modifier.fillMaxWidth().background(Color(0xFFE4EFFC)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                question.tutor?.let { TutorPortrait(it, Modifier.size(80.dp)) }
                Box(Modifier.weight(1f)) { if (source != null) AudioControls(source) }
                TextButton(onClick = { english = !english }, modifier = Modifier.semantics { contentDescription = if (english) "Hide English" else "Show English" }) { Text("En", fontSize = 13.sp, color = Color(0xFF6393EF)) }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (source == null) RubyText(question.reading.ifBlank { question.text })
                else AudioReading(source)
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
    else Box(modifier.background(Color(0xFFE4EFFC), CircleShape), contentAlignment = Alignment.Center) { Text(tutor.name, color = JournalBrand.Ink) }
}

@Composable
fun TutorPicker(presentation: TutorPresentation) {
    val state = presentation.state
    JournalSheet("Choose your tutor", presentation.controller::dismissPicker) {
        TutorChoices(presentation)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = JournalStyle.Red)
            JournalDraftButton("Retry tutor", presentation.controller::retry, style = DraftButtonStyle(enabled = !state.busy))
        }
    }
}

@Composable
private fun TutorChoices(presentation: TutorPresentation) {
    val state = presentation.state
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.preferences?.tutors?.forEach { tutor ->
            JournalAvatarChoice(state.preferences.selectedId == tutor.id, tutor.name, !state.busy,
                Modifier.weight(1f), { presentation.controller.choose(tutor) }) {
                TutorPortrait(tutor, Modifier.size(80.dp).clearAndSetSemantics {})
            }
        }
    }
}
