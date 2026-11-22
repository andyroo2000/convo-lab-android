package com.cdcg.convolab.journal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Same stable, shallow tear as iOS JournalPaperEdge. */
object NotebookPaperShape : Shape by JournalPaperShape(false)
object NotebookReplyPaperShape : Shape by JournalPaperShape(true)

private class JournalPaperShape(private val tornTop: Boolean) : Shape {
    private val depths = listOf(5, 3, 6, 2, 4, 1, 5, 3, 6, 2, 4, 0, 3, 1, 5, 3, 6, 2, 4, 1, 3)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            for (step in depths.indices) {
                val y = if (tornTop) with(density) { depths[step].dp.toPx() } else 0f
                val x = size.width * step / (depths.size - 1)
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (step in depths.indices.reversed()) {
                val inset = with(density) { (depths[step] + 2).dp.toPx() }
                lineTo(size.width * step / (depths.size - 1), size.height - inset)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
