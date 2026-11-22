package com.cdcg.convolab.journal.appearance

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.R
import com.cdcg.convolab.ui.JournalBrand

enum class JournalFont(val label: String) { Klee("Klee One"), Sans("Sans-serif"), Serif("Serif") }
data class JournalTypography(val font: JournalFont = JournalFont.Klee, val size: Int = 20) {
    val family: FontFamily get() = when (font) {
        JournalFont.Klee -> JournalBrand.Handwriting
        JournalFont.Sans -> JapaneseSans
        JournalFont.Serif -> JapaneseSerif
    }
    val style: TextStyle get() = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = (size * PaperLinePitch).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None))
    companion object {
        const val PaperLinePitch = 2.7
        private val JapaneseSans = FontFamily(Font(R.font.noto_sans_jp))
        private val JapaneseSerif = FontFamily(Font(R.font.noto_serif_jp))
        fun restored(font: String?, size: Int) = JournalTypography(JournalFont.entries.firstOrNull { it.name == font } ?: JournalFont.Klee, size.coerceIn(16, 30))
    }
}
val LocalJournalTypography = compositionLocalOf { JournalTypography() }
