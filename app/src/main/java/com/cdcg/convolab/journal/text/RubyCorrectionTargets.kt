package com.cdcg.convolab.journal.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Use the same character geometry as the highlights, not Android's range selection paths. */
@Composable
internal fun RubyCorrectionTargets(layout: RubyHighlightLayout, runs: List<RubyRun>, ink: android.graphics.Rect) {
    val density = LocalDensity.current
    layout.lines.forEach { line ->
        val run = runs[line.run]
        run.onClick?.let { action ->
            val padding = with(density) { 2.dp.toPx() }
            val top = minOf(line.rect.top, line.baseline + ink.top - padding)
            val bottom = line.baseline + ink.bottom + padding
            val label = RubyDocument.parse(run.reading).joinToString("") { it.text }
            Box(Modifier.offset { IntOffset(line.rect.left.roundToInt(), top.roundToInt()) }
                .size(with(density) { line.rect.width.toDp() }, with(density) { (bottom - top).toDp() })
                .clipToBounds().semantics { contentDescription = "Show correction for $label" }
                .clickable(role = Role.Button, onClickLabel = "Show correction", onClick = action))
        }
    }
}
