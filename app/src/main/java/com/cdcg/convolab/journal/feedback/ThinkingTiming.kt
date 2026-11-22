package com.cdcg.convolab.journal.feedback

internal data class ThinkingFrame(val turn: Int, val opacity: Float)

internal object ThinkingTiming {
    fun frame(elapsed: Float, slot: Int): ThinkingFrame {
        val first = (slot + 1) * 3f
        val later = (elapsed - first).coerceAtLeast(0f)
        val turn = if (elapsed < first) 0 else 1 + (later / 9f).toInt()
        val progress = if (turn == 0) elapsed / first else (later % 9f) / 9f
        val fade = minOf(progress / .15f, (1f - progress) / .15f).coerceIn(0f, 1f)
        return ThinkingFrame(turn, fade * fade * (3f - 2f * fade))
    }

    fun dot(elapsed: Float, index: Int): Float {
        val phase = elapsed % 2.85f / 2.85f
        val fadeIn = ((phase - (.03f + index * .19f)) / .06f).coerceIn(0f, 1f)
        val fadeOut = ((1f - phase) / .22f).coerceIn(0f, 1f)
        return .15f + .65f * minOf(fadeIn, fadeOut)
    }
}
