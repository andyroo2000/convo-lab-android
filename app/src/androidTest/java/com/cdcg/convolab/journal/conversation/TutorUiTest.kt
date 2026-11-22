package com.cdcg.convolab.journal.conversation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.ui.AccountMenu
import com.cdcg.convolab.ui.LocalTutorPicker
import androidx.compose.runtime.CompositionLocalProvider
import com.cdcg.convolab.ui.JournalBrand
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TutorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun close() { scope.cancel() }

    @Test fun translationAndTutorChoiceKeepHistoricalPortrait() {
        val session = Session("fixture-token", Account(101, "Naomi", "naomi@example.invalid"))
        val controller = TutorController(TutorApi(JournalApi(TutorTransport(), session) { session }), scope)
        compose.setContent {
            val state by controller.state.collectAsState()
            ConvoLabTheme {
                Column(Modifier.fillMaxSize().background(JournalBrand.Paper).safeDrawingPadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp)) {
                    CompositionLocalProvider(LocalTutorPicker provides { controller.showPicker() }) { AccountMenu("Naomi", {}, null) }
                    TutorCard(TutorPresentation(state, controller))
                }
            }
        }
        compose.runOnIdle { controller.open(QuestionSource(JournalId(JournalIds.newId()), JournalId(JournalIds.newId()))) }
        compose.waitUntil(5_000) { controller.state.value.question != null && !controller.state.value.busy }
        compose.onNodeWithContentDescription("Hana, your tutor").assertIsDisplayed()
        compose.onNodeWithText("What did you do today?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show English").performClick()
        compose.onNodeWithText("What did you do today?").assertIsDisplayed()
        capture("tutor-question.png")
        compose.onNodeWithContentDescription("Account menu").performClick()
        compose.onNodeWithText("Choose your tutor").performClick()
        compose.waitUntil(5_000) { controller.state.value.preferences != null && !controller.state.value.busy }
        compose.onNodeWithContentDescription("Hana").assertIsDisplayed()
        capture("tutor-picker.png")
        compose.onNodeWithContentDescription("Ren").performClick()
        compose.waitUntil(5_000) { !controller.state.value.choosing && !controller.state.value.busy }
        assertEquals("ren", controller.state.value.preferences?.selectedId)
        compose.onNodeWithContentDescription("Hana, your tutor").assertIsDisplayed()
        capture("tutor-preference-changed.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        assertTrue(device.takeScreenshot(TestOutput.file(name)))
    }
}
