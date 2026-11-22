package com.cdcg.convolab.journal.text

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.typing.TestOutput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.journal.appearance.*
import com.cdcg.convolab.ui.ConvoLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RubyLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun readingsStayCloseWithoutTouchingKanjiAcrossFontsAndSizes() {
        var typography by mutableStateOf(JournalTypography())
        var scale by mutableFloatStateOf(1f)
        var em = 0f
        compose.setContent {
            ConvoLabTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    em = with(LocalDensity.current) { typography.size.sp.toPx() }
                    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(24.dp)) {
                        RubyText("映画[えいが]を見ました。", Modifier.testTag("ruby-sample"),
                            RubyAppearance(typography.size, typography.family, color = Color.Black, readingColor = Color.Red))
                    }
                }
            }
        }
        for (font in JournalFont.entries) for (size in listOf(16, 20, 30)) for (fontScale in listOf(1f, 1.6f)) {
            compose.runOnIdle { typography = JournalTypography(font, size); scale = fontScale }
            assertGap(compose.onNodeWithTag("ruby-sample").captureToImage().toPixelMap(), em, "$font $size at $fontScale")
            assertBaselineAlignment()
        }
    }

    @Test fun wrappedHighlightsStayOnTheBaseTextAndKnownKanjiHideReadings() {
        var known by mutableStateOf(emptySet<Int>())
        var clicked = false
        compose.setContent {
            ConvoLabTheme {
                CompositionLocalProvider(LocalRubyOnPaper provides true) {
                    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(24.dp)) {
                        RubyParagraph(listOf(RubyRun("映画[えいが]を見[み]ました。".repeat(4), Color.Yellow,
                            underline = RubyUnderline(Color.Blue, dashed = true), onClick = { clicked = true })),
                            Modifier.width(180.dp).testTag("ruby-sample"), RubyAppearance(knownKanji = known, color = Color.Black, readingColor = Color.Red))
                    }
                }
            }
        }
        val sample = compose.onNodeWithTag("ruby-sample")
        val layouts = mutableListOf<TextLayoutResult>()
        sample.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Exercise highlights across wrapped lines", layouts.single().lineCount > 1)
        val pixels = sample.captureToImage().toPixelMap()
        val readings = coloredRows(pixels) { it.isReading() }
        val highlights = coloredRows(pixels) { it.isHighlight() }
        assertTrue("Readings must be visible", readings.isNotEmpty())
        assertTrue("Highlights must remain visible after layout", highlights.isNotEmpty())
        assertTrue("Highlights must not fill the readings' rows", readings.intersect(highlights).isEmpty())
        compose.onAllNodesWithText("映画", useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        assertTrue("Inline correction remains tappable", clicked)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(device.takeScreenshot(TestOutput.file("ruby-wrapped-highlights.png")))
        compose.runOnIdle { known = setOf('映'.code, '画'.code, '見'.code) }
        compose.onAllNodesWithText("えいが", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("み", useUnmergedTree = true).assertCountEquals(0)
        assertTrue(coloredRows(sample.captureToImage().toPixelMap()) { it.isReading() }.isEmpty())
    }

    private fun assertGap(pixels: PixelMap, em: Float, description: String) {
        val rubyRows = coloredRows(pixels) { it.isReading() }
        val baseRows = coloredRows(pixels) { it.isBase() }
        assertTrue("Readings must render for $description", rubyRows.isNotEmpty())
        assertTrue("Kanji must render for $description", baseRows.isNotEmpty())
        val gap = baseRows.min() - rubyRows.max() - 1
        assertTrue("Readings overlap kanji: $description ($gap px)", gap >= 0)
        assertTrue("Readings float above kanji: $description ($gap px)", gap <= em * .18f)
    }

    private fun assertBaselineAlignment() {
        val parent = compose.onNodeWithTag("ruby-sample")
        val kanji = compose.onNodeWithText("映画", useUnmergedTree = true)
        assertEquals("Kanji and adjacent kana must share a baseline", baseline(parent), baseline(kanji), 2f)
    }

    private fun baseline(node: SemanticsNodeInteraction): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().didOverflowHeight)
        return node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
    }

    private fun coloredRows(pixels: PixelMap, matches: (Color) -> Boolean): Set<Int> = buildSet {
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (matches(pixels[x, y])) add(y)
        }
    }

    private fun Color.isReading() = red > .6f && green < .3f && blue < .3f
    private fun Color.isHighlight() = red > .8f && green > .8f && blue < .2f
    private fun Color.isBase() = red < .3f && green < .3f && blue < .3f
}
