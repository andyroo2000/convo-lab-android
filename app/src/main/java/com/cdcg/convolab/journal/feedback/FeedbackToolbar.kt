package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cdcg.convolab.ui.JournalBrand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF398B49), selectedLabelColor = Color.White)
        FilterChip(shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp), colors = colors, selected = !final, onClick = { choose(false) }, label = { Text("Draft", fontFamily = JournalBrand.Serif, fontSize = 20.sp) })
        FilterChip(shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp), colors = colors, selected = final, onClick = { choose(true) }, label = { Text("Final", fontFamily = JournalBrand.Serif, fontSize = 20.sp) })
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More journal options" }) { Text("⋯", fontSize = 24.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (copied) "Copied" else "Copy text") }, onClick = {
                    clipboard.setText(AnnotatedString(analysis.copyText(final))); copied = true; menu = false
                })
                if (final) DropdownMenuItem(text = { Text("Edit final") }, enabled = !presentation.state.busy,
                    onClick = { menu = false; presentation.controller.beginFinalEdit() })
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
