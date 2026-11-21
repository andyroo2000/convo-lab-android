package com.cdcg.convolab.journal.text

import org.junit.Assert.*
import org.junit.Test

class RubyDocumentTest {
    @Test fun parsesParticlesOkuriganaAndBothReadingStylesWithoutChangingPlainText() {
        val tokens = RubyDocument.parse("映画[えいが]を見た(みた)。\nお茶[おちゃ]です。")
        assertEquals("映画を見た。\nお茶です。", tokens.joinToString("") { it.text })
        assertEquals(listOf(RubyToken("映画", "えいが"), RubyToken("見", "み"), RubyToken("茶", "ちゃ")), tokens.filter { it.reading != null })
    }

    @Test fun hidesOnlyWordsWhoseEveryKanjiIsKnown() {
        val tokens = RubyDocument.parse("映画[えいが]と日本[にほん]", setOf('映'.code, '画'.code, '日'.code))
        assertEquals(listOf(RubyToken("日本", "にほん")), tokens.filter { it.reading != null })
    }

    @Test fun literalBracketsAndSupplementaryCharactersRemainIntact() {
        val value = "Android[beta] 😀\n日記[not a reading]"
        assertEquals(value, RubyDocument.parse(value).joinToString("") { it.text })
        assertEquals("𠮷", RubyDocument.parse("𠮷[よし]").single().text)
    }
}
