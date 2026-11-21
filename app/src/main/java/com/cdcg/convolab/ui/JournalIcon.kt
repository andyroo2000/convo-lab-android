package com.cdcg.convolab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

// Small outline symbols share the weight of the iOS notebook controls.
enum class JournalSymbol(val path: String) {
    Close("M5 5L19 19M19 5L5 19"),
    Chevron("M9 5L16 12L9 19"),
    Down("M5 9L12 16L19 9"),
    Up("M5 15L12 8L19 15"),
    Person("M16 6a4 4 0 1 1 -8 0a4 4 0 0 1 8 0M3 21c0 -5 3 -8 9 -8s9 3 9 8z"),
    Lock("M7 10V6a5 5 0 0 1 10 0v4M5 10h14a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V11a1 1 0 0 1 1 -1"),
    Logout("M14 6V4a2 2 0 0 0 -2 -2H4a2 2 0 0 0 -2 2v16a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2v-2M10 12h13M19 8l4 4l-4 4"),
    Trash("M3 5h18M9 5V2h6v3M6 8l1 13h10l1 -13M10 9v10M14 9v10"),
    Link("M10 7l4 -4a5 5 0 0 1 7 7l-5 5a5 5 0 0 1 -7 -1M14 17l-4 4a5 5 0 0 1 -7 -7l5 -5a5 5 0 0 1 7 1"),
    Experiment("M9 2h6M10 2v8L3 21h18L14 10V2M7 15h10"),
    Contents("M8 5h14M8 12h14M8 19h14M2 5h1M2 12h1M2 19h1"),
    Keyboard("M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1H3a1 1 0 0 1 -1 -1V6a1 1 0 0 1 1 -1M6 9h1M11 9h1M16 9h1M6 13h1M11 13h1M16 13h1M7 16h10"),
    Appearance("M2 20L8 4L14 20M4 14h8M17 12c3 -3 6 -1 6 1v7M23 15c-10 -1 -8 8 0 3"),
    Settings("M9 2h6l1 4l4 1l3 5l-3 5l-4 1l-1 4H9l-1 -4l-4 -1l-3 -5l3 -5l4 -1zM16 12a4 4 0 1 1 -8 0a4 4 0 0 1 8 0"),
    Play("M6 3L21 12L6 21z"),
    Pause("M8 4v16M16 4v16"),
    Check("M4 12L10 18L21 5")
}

@Composable
fun JournalIcon(symbol: JournalSymbol, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val path = androidx.compose.runtime.remember(symbol) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(symbol.path).toPath() }
    Canvas(modifier.size(20.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
