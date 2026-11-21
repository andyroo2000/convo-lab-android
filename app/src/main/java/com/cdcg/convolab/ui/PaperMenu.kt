package com.cdcg.convolab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaperMenu(expanded: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, dismiss, modifier = Modifier.widthIn(min = 260.dp, max = 350.dp),
        shape = RoundedCornerShape(24.dp), containerColor = JournalBrand.Paper, tonalElevation = 0.dp, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
fun PaperMenuGroup(title: String) {
    Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp,
        color = JournalBrand.Ink.copy(alpha = .67f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun PaperMenuItem(title: String, icon: JournalSymbol, enabled: Boolean = true, action: () -> Unit) {
    DropdownMenuItem(text = { Text(title, style = JournalStyle.Body, color = JournalBrand.Ink) },
        leadingIcon = { JournalIcon(icon, color = JournalBrand.Ink) }, enabled = enabled, onClick = action,
        modifier = Modifier.heightIn(min = 49.dp), contentPadding = PaddingValues(horizontal = 12.dp))
}
