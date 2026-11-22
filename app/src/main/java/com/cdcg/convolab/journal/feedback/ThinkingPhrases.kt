package com.cdcg.convolab.journal.feedback

internal object ThinkingPhrases {
    private val phrases = listOf(
        "少し待ってくださいね。" to "Just a moment, please.",
        "ちょっと待ってくださいね。" to "Give me a moment, please.",
        "少しお待ちくださいね。" to "Please wait a moment.",
        "少し考えさせてくださいね。" to "Let me think for a moment.",
        "ちょっと考えますね。" to "Let me think for a second.",
        "少し時間をくださいね。" to "Please give me a little time.",
        "ちょっと見てみますね。" to "Let me take a look.",
        "少し読ませてくださいね。" to "Let me read through it for a moment.",
    )
    private var previous = -1
    @Synchronized fun next(): Pair<String, String> {
        val index = phrases.indices.filter { it != previous }.random()
        previous = index
        return phrases[index]
    }
}
