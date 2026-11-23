package com.cdcg.convolab.journal.text

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.ui.ConvoLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RubyCorrectionInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun adjacentCorrectionsAndKanaOnlyCorrectionsOpenTheirOwnExplanation() {
        val taps = mutableListOf<Int>()
        var width by mutableStateOf(210.dp)
        compose.setContent {
            ConvoLabTheme {
                CompositionLocalProvider(LocalRubyOnPaper provides true) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        RubyParagraph(listOf(
                            RubyRun("わたしはげんきです。".repeat(4)),
                            RubyRun("日曜[にちよう]い", Color.Red, onClick = { taps += 0 }),
                            RubyRun("そのこと", Color.Red, onClick = { taps += 1 }),
                            RubyRun("。人[ひと]が"),
                            RubyRun("たくさん", Color.Red, onClick = { taps += 3 }),
                            RubyRun("来[き]た。" + "ひとがたくさんいました。".repeat(10))), Modifier.width(width).testTag("adjacent-corrections"), RubyAppearance(size = 28, knownKanji = setOf('人'.code, '来'.code)))
                    }
                }
            }
        }
        for (size in listOf(336, 350)) {
            compose.runOnIdle { width = size.dp }
            assertAccessibleCorrections(taps)
            assertCorrectionCharacters(taps)
        }
    }

    private fun assertAccessibleCorrections(taps: List<Int>) {
        for ((label, expected) in listOf("日曜い" to 0, "そのこと" to 1, "たくさん" to 3)) {
            compose.onAllNodesWithContentDescription("Show correction for $label").onFirst()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertEquals("Accessible actions open their own correction", expected, taps.last())
        }
    }

    private fun assertCorrectionCharacters(taps: List<Int>) {
        val sample = compose.onNodeWithTag("adjacent-corrections")
        val results = mutableListOf<TextLayoutResult>()
        sample.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        val ranges = layout.layoutInput.text.getStringAnnotations("ruby-run", 0, layout.layoutInput.text.length)
        for (run in listOf(1, 2, 4)) {
            val range = ranges.single { it.item == run.toString() }
            for (offset in range.start until range.end) {
                val box = layout.getBoundingBox(offset)
                val baseline = layout.getLineBaseline(layout.getLineForOffset(offset))
                val before = taps.size
                sample.performTouchInput { click(Offset(box.center.x, baseline - 30)) }
                assertEquals("Run $run at offset $offset must receive one tap", before + 1, taps.size)
                assertEquals("Adjacent corrections must not steal taps", run - 1, taps.last())
            }
        }
    }

    @Test fun everyWrappedHighlightLineMatchesItsTextAndRespondsToTaps() {
        var known by mutableStateOf(emptySet<Int>())
        var taps = 0
        compose.setContent {
            ConvoLabTheme {
                CompositionLocalProvider(LocalRubyOnPaper provides true) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                        RubyParagraph(listOf(
                            RubyRun("映画[えいが]を見[み]ました。いつもテレビを見[み]ながら笑[わら]う。"),
                            RubyRun("もっとたくさん美味[おい]しいものを食[た]べる。楽[たの]しい時間[じかん]でした。", Color.Yellow, onClick = { taps++ }),
                            RubyRun("また来週[らいしゅう]です。")), Modifier.width(210.dp).testTag("correction-sample"),
                            RubyAppearance(knownKanji = known))
                    }
                }
            }
        }
        for (hideReadings in listOf(false, true)) {
            compose.runOnIdle { if (hideReadings) known = "映画見笑美味食楽時間来週".map { it.code }.toSet() }
            val sample = compose.onNodeWithTag("correction-sample")
            val results = mutableListOf<TextLayoutResult>()
            sample.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            val range = layout.layoutInput.text.getStringAnnotations("ruby-run", 0, layout.layoutInput.text.length).single { it.item == "1" }
            val highlights = rubyHighlightLayout(layout).lines.filter { it.run == 1 }
            assertTrue("Exercise three wrapped correction lines", highlights.size >= 3)
            highlights.forEachIndexed { index, highlight ->
                val line = layout.getLineForOffset(range.start) + index
                val start = maxOf(range.start, layout.getLineStart(line))
                val end = minOf(range.end, layout.getLineEnd(line, visibleEnd = true))
                val first = layout.getBoundingBox(start)
                val last = layout.getBoundingBox(end - 1)
                assertEquals("Highlight must not include preceding plain text", first.left, highlight.rect.left, 1f)
                assertEquals("Highlight must not include following plain text", last.right, highlight.rect.right, 1f)
                val before = taps
                val offset = (start + end - 1) / 2
                val box = layout.getBoundingBox(offset)
                sample.performTouchInput { click(Offset(box.center.x, highlight.baseline - 5)) }
                assertEquals("Every highlighted line must open the correction", before + 1, taps)
            }
        }
    }
}
