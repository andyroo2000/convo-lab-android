package com.cdcg.convolab.journal.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ThinkingOrnament(elapsed: Float, reducedMotion: Boolean) {
    val deck = remember { thinkingIllustrations.shuffled().withIndex().groupBy { it.index % 3 }.values.map { group -> group.map { it.value } } }
    Row(Modifier.fillMaxWidth().height(47.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OrnamentRule(Modifier.weight(1f))
        repeat(3) { slot ->
            val frame = ThinkingTiming.frame(elapsed, slot)
            val options = deck[slot]
            val item = options[if (reducedMotion) 0 else frame.turn % options.size]
            ThinkingImage(item, if (reducedMotion) 1f else frame.opacity)
        }
        OrnamentRule(Modifier.weight(1f))
    }
}

@Composable
private fun OrnamentRule(modifier: Modifier) {
    Canvas(modifier.height(4.dp)) {
        val ink = Color(0x809B9486)
        drawLine(ink, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
        drawLine(ink, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
    }
}

@Composable
private fun ThinkingImage(item: ThinkingIllustration, opacity: Float) {
    val atlas = ImageBitmap.imageResource(item.atlas)
    Canvas(Modifier.size(width = 49.dp, height = 41.dp)) {
        val scale = minOf(size.width / item.width, size.height / item.height)
        val width = (item.width * scale).roundToInt()
        val height = (item.height * scale).roundToInt()
        drawImage(atlas, srcOffset = IntOffset(item.x, item.y), srcSize = IntSize(item.width, item.height),
            dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height), alpha = opacity)
    }
}

@Composable
internal fun ThinkingDots(elapsed: Float, reducedMotion: Boolean) {
    Canvas(Modifier.size(width = 45.dp, height = 12.dp)) {
        repeat(3) { dot ->
            drawCircle(Color(0xFF635D53).copy(alpha = if (reducedMotion) .65f else ThinkingTiming.dot(elapsed, dot)),
                radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset((3.5f + dot * 17f).dp.toPx(), size.height / 2))
        }
    }
}
