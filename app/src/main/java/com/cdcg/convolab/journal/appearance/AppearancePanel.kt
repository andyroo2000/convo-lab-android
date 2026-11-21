package com.cdcg.convolab.journal.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePanel() {
    val store = LocalAppearanceStore.current
    val typography = LocalJournalTypography.current
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, JournalStyle.Border)) {
        Text("今日はいい一日でした。", style = typography.style,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("appearance-preview"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JournalFont.entries.chunked(2).forEach { fonts ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fonts.forEach { font -> FontCard(font, typography.font == font, Modifier.weight(1f)) { store?.change(typography.copy(font = font)) } }
                if (fonts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Font size")
            Text("${typography.size} pt")
        }
        val slider = rememberSliderState(typography.size.toFloat(), steps = 13, trackRange = 16f..30f)
        SideEffect { slider.value = typography.size.toFloat() }
        Slider(state = slider, onValueChange = { slider.value = it; store?.change(typography.copy(size = it.toInt())) }, thumb = { PaperSliderThumb() }, track = { PaperSliderTrack(it) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = JournalStyle.Green),
            modifier = Modifier.semantics { contentDescription = "Font size"; stateDescription = "${typography.size} scaled points" })
    }
}

@Composable
private fun FontCard(font: JournalFont, chosen: Boolean, modifier: Modifier, choose: () -> Unit) {
    Surface(onClick = choose, modifier = modifier.semantics { selected = chosen }, color = if (chosen) JournalStyle.Sage else Color.White,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) JournalStyle.Green else JournalStyle.Border)) {
        Box {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aa あ", fontFamily = JournalTypography(font).family, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics {})
                Text(font.label, style = JournalStyle.Body)
            }
            if (chosen) JournalIcon(JournalSymbol.Check, Modifier.align(Alignment.TopEnd).padding(10.dp).size(14.dp), JournalStyle.Green)
        }
    }
}
