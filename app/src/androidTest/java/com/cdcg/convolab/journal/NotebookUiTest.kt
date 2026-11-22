package com.cdcg.convolab.journal

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.ui.AccountMenu
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NotebookUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val api = UiJournalGateway()
    private lateinit var controller: NotebookController

    @After fun stop() { scope.cancel() }

    @Test fun refreshingContentsRemovesARemoteDeletionAndKeepsLocalWriting() {
        launchNotebook()
        compose.onNodeWithTag("journal-editor").performClick().performTextInput("端末に残す文章。")
        device.pressBack()
        compose.onNodeWithText("Contents").performClick()
        compose.onNodeWithText("A movie night").assertIsDisplayed()
        compose.runOnIdle { api.entries = emptyList() }
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.waitUntil(5_000) { !controller.state.value.busy }
        compose.onNodeWithText("A movie night").assertDoesNotExist()
        compose.onNodeWithText("Untitled entry").assertIsDisplayed()
        screenshot("contents-after-remote-deletion")
        compose.runOnIdle { api.offline = true }
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.waitUntil(5_000) { !controller.state.value.busy }
        compose.onNodeWithText("A movie night").assertDoesNotExist()
        compose.onNodeWithText("Untitled entry").performClick()
        compose.onNodeWithTag("journal-editor").assertTextContains("端末に残す文章。")
    }

    @Test fun writingAndActionsRemainAboveKeyboardForLongEntries() {
        launchNotebook()
        screenshot("notebook-empty")
        compose.onNodeWithTag("journal-editor").performClick().performTextInput("今日は友達と映画を見ました。")
        assertActionsAboveKeyboard()
        screenshot("notebook-keyboard")
        compose.onNodeWithTag("journal-editor").performTextInput("\n次の文章です。".repeat(30))
        compose.onNodeWithTag("journal-save").assertIsDisplayed().assertIsEnabled()
        assertActionsAboveKeyboard()
        screenshot("notebook-long-keyboard")
        device.pressBack()
        compose.waitUntil(10_000) { !compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        compose.onNodeWithTag("journal-save").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        screenshot("notebook-long-keyboard-hidden")
    }

    @Test fun pendingSaveVisiblyDisablesEditingUntilAcknowledged() {
        launchNotebook()
        compose.onNodeWithTag("journal-editor").performClick().performTextInput("保存中の文章。")
        device.waitForIdle()
        compose.waitForIdle()
        api.response = CompletableDeferred()
        compose.onNodeWithTag("journal-save").performClick()
        compose.waitUntil(5_000) { controller.state.value.busy }
        compose.onNodeWithTag("journal-editor").assertIsNotEnabled()
        compose.onNodeWithTag("journal-save").assertIsNotEnabled()
        val draft = controller.state.value.draft
        api.response!!.complete(JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, 1, "created", "updated"))
        compose.waitUntil(5_000) { !controller.state.value.busy }
        compose.onNodeWithTag("journal-editor").assertIsEnabled().assertTextContains("保存中の文章。")
    }

    @Test fun contentsAndCloseKeepWritingAvailable() {
        launchNotebook()
        compose.onNodeWithTag("journal-editor").performClick().performTextInput("大切な日記です。")
        device.pressBack()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Keep this draft?").assertIsDisplayed()
        screenshot("notebook-close-draft")
        compose.onNodeWithContentDescription("Close Keep this draft?").performClick()
        compose.onNodeWithText("Contents").performClick()
        compose.onNodeWithText("A movie night").assertIsDisplayed()
        screenshot("notebook-contents")
        compose.onNodeWithText("A movie night").performClick()
        compose.onNodeWithTag("journal-editor").assertTextContains("昨日は映画を見ました。")
    }

    @Test fun closingASavedEntryRequiresExplicitDeletionConfirmation() {
        launchNotebook()
        compose.onNodeWithText("Contents").performClick()
        compose.onNodeWithText("A movie night").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Delete draft").performClick()
        compose.onNodeWithText("Delete this entry?").assertIsDisplayed()
        compose.onNodeWithText("Keep entry").performClick()
        compose.onNodeWithContentDescription("Close Keep this draft?").performClick()
        compose.onNodeWithTag("journal-editor").assertTextContains("昨日は映画を見ました。")
    }

    private fun launchNotebook() {
        val storage = UiJournalStorage()
        val id = JournalIds.newId()
        storage.records[id] = JournalRecord(entry = JournalEntry(id, "A movie night", "昨日は映画を見ました。",
            "2026-11-12", 1, "2026-11-12T12:00:00Z", "2026-11-12T12:00:00Z"))
        api.entries = listOf(requireNotNull(storage.records[id]?.entry))
        controller = NotebookController(JournalRepository(api, storage), scope)
        compose.setContent {
            val state by controller.state.collectAsState()
            ConvoLabTheme { NotebookScreen(state, controller) { AccountMenu("Naomi", {}, null) } }
        }
        compose.runOnIdle { controller.start() }
        compose.waitUntil(5_000) { !controller.state.value.loading }
    }

    private fun assertActionsAboveKeyboard() {
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        compose.waitForIdle()
        screenshot("notebook-keyboard-bounds")
        val keyboardTop = compose.runOnIdle {
            val view = compose.activity.window.decorView
            view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        }
        for (node in listOf(compose.onNodeWithTag("journal-save"), compose.onNodeWithText("Close"))) {
            val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertTrue("Journal action $bounds is hidden by keyboard starting at $keyboardTop", bounds.height > 0 && bounds.bottom <= keyboardTop + 1)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        device.waitForIdle()
        assertTrue(device.takeScreenshot(TestOutput.file("$name.png")))
    }
}

private class UiJournalStorage : JournalStorage {
    val records = mutableMapOf<String, JournalRecord>()
    override fun load() = JournalLoad(records.values.toList(), emptyList())
    override fun read(id: String) = records[id]
    override fun write(record: JournalRecord) { records[record.id] = record }
    override fun remove(id: String) { records.remove(id) }
}

private class UiJournalGateway : JournalGateway {
    var response: CompletableDeferred<JournalEntry>? = null
    var entries = emptyList<JournalEntry>()
    var offline = false
    override suspend fun archive(cursor: String?): JournalPage {
        if (offline) throw IOException("fixture offline")
        return JournalPage(entries, null)
    }
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("Unexpected fixture fetch")
    override suspend fun save(draft: JournalDraft) = response?.await() ?: JournalEntry(draft.id, draft.title, draft.body, draft.entryDate,
        draft.expectedVersion + 1, "created", "updated")
    override suspend fun delete(id: String, version: Int) = Unit
}
