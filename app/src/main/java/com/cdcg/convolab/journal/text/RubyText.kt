package com.cdcg.convolab.journal.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.journal.appearance.LocalJournalTypography

val LocalKnownKanji = compositionLocalOf<Set<Int>> { emptySet() }

data class RubyAppearance(val size: Int = 20, val font: FontFamily = JournalBrand.Handwriting, val knownKanji: Set<Int> = emptySet(), val color: Color = Color(0xFF4B1800), val readingColor: Color = Color(0xFF79776C), val compact: Boolean = false)

class RubyRun(val reading: String, val background: Color = Color.Transparent, val style: SpanStyle = SpanStyle(), val onClick: (() -> Unit)? = null)

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
    val style = TextStyle(fontFamily = font, fontSize = size.sp, letterSpacing = 0.sp, lineHeight = (size * if (appearance.compact) 1.4 else 1.8).sp, color = appearance.color)
    val parsed = runs.map { run -> remember(run.reading, knownKanji) { RubyDocument.parse(run.reading, knownKanji) } }
    val inline = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        runs.forEachIndexed { index, run ->
            if (run.onClick != null) pushLink(LinkAnnotation.Clickable("correction-$index", TextLinkStyles(style = SpanStyle(color = style.color, textDecoration = TextDecoration.None))) { run.onClick.invoke() })
            pushStyle(SpanStyle(background = run.background, color = appearance.color).merge(run.style))
            parsed[index].forEachIndexed { tokenIndex, token ->
                if (token.reading == null) append(token.text) else {
                    val key = "$index-$tokenIndex"
                    val baseWidth = measurer.measure(token.text, style.merge(run.style).copy(lineHeight = size.sp)).size.width
                    val rubyWidth = measurer.measure(token.reading, style.copy(fontSize = (size * 0.48).sp)).size.width
                    val width = with(density) { (maxOf(baseWidth, rubyWidth) + 2).toSp() }
                    inline[key] = InlineTextContent(Placeholder(width, (size * if (appearance.compact) 1.6 else 2.0).sp, PlaceholderVerticalAlign.TextBottom)) {
                        RubyInlineText(token, appearance, run)
                    }
                    appendInlineContent(key, token.text)
                }
            }
            pop()
            if (run.onClick != null) pop()
        }
    }
    Text(text, modifier, style = style, inlineContent = inline, onTextLayout = { layout ->
        text.getLinkAnnotations(0, text.length).forEach { link ->
            val index = (link.item as? LinkAnnotation.Clickable)?.tag?.removePrefix("correction-")?.toIntOrNull()
            if (index != null && link.start < link.end) onRunBounds?.invoke(index, layout.getPathForRange(link.start, link.end).getBounds())
        }
    })
}

@Composable
private fun RubyInlineText(token: RubyToken, appearance: RubyAppearance, run: RubyRun) {
    val (size, font) = appearance
    val click = run.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Column(Modifier.fillMaxSize().background(run.background).then(click), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(token.reading.orEmpty(), softWrap = false, letterSpacing = 0.sp, fontSize = (size * 0.48).sp, lineHeight = (size * if (appearance.compact) .5 else .6).sp, color = if (appearance.compact) run.style.color.takeOrElse { appearance.readingColor } else appearance.readingColor)
        Text(token.text, fontFamily = font, fontWeight = run.style.fontWeight, fontStyle = run.style.fontStyle, softWrap = false, letterSpacing = 0.sp, fontSize = size.sp, lineHeight = (size * if (appearance.compact) 1.1 else 1.4).sp, color = run.style.color.takeOrElse { appearance.color })
    }
}
