package com.cdcg.convolab.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

@Composable
fun NotebookPaper(state: NotebookState, edit: (String) -> Unit) {
    val typography = LocalJournalTypography.current
    val linePitch = typography.size * 2.2
    val field = rememberTextFieldState(state.draft.body)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(edit) }
    LaunchedEffect(state.busy) {
        if (!state.busy && field.text.toString() != state.draft.body) field.setTextAndPlaceCursorAtEnd(state.draft.body)
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 260.dp).shadow(3.dp, NotebookPaperShape).clip(NotebookPaperShape).background(JournalBrand.Paper)
        .border(1.dp, Color(0xFFE5DECC), NotebookPaperShape).drawBehind {
            drawRules(linePitch)
        }.padding(start = 28.dp, end = 14.dp, top = 28.dp, bottom = 38.dp)) {
        BasicTextField(state = field, inputTransformation = {
                val text = asCharSequence().toString()
                if (text.codePointCount(0, text.length) > 20_000 || text.contains('\u0000')) revertAllChanges()
            }, enabled = state.canEdit,
            textStyle = typography.style.copy(color = Color(0xFF452713)), cursorBrush = SolidColor(JournalBrand.Blue),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().heightIn(min = 194.dp).testTag("journal-editor")
                .semantics { contentDescription = "Journal entry" },
            decorator = { inner ->
                if (state.draft.body.isEmpty()) Text("今日は…", fontFamily = typography.family,
                    fontSize = typography.size.sp, lineHeight = linePitch.sp, color = Color(0xFFBEBBB2))
                inner()
            })
    }
}

private fun DrawScope.drawRules(linePitch: Double) {
    val margin = 15.dp.toPx()
    drawLine(Color(0xFFD58B78), Offset(margin, 0f), Offset(margin, size.height), 1.dp.toPx())
    var y = 28.dp.toPx() + linePitch.sp.toPx() * (34f / 44f)
    while (y < size.height) {
        drawLine(Color(0xFFD1D9DA), Offset(28.dp.toPx(), y), Offset(size.width - 14.dp.toPx(), y), 0.5.dp.toPx())
        y += linePitch.sp.toPx()
    }
}
