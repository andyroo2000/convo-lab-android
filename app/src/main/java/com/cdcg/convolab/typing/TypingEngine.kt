package com.cdcg.convolab.typing

data class Conversion(val reading: String, val candidates: List<String>, val engineMs: Double)

interface TypingEngine {
    suspend fun prepare()
    suspend fun convert(text: String): Conversion
    suspend fun close()
}

object TypingLimits {
    const val MAX_INPUT = 120
    fun accepts(text: String): Boolean = text.length <= MAX_INPUT && text.none { it.isISOControl() }
}
