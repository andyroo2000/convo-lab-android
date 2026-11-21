package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cdcg.convolab.ui.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FeedbackToolbar(final: Boolean, choose: (Boolean) -> Unit, presentation: FeedbackPresentation, analysis: Analysis) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var copied by remember(final) { mutableStateOf(false) }
    Surface(color = JournalBrand.Paper, shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VersionPill("Draft", !final, presentation.state.finalEdit == null) { choose(false) }
        VersionPill("Final", final, presentation.state.finalEdit == null) { choose(true) }
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More journal options" }) { Text("⋯", fontSize = 24.sp) }
            PaperMenu(menu, { menu = false }) {
                PaperMenuItem(if (copied) "Copied" else "Copy text", JournalSymbol.Contents, action = {
                    clipboard.setText(AnnotatedString(presentation.state.finalEdit?.text ?: analysis.copyText(final))); copied = true; menu = false
                })
                if (final && presentation.state.finalEdit == null) PaperMenuItem("Edit final", JournalSymbol.Appearance, enabled = !presentation.state.busy,
                    action = { menu = false; presentation.controller.beginFinalEdit() })
            }
        }
    }
    }
}

@Composable
fun DraftFeedback(analysis: Analysis, fallback: String) {
    val documents = analysis.draftFeedback
    if (documents.isEmpty()) CorrectionText(emptyList(), fallback)
    else documents.forEachIndexed { index, feedback ->
        if (index > 0) HorizontalDivider()
        CorrectionText(feedback.segments, if (index == 0) fallback else "")
    }
}

@Composable
private fun VersionPill(label: String, chosen: Boolean, enabled: Boolean, choose: () -> Unit) {
    Surface(onClick = choose, enabled = enabled, color = if (chosen) Color(0xFF398B49) else Color(0xFFF6F9F0),
        contentColor = if (chosen) Color.White else Color(0xFF47664C), shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xFFB3CCA8)), modifier = Modifier.alpha(if (enabled) 1f else .5f).semantics { selected = chosen }) {
        Box(Modifier.widthIn(min = 90.dp).heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, fontFamily = JournalBrand.Serif, fontSize = 20.sp)
        }
    }
}
