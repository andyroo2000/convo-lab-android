package com.cdcg.convolab.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.journal.conversation.Tutor
import com.cdcg.convolab.journal.conversation.TutorPortrait
import com.cdcg.convolab.journal.text.RubyText
import com.cdcg.convolab.ui.*

@Composable
fun JournalMissingEntry(writing: String, back: () -> Unit, recover: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Surface(color = Color(0xFFFFFDF6), contentColor = Color(0xFF4B1800), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("404 · Entry Not Found", fontSize = 12.sp, color = Color.Gray)
            TutorPortrait(Tutor("hana", "Hana", "female", "hana-v1", null), Modifier.size(94.dp))
            Text("Today’s phrase", fontSize = 12.sp)
            RubyText("見[み]つかりません。")
            Text("“I can’t find it.”", fontFamily = JournalBrand.Serif, fontSize = 18.sp)
            Text("This entry is no longer available on the server. Your saved writing is kept on this device.",
                fontFamily = JournalBrand.Serif, fontSize = 16.sp, textAlign = TextAlign.Center)
            JournalDraftButton("Back to Journal", back)
            TextButton(onClick = { clipboard.setText(AnnotatedString(writing)) }) { Text("Copy saved writing") }
            TextButton(onClick = recover) { Text("Save as a new entry") }
        }
    }
}
