package com.cdcg.convolab.journal.input

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class RomajiTest {
    @Test fun everyMappingPrefixAndTypingFixtureMatchesTheIosComposer() {
        val resource = checkNotNull(javaClass.getResourceAsStream("/typing/romaji-golden.tsv"))
        var checked = 0
        resource.bufferedReader().useLines { lines -> lines.forEach { line ->
            val parts = line.split('\t', limit = 3)
            val raw = decode(parts[0])
            val final = parts[1].toBooleanStrict()
            assertEquals("$raw (final=$final)", decode(parts[2]), Romaji.hiragana(raw, final))
            checked++
        } }
        assertEquals(1538, checked)
    }
    @Test fun partialSyllablesAndExplicitNRemainEditable() {
        assertEquals("k", Romaji.hiragana("k"))
        assertEquals("きゃ", Romaji.hiragana("kya"))
        assertEquals("ほn", Romaji.hiragana("hon"))
        assertEquals("ほん", Romaji.hiragana("hon", final = true))
        assertEquals("ん", Romaji.hiragana("nn"))
        assertEquals("ん", Romaji.hiragana("NN"))
        assertEquals("ほん", Romaji.hiragana("hoNN"))
        assertEquals("んな", Romaji.hiragana("nna"))
        assertEquals("しんよう", Romaji.hiragana("shin'you"))
        assertEquals("がっこう", Romaji.hiragana("gakkou"))
        assertEquals("カタカナ日本😊", Romaji.katakana("かたかな日本😊"))
    }
    private fun decode(encoded: String) = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
}
