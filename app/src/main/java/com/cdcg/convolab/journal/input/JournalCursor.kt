package com.cdcg.convolab.journal.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.text.TextLayoutResult
import com.cdcg.convolab.ui.JournalBrand

/** A glyph-height mask for Compose's cursor, retaining native blinking and scrolling. */
internal class JournalCursorBrush(private val fontSize: Float) : ShaderBrush() {
    var textLayout: (() -> TextLayoutResult?)? = null

    override fun createShader(size: Size): Shader = LinearGradientShader(
        Offset.Zero, Offset(0f, 1f),
        listOf(Color.Transparent, JournalBrand.Blue, JournalBrand.Blue, Color.Transparent),
        listOf(0f, .001f, .999f, 1f),
    )

    /** Called before drawing, using the same current layout coordinates as the native caret. */
    fun align(selection: Int) {
        val layout = textLayout?.invoke() ?: return
        val offset = selection.coerceIn(0, layout.layoutInput.text.length)
        val bounds = layout.getCursorRect(offset)
        val baseline = layout.getLineBaseline(layout.getLineForOffset(offset))
        val top = maxOf(bounds.top, baseline - fontSize)
        val bottom = minOf(bounds.bottom, baseline + fontSize * .2f)
        transform = Matrix().apply { translate(y = top); scale(y = bottom - top) }
    }
}
