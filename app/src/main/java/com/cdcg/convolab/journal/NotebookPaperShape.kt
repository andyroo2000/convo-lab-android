package com.cdcg.convolab.journal

import androidx.compose.foundation.shape.GenericShape

val NotebookPaperShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    for (step in 20 downTo 0) {
        val inset = if (step % 2 == 0) size.width * 0.005f else 0f
        lineTo(size.width * step / 20f, size.height - inset)
    }
    close()
}
