package com.cdcg.convolab.journal.text

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

val LocalRubyOnPaper = compositionLocalOf { false }

val LocalKnownKanji = compositionLocalOf<Set<Int>> { emptySet() }

data class RubyAppearance(val size: Int = 20, val font: FontFamily = JournalBrand.Handwriting, val knownKanji: Set<Int> = emptySet(), val color: Color = Color(0xFF4B1800), val readingColor: Color = Color(0xFF79776C), val compact: Boolean = false)

class RubyRun(val reading: String, val background: Color = Color.Transparent, val style: SpanStyle = SpanStyle(), val underline: RubyUnderline? = null, val onClick: (() -> Unit)? = null)

@Composable
private fun journalRubyAppearance(): RubyAppearance {
    val typography = LocalJournalTypography.current
    return RubyAppearance(typography.size, typography.family, LocalKnownKanji.current)
}

@Composable
fun RubyText(value: String, modifier: Modifier = Modifier, appearance: RubyAppearance = journalRubyAppearance()) {
    RubyParagraph(listOf(RubyRun(value)), modifier, appearance)
}

@Composable
fun RubyParagraph(runs: List<RubyRun>, modifier: Modifier = Modifier, appearance: RubyAppearance = journalRubyAppearance(), onRunBounds: ((Int, Rect) -> Unit)? = null) {
    val (size, font, knownKanji) = appearance
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val metrics = RubyTextMetrics(measurer, density, LocalFontFamilyResolver.current)
    val onPaper = LocalRubyOnPaper.current && !appearance.compact
    val pitch = when { appearance.compact -> 1.4; onPaper -> 2.7; else -> 1.8 }
    val style = TextStyle(fontFamily = font, fontSize = size.sp, letterSpacing = 0.sp, lineHeight = (size * pitch).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None), color = appearance.color)
    val parsed = runs.map { run -> remember(run.reading, knownKanji) { RubyDocument.parse(run.reading, knownKanji) } }
    val inline = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        runs.forEachIndexed { index, run ->
            if (run.onClick != null) pushLink(LinkAnnotation.Clickable("correction-$index", TextLinkStyles(style = SpanStyle(color = style.color, textDecoration = TextDecoration.None))) { run.onClick.invoke() })
            pushStringAnnotation("ruby-run", index.toString())
            pushStyle(SpanStyle(color = appearance.color).merge(run.style))
            parsed[index].forEachIndexed { tokenIndex, token ->
                if (token.reading == null) append(token.text) else {
                    val key = "$index-$tokenIndex"
                    inline[key] = rubyInlineContent(token, run, appearance, metrics)
                    appendInlineContent(key, token.text)
                }
            }
            pop()
            pop()
            if (run.onClick != null) pop()
        }
    }
    var highlights by remember { mutableStateOf(RubyHighlightLayout()) }
    val ink = metrics.ink("国", style)
    Text(text, modifier.drawBehind { if (highlights.text == text.text) drawRubyHighlights(highlights, runs, ink) }, style = style, inlineContent = inline, onTextLayout = { layout ->
        // Store geometry only: fresh link callbacks must not trigger another layout.
        highlights = rubyHighlightLayout(layout)
        text.getLinkAnnotations(0, text.length).forEach { link ->
            val index = (link.item as? LinkAnnotation.Clickable)?.tag?.removePrefix("correction-")?.toIntOrNull()
            if (index != null && link.start < link.end) onRunBounds?.invoke(index, layout.getPathForRange(link.start, link.end).getBounds())
        }
    })
}
