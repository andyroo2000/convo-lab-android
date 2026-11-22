package com.cdcg.convolab.journal.text

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse

internal class RubyTextMetrics(val measurer: TextMeasurer, val density: Density, val fonts: FontFamily.Resolver) {
    fun typeface(style: TextStyle): Typeface = fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal,
        style.fontStyle ?: FontStyle.Normal, style.fontSynthesis ?: FontSynthesis.All).value as? Typeface ?: Typeface.DEFAULT

    fun ink(text: String, style: TextStyle): android.graphics.Rect {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(style)
            textSize = with(density) { style.fontSize.toPx() }
        }
        return android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }
    }
}

private data class RubyInlineLayout(val placeholder: Placeholder, val readingStyle: TextStyle, val baseStyle: TextStyle)

@Composable
internal fun rubyInlineContent(token: RubyToken, run: RubyRun, appearance: RubyAppearance, metrics: RubyTextMetrics): InlineTextContent {
    val styles = rubyInlineStyles(appearance, run)
    // Typeface values are keys too, so asynchronously resolved fonts invalidate measurements.
    val baseTypeface = metrics.typeface(styles.second)
    val readingTypeface = metrics.typeface(styles.first)
    val layout = remember(token, styles, metrics, baseTypeface, readingTypeface) { measureRubyInline(token, styles, metrics) }
    return InlineTextContent(layout.placeholder) {
        val click = run.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
        Box(Modifier.fillMaxSize().then(click)) {
            Text(token.reading.orEmpty(), Modifier.align(Alignment.TopCenter), softWrap = false, style = layout.readingStyle)
            Text(token.text, Modifier.align(Alignment.BottomCenter), softWrap = false, style = layout.baseStyle)
        }
    }
}

private fun measureRubyInline(token: RubyToken, styles: Pair<TextStyle, TextStyle>, metrics: RubyTextMetrics): RubyInlineLayout {
    val (initialReadingStyle, baseStyle) = styles
    val base = metrics.measurer.measure(token.text, baseStyle)
    val readingText = token.reading.orEmpty()
    val naturalReading = metrics.measurer.measure(readingText, initialReadingStyle)
    val characters = readingText.codePointCount(0, readingText.length)
    val spread = if (characters > 1) (base.size.width - naturalReading.size.width).coerceAtLeast(0).toFloat() / characters else 0f
    val readingStyle = initialReadingStyle.copy(letterSpacing = with(metrics.density) { spread.toSp() })
    val reading = metrics.measurer.measure(readingText, readingStyle)
    val baseInk = metrics.ink(token.text, baseStyle)
    val readingInk = metrics.ink(token.reading.orEmpty(), readingStyle)
    val gap = with(metrics.density) { baseStyle.fontSize.toPx() } * .10f
    // Overlap only the fonts' empty metric space. Keep both full text boxes to avoid clipping fallback glyphs.
    val baseTop = (reading.firstBaseline + readingInk.bottom + gap - base.firstBaseline - baseInk.top).coerceAtLeast(0f)
    val height = maxOf(reading.size.height.toFloat(), baseTop + base.size.height)
    val placeholder = with(metrics.density) {
        Placeholder((maxOf(base.size.width, reading.size.width) + 2).toSp(), height.toSp(), PlaceholderVerticalAlign.TextBottom)
    }
    return RubyInlineLayout(placeholder, readingStyle, baseStyle)
}

private fun rubyInlineStyles(appearance: RubyAppearance, run: RubyRun): Pair<TextStyle, TextStyle> {
    val size = run.style.fontSize.takeOrElse { appearance.size.sp }
    val reading = TextStyle(letterSpacing = 0.sp, fontSize = size * .42f,
        color = if (appearance.compact) run.style.color.takeOrElse { appearance.readingColor } else appearance.readingColor)
    val base = TextStyle(fontFamily = run.style.fontFamily ?: appearance.font, fontWeight = run.style.fontWeight, fontStyle = run.style.fontStyle,
        letterSpacing = 0.sp, fontSize = size, color = run.style.color.takeOrElse { appearance.color })
    return reading to base
}
