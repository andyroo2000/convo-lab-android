package com.cdcg.convolab.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.journal.input.JournalWritingField
import com.cdcg.convolab.journal.input.WritingOptions
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.appearance.LocalJournalTypography
import com.cdcg.convolab.journal.appearance.JournalTypography
import androidx.compose.ui.platform.LocalDensity

@Composable
fun NotebookPaper(state: NotebookState, edit: (String) -> Unit) {
    val typography = LocalJournalTypography.current
    val linePitch = typography.size * JournalTypography.PaperLinePitch
    val minimumWritingHeight = with(LocalDensity.current) { (linePitch * 4).sp.toDp() }
    Box(Modifier.fillMaxWidth().shadow(3.dp, NotebookPaperShape).clip(NotebookPaperShape).background(JournalBrand.Paper)
        .border(1.dp, Color(0xFFE5DECC), NotebookPaperShape).drawBehind {
            drawRules(linePitch)
        }.padding(start = 28.dp, end = 14.dp, top = 20.dp, bottom = 36.dp)) {
        JournalWritingField(text = state.draft.body, changed = edit,
            options = WritingOptions("Journal entry", state.canEdit, typography.style.copy(color = Color(0xFF452713)), "今日は…"), modifier = Modifier.fillMaxWidth().heightIn(min = minimumWritingHeight).testTag("journal-editor"))
    }
}

private fun DrawScope.drawRules(linePitch: Double) {
    val margin = 15.dp.toPx()
    drawLine(Color(0xFFD58B78), Offset(margin, 0f), Offset(margin, size.height), 1.dp.toPx())
    var y = 20.dp.toPx() + linePitch.sp.toPx()
    while (y < size.height) {
        drawLine(Color(0xFFD1D9DA), Offset(28.dp.toPx(), y), Offset(size.width - 14.dp.toPx(), y), 0.5.dp.toPx())
        y += linePitch.sp.toPx()
    }
}

/** Rules for editable passages, including final edits and tutor replies. */
fun Modifier.notebookLines(fontSize: Int): Modifier = drawBehind {
    val pitch = (fontSize * JournalTypography.PaperLinePitch).sp.toPx()
    var y = pitch
    while (y < size.height) {
        drawLine(Color(0xFFD1D9DA), Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
        y += pitch
    }
}
