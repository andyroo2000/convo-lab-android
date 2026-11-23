package com.cdcg.convolab.journal.input

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.journal.appearance.JournalTypography
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.ui.JournalBrand
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class JournalCursorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cursorTracksTheTextSizeInsteadOfTheNotebookLinePitch() {
        var text by mutableStateOf("")
        var size by mutableIntStateOf(20)
        compose.setContent {
            ConvoLabTheme {
                JournalWritingField(text, { text = it }, WritingOptions("Journal entry", style = JournalTypography(size = size).style),
                    Modifier.fillMaxWidth().height(180.dp).testTag("cursor-field"))
            }
        }
        compose.onNodeWithTag("cursor-field").performClick().assertIsFocused()
        compose.mainClock.autoAdvance = false
        assertCursorHeight(size)
        compose.onNodeWithTag("cursor-field").performTextInput("日本語\n次の行")
        compose.onNodeWithTag("cursor-field").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, androidx.compose.ui.text.TextRange("日本語\n次の行".length)))
        assertCursorHeight(size)
        compose.onNodeWithTag("cursor-field").performTextInputSelection(androidx.compose.ui.text.TextRange(0))
        assertCursorHeight(size)
        compose.runOnIdle { size = 30 }
        assertCursorHeight(size)
        compose.onNodeWithTag("cursor-field").performTextReplacement("折り返しとスクロールの確認です。".repeat(30))
        assertCursorHeight(size)
        compose.mainClock.autoAdvance = true
    }

    private fun assertCursorHeight(size: Int) {
        val expected = with(compose.density) { size.sp.toPx() }
        val height = visibleCursorHeight()
        assertTrue("Cursor height $height should follow text size $expected", height in (expected * .8f)..(expected * 1.3f))
    }

    private fun visibleCursorHeight(): Float {
        repeat(12) {
            compose.mainClock.advanceTimeBy(100)
            val pixels = compose.onNodeWithTag("cursor-field").captureToImage().toPixelMap()
            val rows = (0 until pixels.height).filter { y -> (0 until pixels.width).any { x ->
                val color = pixels[x, y]
                abs(color.red - JournalBrand.Blue.red) < .02f && abs(color.green - JournalBrand.Blue.green) < .02f &&
                    abs(color.blue - JournalBrand.Blue.blue) < .02f
            } }
            if (rows.isNotEmpty()) return (rows.last() - rows.first() + 1).toFloat()
        }
        error("No visible cursor appeared during a full blink cycle")
    }
}
