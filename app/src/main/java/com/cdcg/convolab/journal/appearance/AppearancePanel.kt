package com.cdcg.convolab.journal.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.journal.text.RubyText

@Composable
fun AppearancePanel() {
    val store = LocalAppearanceStore.current
    val typography = LocalJournalTypography.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("appearance-preview")) {
            RubyText("今日[きょう]はいい一日[いちにち]でした。")
            Text("Today was a good day.", style = typography.style)
        }
    }
    JournalFont.entries.forEach { font ->
        OutlinedButton(onClick = { store?.change(typography.copy(font = font)) }, modifier = Modifier.fillMaxWidth().semantics { selected = typography.font == font }) {
            Text("Aa あ", fontFamily = JournalTypography(font).family, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(16.dp))
            Text(font.label, Modifier.weight(1f))
            if (typography.font == font) Text("✓", Modifier.clearAndSetSemantics {})
        }
    }
    Text("Base font size · ${typography.size} sp")
    Slider(value = typography.size.toFloat(), onValueChange = { store?.change(typography.copy(size = it.toInt())) },
        valueRange = 16f..30f, steps = 13, modifier = Modifier.semantics { contentDescription = "Font size"; stateDescription = "${typography.size} scaled points" })
    Text("Your device’s text-size setting also applies. This choice is saved on this device.", style = MaterialTheme.typography.bodySmall)
}
