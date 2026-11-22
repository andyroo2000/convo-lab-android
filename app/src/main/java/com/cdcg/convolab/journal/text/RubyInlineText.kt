package com.cdcg.convolab.journal.text

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

internal class RubyTextMetrics(val measurer: TextMeasurer, val density: Density, val fonts: FontFamily.Resolver) {
    fun ink(text: String, style: TextStyle): android.graphics.Rect {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal,
                style.fontStyle ?: FontStyle.Normal, style.fontSynthesis ?: FontSynthesis.All).value as Typeface
            textSize = with(density) { style.fontSize.toPx() }
        }
        return android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }
    }
}

internal fun rubyInlineContent(token: RubyToken, run: RubyRun, appearance: RubyAppearance, metrics: RubyTextMetrics): InlineTextContent {
    val (initialReadingStyle, baseStyle) = rubyInlineStyles(appearance, run)
    val base = metrics.measurer.measure(token.text, baseStyle)
    val readingText = token.reading.orEmpty()
    val naturalReading = metrics.measurer.measure(readingText, initialReadingStyle)
    val characters = readingText.codePointCount(0, readingText.length)
    val spread = if (characters > 1) (base.size.width - naturalReading.size.width).coerceAtLeast(0).toFloat() / characters else 0f
    val readingStyle = initialReadingStyle.copy(letterSpacing = with(metrics.density) { spread.toSp() })
    val reading = metrics.measurer.measure(readingText, readingStyle)
    val baseInk = metrics.ink(token.text, baseStyle)
    val readingInk = metrics.ink(token.reading.orEmpty(), readingStyle)
    val gap = with(metrics.density) { appearance.size.sp.toPx() } * .10f
    // Overlap only the fonts' empty metric space. Keep both full text boxes to avoid clipping fallback glyphs.
    val baseTop = (reading.firstBaseline + readingInk.bottom + gap - base.firstBaseline - baseInk.top).coerceAtLeast(0f)
    val height = maxOf(reading.size.height.toFloat(), baseTop + base.size.height)
    val placeholder = with(metrics.density) {
        Placeholder((maxOf(base.size.width, reading.size.width) + 2).toSp(), height.toSp(), PlaceholderVerticalAlign.TextBottom)
    }
    return InlineTextContent(placeholder) {
        val click = run.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
        Box(Modifier.fillMaxSize().then(click)) {
            Text(token.reading.orEmpty(), Modifier.align(Alignment.TopCenter), softWrap = false, style = readingStyle)
            Text(token.text, Modifier.align(Alignment.BottomCenter), softWrap = false, style = baseStyle)
        }
    }
}

private fun rubyInlineStyles(appearance: RubyAppearance, run: RubyRun): Pair<TextStyle, TextStyle> {
    val reading = TextStyle(letterSpacing = 0.sp, fontSize = (appearance.size * .42).sp,
        color = if (appearance.compact) run.style.color.takeOrElse { appearance.readingColor } else appearance.readingColor)
    val base = TextStyle(fontFamily = appearance.font, fontWeight = run.style.fontWeight, fontStyle = run.style.fontStyle,
        letterSpacing = 0.sp, fontSize = appearance.size.sp, color = run.style.color.takeOrElse { appearance.color })
    return reading to base
}
