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
                    val (readingStyle, baseStyle) = rubyInlineStyles(appearance, run)
                    val base = measurer.measure(token.text, baseStyle).size
                    val reading = measurer.measure(token.reading, readingStyle).size
                    val width = with(density) { (maxOf(base.width, reading.width) + 2).toSp() }
                    // Font fallback metrics can exceed the requested line height, especially in compact serif text.
                    val height = with(density) { (base.height + reading.height).toSp() }
                    inline[key] = InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextBottom)) {
                        RubyInlineText(token, run, readingStyle, baseStyle)
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

private fun rubyInlineStyles(appearance: RubyAppearance, run: RubyRun): Pair<TextStyle, TextStyle> {
    val (size, font) = appearance
    val reading = TextStyle(letterSpacing = 0.sp, fontSize = (size * .48).sp,
        lineHeight = (size * if (appearance.compact) .5 else .6).sp,
        color = if (appearance.compact) run.style.color.takeOrElse { appearance.readingColor } else appearance.readingColor)
    val base = TextStyle(fontFamily = font, fontWeight = run.style.fontWeight, fontStyle = run.style.fontStyle,
        letterSpacing = 0.sp, fontSize = size.sp, lineHeight = (size * if (appearance.compact) 1.1 else 1.4).sp,
        color = run.style.color.takeOrElse { appearance.color })
    return reading to base
}

@Composable
private fun RubyInlineText(token: RubyToken, run: RubyRun, readingStyle: TextStyle, baseStyle: TextStyle) {
    val click = run.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Column(Modifier.fillMaxSize().background(run.background).then(click), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(token.reading.orEmpty(), softWrap = false, style = readingStyle)
        Text(token.text, softWrap = false, style = baseStyle)
    }
}
