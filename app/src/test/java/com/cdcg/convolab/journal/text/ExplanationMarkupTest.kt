package com.cdcg.convolab.journal.text

import org.junit.Assert.*
import org.junit.Test

class ExplanationMarkupTest {
    @Test fun emphasisMatchesIosForOneTwoAndThreeStars() {
        assertEquals(listOf(ExplanationRun("plain "), ExplanationRun("italic", italic = true),
            ExplanationRun(" "), ExplanationRun("bold", bold = true), ExplanationRun(" "),
            ExplanationRun("both", bold = true, italic = true)),
            ExplanationMarkup.parse("plain *italic* **bold** ***both***"))
    }

    @Test fun rubyIsRetainedInsideEmphasisForTheReadingRenderer() {
        val run = ExplanationMarkup.parse("**映画[えいが]**").single()
        assertTrue(run.bold)
        assertEquals(listOf(RubyToken("映画", "えいが")), RubyDocument.parse(run.text))
    }

    @Test fun unsupportedMarkersLinksAndHtmlRemainLiteral() {
        val value = "****literal**** *unfinished [link](https://example.invalid) <b>text</b>"
        assertEquals(listOf(ExplanationRun(value)), ExplanationMarkup.parse(value))
    }

    @Test fun japaneseAndParagraphBreaksSurviveFormatting() {
        val value = "Use **の**.\n\n***party*** means パーティー。"
        assertEquals("Use の.\n\nparty means パーティー。", ExplanationMarkup.parse(value).joinToString("") { it.text })
    }
}
