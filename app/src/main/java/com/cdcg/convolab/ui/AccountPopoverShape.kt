package com.cdcg.convolab.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** The trailing account avatar anchors the paper popover's top pointer. */
internal object AccountPopoverShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pointer = with(density) { 8.dp.toPx() }
        val corner = with(density) { 24.dp.toPx() }
        val center = if (layoutDirection == LayoutDirection.Ltr) size.width - corner else corner
        val path = Path().apply {
            addRoundRect(RoundRect(0f, pointer, size.width, size.height, CornerRadius(corner)))
            moveTo(center - pointer, pointer)
            lineTo(center, 0f)
            lineTo(center + pointer, pointer)
            close()
        }
        return Outline.Generic(path)
    }
}
