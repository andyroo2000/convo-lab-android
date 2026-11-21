package com.cdcg.convolab.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.typing.TestOutput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JournalVisualParityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun correctionIsAnchoredAndDismissesWithoutChangingText() {
        showCorrection(1f, "**見ました** is the ***polite*** past form.")
        openCorrection()
        assertInsideWindow()
        capture("parity-correction-popover.png")
        compose.onNodeWithText("Got it").assertDoesNotExist()
        compose.onNodeWithText("***", substring = true).assertDoesNotExist()
        device.pressBack()
        compose.onNodeWithTag("correction-popover").assertDoesNotExist()
        compose.onNodeWithText("映画を見た。", substring = true).assertExists()
    }

    @Test fun longCorrectionScrollsAtLargeTypeWithoutLeavingWindow() {
        showCorrection(1.6f, "This explanation remains readable at larger text sizes. ".repeat(35) + "End of explanation.")
        openCorrection()
        assertInsideWindow()
        capture("parity-correction-large.png")
        compose.onNodeWithTag("correction-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag("correction-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Long explanation must scroll to its end", scroll.maxValue() > 0f && scroll.value() == scroll.maxValue())
        assertInsideWindow()
        device.pressBack()
        compose.onNodeWithTag("correction-popover").assertDoesNotExist()
    }

    @Test fun accountMenuGroupsJournalPreferencesAndOpensPaperAppearance() {
        compose.setContent {
            ConvoLabTheme { Box(Modifier.fillMaxSize().background(JournalBrand.Green).safeDrawingPadding().padding(20.dp)) {
                CompositionLocalProvider(LocalTutorPicker provides {}) { AccountMenu("Naomi", {}, null, {}) }
            } }
        }
        compose.onNodeWithContentDescription("Account menu").performClick()
        compose.onNodeWithText("Tutor").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        capture("parity-account-menu.png")
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Journal appearance").assertIsDisplayed()
        compose.onNodeWithText("Klee One").assertIsDisplayed()
        capture("parity-appearance.png")
        compose.onNodeWithContentDescription("Close Journal appearance").performClick()
        compose.onNodeWithText("Journal appearance").assertDoesNotExist()
    }

    private fun showCorrection(scale: Float, explanation: String) {
        val segment = FeedbackSegment("映画を見た。", "映画を見た。", SegmentKind.Optional, null, null,
            explanation, null, "映画を見ました。", "映画[えいが]を見[み]ました。")
        compose.setContent {
            ConvoLabTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Column(Modifier.fillMaxSize().background(JournalStyle.Paper).safeDrawingPadding().padding(24.dp)) {
                        Spacer(Modifier.height(170.dp))
                        Box(Modifier.testTag("correction-anchor")) { CorrectionText(listOf(segment), "") }
                    }
                }
            }
        }
    }

    private fun openCorrection() {
        compose.onNodeWithText("映画を見た。", substring = true).performTouchInput { click(center) }
        compose.onNodeWithTag("correction-popover").assertIsDisplayed()
    }

    private fun assertInsideWindow() {
        val bounds = compose.onNodeWithTag("correction-popover").fetchSemanticsNode().boundsInWindow
        assertTrue(bounds.left >= 0 && bounds.top >= 0)
        assertTrue(bounds.right <= device.displayWidth && bounds.bottom <= device.displayHeight)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        device.waitForIdle()
        assertTrue(device.takeScreenshot(TestOutput.file(name)))
    }
}
