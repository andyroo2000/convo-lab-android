package com.cdcg.convolab.journal.feedback

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cdcg.convolab.journal.conversation.Tutor
import com.cdcg.convolab.journal.conversation.TutorPortrait
import com.cdcg.convolab.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

val LocalThinkingTutor = compositionLocalOf<Tutor?> { null }

@Composable
fun JournalThinking(lastAnswer: String? = null, returnToDraft: (() -> Unit)? = null) {
    val phrase = remember { ThinkingPhrases.next() }
    var english by rememberSaveable { mutableStateOf(true) }
    val reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    val elapsed = thinkingElapsed(reducedMotion)
    Surface(color = Color(0xFFFFFDF6), contentColor = Color(0xFF542006), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8E4F2))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).testTag("journal-thinking"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (english) "Hide English" else "Show English", fontSize = 13.sp, fontFamily = FontFamily.SansSerif,
                modifier = Modifier.align(Alignment.End).clickable(role = Role.Button) { english = !english })
            Spacer(Modifier.height(18.dp))
            ThinkingOrnament(elapsed, reducedMotion)
            Spacer(Modifier.height(28.dp))
            WaitingTutorPortrait(Modifier.size(80.dp))
            Spacer(Modifier.height(18.dp))
            Text(phrase.first, fontFamily = FontFamily.SansSerif, fontSize = 21.sp, textAlign = TextAlign.Center)
            if (english) Text(phrase.second, fontFamily = JournalBrand.Serif, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
            Spacer(Modifier.height(24.dp))
            Box(Modifier.semantics { contentDescription = "Your tutor is preparing your feedback. Your writing is saved."; liveRegion = LiveRegionMode.Polite }) {
                ThinkingDots(elapsed, reducedMotion)
            }
            if (elapsed >= 60) {
                Text("This is taking longer than usual. Your writing is saved.", style = JournalStyle.Body, modifier = Modifier.padding(top = 20.dp))
                if (returnToDraft != null) TextButton(onClick = returnToDraft) { Text("Return to journal") }
            }
            lastAnswer?.let { SavedThinkingAnswer(it) }
        }
    }
}

@Composable
fun WaitingTutorPortrait(modifier: Modifier = Modifier) {
    val tutor = LocalThinkingTutor.current
    if (tutor != null) TutorPortrait(tutor, modifier)
    else ThinkingAvatar(modifier)
}

@Composable
private fun SavedThinkingAnswer(answer: String) {
    HorizontalDivider(Modifier.padding(top = 36.dp, bottom = 18.dp), thickness = .5.dp, color = Color(0xFFDDD8C9))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        JournalIcon(JournalSymbol.Check, Modifier.size(16.dp), Color(0xFF6A8267))
        Text(answer, fontFamily = JournalBrand.Serif, fontSize = 17.sp, color = Color(0xFF594C44))
    }
}

@Composable
private fun thinkingElapsed(reducedMotion: Boolean): Float {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val opened = remember { SystemClock.elapsedRealtime() }
    val elapsed by produceState(0f, lifecycle, reducedMotion) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = (SystemClock.elapsedRealtime() - opened) / 1000f
                delay(if (reducedMotion) 1000 else 50)
            }
        }
    }
    return elapsed
}
