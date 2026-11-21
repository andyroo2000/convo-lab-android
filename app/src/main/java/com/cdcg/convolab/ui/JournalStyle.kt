package com.cdcg.convolab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

object JournalStyle {
    val Paper = Color(0xFFFFFDF7)
    val Ink = Color(0xFF4B1800)
    val Green = Color(0xFF407C35)
    val Sage = Color(0xFFEDF3E6)
    val Border = Color(0xFFD5DCCB)
    val Red = Color(0xFF9E443D)
    val Body = TextStyle(fontFamily = JournalBrand.Serif, fontSize = 20.sp, lineHeight = 25.sp)
}

@Composable
fun JournalCloseButton(label: String, close: () -> Unit) {
    IconButton(onClick = close, modifier = Modifier.size(44.dp).background(JournalStyle.Sage, CircleShape).semantics { contentDescription = label }) {
        JournalIcon(JournalSymbol.Close)
    }
}

@Composable
fun JournalHeading(title: String, close: () -> Unit, label: String = "Close $title") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontFamily = JournalBrand.Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, modifier = Modifier.weight(1f))
        JournalCloseButton(label, close)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSheet(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JournalStyle.Paper, contentColor = JournalStyle.Ink,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), dragHandle = null) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF398B47)))
        ProvideTextStyle(JournalStyle.Body) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                JournalHeading(title, close)
                content()
            }
        }
    }
}

@Composable
fun JournalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = JournalStyle.Green
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JournalStyle.Sage, contentColor = ink,
            disabledContainerColor = JournalStyle.Sage.copy(alpha = .5f), disabledContentColor = ink.copy(alpha = .5f)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) { Text(text, style = JournalStyle.Body) }
}

@Composable
fun JournalCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, color = JournalStyle.Paper, contentColor = JournalStyle.Ink,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, JournalStyle.Border)) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF398B47)))
            ProvideTextStyle(JournalStyle.Body) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

data class DraftButtonStyle(val enabled: Boolean = true, val filled: Boolean = true, val destructive: Boolean = false)

@Composable
fun JournalDraftButton(text: String, action: () -> Unit, modifier: Modifier = Modifier, style: DraftButtonStyle = DraftButtonStyle()) {
    val (enabled, filled, destructive) = style
    val ink = if (destructive) JournalStyle.Red else JournalStyle.Green
    Button(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 44.dp), shape = CircleShape,
        border = BorderStroke(1.dp, ink.copy(alpha = .5f)),
        colors = ButtonDefaults.buttonColors(containerColor = if (filled) ink else JournalStyle.Paper,
            contentColor = if (filled) JournalStyle.Paper else ink), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, style = JournalStyle.Body)
    }
}

@Composable
fun JournalAvatarChoice(selected: Boolean, label: String, enabled: Boolean, modifier: Modifier = Modifier, choose: () -> Unit, portrait: @Composable () -> Unit) {
    Surface(onClick = choose, enabled = enabled, modifier = modifier.semantics { contentDescription = label; this.selected = selected },
        color = if (selected) JournalStyle.Sage else JournalStyle.Paper, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) JournalStyle.Green else JournalStyle.Border)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.background(Color.White, CircleShape).padding(8.dp)) { portrait() }
            if (selected) JournalIcon(JournalSymbol.Check, Modifier.align(Alignment.TopEnd).padding(end = 10.dp).size(14.dp), JournalStyle.Green)
        }
    }
}

@Composable
fun PaperSliderThumb(size: Dp = 24.dp) {
    Box(Modifier.size(size).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSliderTrack(state: SliderState, accent: Color = JournalStyle.Green) {
    SliderDefaults.Track(state, modifier = Modifier.height(4.dp), thumbTrackGapSize = 0.dp,
        colors = SliderDefaults.colors(activeTrackColor = accent, inactiveTrackColor = JournalStyle.Border,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent), drawStopIndicator = null)
}
