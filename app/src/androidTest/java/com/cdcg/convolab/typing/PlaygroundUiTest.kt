package com.cdcg.convolab.typing

import android.view.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.TypingLabActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaygroundUiTest {
    @get:Rule val compose = createAndroidComposeRule<TypingLabActivity>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun candidatesRemainAboveEnglishKeyboardInPortraitAndLandscape() {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.setOrientationNatural()
        try {
            awaitReady()
            compose.onNodeWithTag("notebook").performTextInput("今日は日本語を勉強しました。\n".repeat(30))
            compose.onNodeWithTag("romaji").performClick().performTextInput("nihongo")
            awaitCandidate()
            assertVisibleAboveKeyboard("portrait-keyboard")
            compose.onNodeWithTag("candidate-0").performClick()
            compose.onNodeWithTag("notebook").assertTextContains("日本語", substring = true)
            compose.onNodeWithTag("romaji").performTextInput("kinoueigawomimashita")
            awaitCandidate()
            device.setOrientationLeft()
            compose.waitForIdle()
            compose.onNodeWithTag("romaji").performClick()
            awaitCandidate()
            assertVisibleAboveKeyboard("landscape-keyboard")
            compose.onNodeWithTag("romaji").performTextInput("x".repeat(121))
            compose.waitForIdle()
            assertTrue(device.takeScreenshot(TestOutput.file("landscape-input-limit.png")))
            compose.onNodeWithText("Use one phrase of up to 120 characters.").assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test fun rapidEnglishInputKeepsTheEditorResponsive() {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        awaitReady()
        compose.onNodeWithTag("romaji").performClick()
        val edits = mutableListOf<Double>()
        for (letter in "nihongowobenkyoushiteimasu") {
            val start = System.nanoTime()
            compose.onNodeWithTag("romaji").performTextInput(letter.toString())
            edits += (System.nanoTime() - start) / 1e6
        }
        val lastEdit = System.nanoTime()
        awaitCandidate()
        compose.onNodeWithTag("reading").assertTextContains("にほんごをべんきょうしています")
        val summary = TimingSummary.of(edits)
        TestOutput.file("typing-ui-timing.json").writeText(
            org.json.JSONObject().put("editP95Ms", summary.p95)
                .put("lastEditToCandidateMs", (System.nanoTime() - lastEdit) / 1e6)
                .put("scope", "Includes Compose test synchronization; not a physical key-to-photon measurement.").toString(2)
        )
        assertTrue("Editor stalled for more than a second", summary.maximum < 1000)
    }

    private fun awaitReady() {
        compose.waitUntil(90_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Japanese typing lab")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitCandidate() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("candidate-0")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertVisibleAboveKeyboard(name: String) {
        compose.waitUntil(10_000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime())
        }
        compose.onNodeWithTag("candidate-0").assertIsDisplayed()
        compose.onNodeWithTag("romaji").assertIsDisplayed()
        val keyboardTop = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            decor.height - decor.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        }
        for (tag in listOf("candidates", "romaji")) {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
            assertTrue("$tag covered by keyboard: $bounds / $keyboardTop", bounds.bottom <= keyboardTop + 1)
        }
        assertTrue(device.takeScreenshot(TestOutput.file("$name.png")))
    }
}
