package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun ThinkingAvatar(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Your tutor" }) {
        clipPath(Path().apply { addOval(Rect(Offset.Zero, size)) }) {
            drawCircle(Color(0xFFA78B80))
            drawCircle(Color.White, radius = size.width * .18f, center = Offset(size.width * .5f, size.height * .36f))
            drawOval(Color.White, topLeft = Offset(size.width * .17f, size.height * .64f), size = Size(size.width * .66f, size.height * .24f))
        }
    }
}
