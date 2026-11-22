package com.cdcg.convolab.journal.feedback

import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.audio.LocalJournalAudio
import com.cdcg.convolab.network.ApiFailure
import com.cdcg.convolab.journal.appearance.JournalTypography
import com.cdcg.convolab.journal.appearance.LocalJournalTypography
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.AccountMenu
import com.cdcg.convolab.ui.ConvoLabTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val entry = JournalEntry(JournalIds.newId(), "A movie night", "映画を見た。", "2026-11-12", 1, "created", "updated")
    private val api = UiFeedbackGateway()
    private lateinit var workflow: WorkflowController
    private lateinit var notebook: NotebookController

    @After fun stop() { scope.cancel() }

    @Test fun missingCachedEntryCanBeRecoveredAsANewLocalDraft() {
        val cached = runBlocking { api.analysis(entry).analysis }
        api.missingEntry = true
        launchNotebook(submit = false, saved = WorkflowSnapshot(JournalId(entry.id), entry.version, analysis = cached))
        compose.onNodeWithText("404 · Entry Not Found").assertIsDisplayed()
        screenshot("feedback-missing-entry")
        compose.onNodeWithText("Save as a new entry").performScrollTo().performClick()
        compose.waitUntil(5_000) { notebook.state.value.draft.id != entry.id }
        assertEquals(entry.body, notebook.state.value.draft.body)
        assertEquals(0, notebook.state.value.draft.expectedVersion)
        assertTrue(notebook.state.value.records.any { it.entry == entry })
        assertEquals(cached, workflow.state.value.snapshot?.analysis)
    }

    @Test fun missingEntryKeepsCachedWritingAndOffersRecoveryAtLargeText() {
        val cached = runBlocking { api.analysis(entry).analysis }
        api.missingEntry = true
        launchNotebook(submit = false, saved = WorkflowSnapshot(JournalId(entry.id), entry.version, analysis = cached), scale = 2f)
        compose.onNodeWithText("404 · Entry Not Found").assertIsDisplayed()
        compose.onNodeWithText("The service is unavailable. Please try again.").assertDoesNotExist()
        compose.onNodeWithText("Save as a new entry").performScrollTo().assertIsDisplayed()
        screenshot("feedback-missing-entry-large-text")
        compose.onNodeWithText("Copy saved writing").performScrollTo().performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        compose.runOnIdle { assertEquals(entry.body, clipboard.primaryClip?.getItemAt(0)?.text.toString()) }
        assertEquals(cached, workflow.state.value.snapshot?.analysis)
        assertFalse(workflow.state.value.readyForConversation)
        compose.onNodeWithText("Back to Journal").performScrollTo().performClick()
        compose.onAllNodesWithText("Contents", substring = false).assertCountEquals(2)
    }

    @Test fun customClarificationAnswerStaysReachableAboveTheKeyboard() {
        launchNotebook(submit = true)
        compose.onNodeWithText("Who was driving?").assertIsDisplayed()
        screenshot("feedback-clarification")
        compose.onNodeWithText("Something else").performScrollTo().performClick()
        compose.onNodeWithTag("clarification-answer").performScrollTo().performClick().performTextInput("My friend was driving.")
        compose.onNodeWithTag("clarification-send").performScrollTo()
        assertAboveKeyboard("clarification-send")
        screenshot("feedback-clarification-keyboard")
        compose.onNodeWithTag("clarification-send").performClick()
        compose.waitUntil(5_000) { workflow.state.value.snapshot?.analysis?.status == WorkStatus.Ready }
        assertEquals("My friend was driving.", api.answerText)
        device.pressBack()
        compose.onNodeWithText("Final").performScrollTo().assertIsDisplayed()
    }

    @Test fun finalEditorPreservesLongTextAndSaveAboveKeyboard() {
        launchNotebook(submit = false)
        screenshot("feedback-draft")
        compose.onNodeWithText("Final").performClick()
        screenshot("feedback-final")
        compose.onNodeWithContentDescription("More journal options").performScrollTo().performClick()
        compose.onNodeWithText("Edit final").performClick()
        screenshot("feedback-final-editor")
        compose.onNodeWithText("Draft").assertIsNotEnabled()
        compose.onNodeWithText("Contents").assertIsNotEnabled()
        val text = "友達と映画を見ました。\n".repeat(18)
        compose.onNodeWithTag("final-editor").performClick().performTextReplacement(text)
        assertAboveKeyboard("final-save")
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        val heading = compose.onNodeWithText("Draft").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("Editor heading must remain on screen", heading.top >= 0)
        screenshot("feedback-final-editor-keyboard")
        compose.onNodeWithTag("final-save").performClick()
        compose.waitUntil(5_000) { workflow.state.value.finalEdit == null }
        assertEquals(text, api.editedText)
        assertEquals(entry.body, workflow.state.value.entry?.body)
        assertEquals(text, workflow.state.value.snapshot?.analysis?.finalText)
    }

    @Test fun finalEditorSupportsDoubleTextSizeWithKeyboardAndLongText() {
        launchNotebook(submit = false, scale = 2f)
        compose.onNodeWithText("Final").performClick()
        compose.onNodeWithContentDescription("More journal options").performScrollTo().performClick()
        compose.onNodeWithText("Edit final").performClick()
        compose.onAllNodesWithTag("final-editor").assertCountEquals(1)
        compose.onNodeWithTag("final-editor").performClick().performTextReplacement("友達と映画を見ました。\n".repeat(18))
        assertAboveKeyboard("final-save")
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        val editor = compose.onNodeWithTag("final-editor").fetchSemanticsNode().boundsInWindow
        assertTrue("Large type must retain visible writing space", editor.height > 40)
        screenshot("feedback-final-editor-double-text")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { workflow.state.value.finalEdit == null }
    }

    @Test fun earlierPendingTextRequiresConfirmationBeforeArchiveAndContinue() {
        val pending = WorkflowSnapshot(JournalId(entry.id), entry.version + 1,
            pendingFinalEdit = FinalEdit(JournalIds.newId(), 3, "残しておく修正。"))
        launchNotebook(submit = false, saved = pending)
        compose.onNodeWithText("残しておく修正。").assertIsDisplayed()
        screenshot("feedback-recovery")
        compose.onNodeWithText("Archive earlier work and continue").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(workflow.state.value.recovery)
        compose.onNodeWithText("Archive earlier work and continue").performScrollTo().performClick()
        compose.onNodeWithText("Archive and continue").performClick()
        compose.waitUntil(5_000) { workflow.state.value.recovery == null && !workflow.state.value.busy }
        compose.onNodeWithText("Final").performScrollTo().assertIsDisplayed()
    }

    @Test fun generatedTitleCompletesSubmissionAfterIllustratedWaiting() {
        api.finishFeedback = CompletableDeferred()
        launchNotebook(submit = true)
        compose.onNodeWithText("I did").performClick()
        compose.onNodeWithTag("clarification-send").performScrollTo().performClick()
        compose.onNodeWithTag("journal-thinking").assertIsDisplayed()
        compose.onNodeWithText("Hide English").performScrollTo().performClick()
        compose.onNodeWithText("I did").performScrollTo().assertIsDisplayed()
        screenshot("feedback-illustrated-waiting")
        compose.runOnIdle { api.finishFeedback!!.complete(Unit) }
        compose.waitUntil(5_000) { workflow.state.value.snapshot?.analysis?.status == WorkStatus.Ready }
        assertNull(workflow.state.value.error)
        compose.onNodeWithTag("journal-thinking").assertDoesNotExist()
        compose.onNodeWithText("Final").performScrollTo().performClick()
        screenshot("feedback-generated-title-final")
        assertEquals("映画を見ました。", workflow.state.value.snapshot?.analysis?.finalText)
    }

    @Test fun waitingAtDoubleTextSizeKeepsTheAnswerAndLanguageControlReachable() {
        api.finishFeedback = CompletableDeferred()
        launchNotebook(submit = true, scale = 2f)
        compose.onNodeWithText("I did").performScrollTo().performClick()
        compose.onNodeWithTag("clarification-send").performScrollTo().performClick()
        compose.onNodeWithText("Hide English").performScrollTo().performClick()
        compose.onNodeWithText("I did").performScrollTo().assertIsDisplayed()
        screenshot("feedback-waiting-double-text")
        compose.runOnIdle { api.finishFeedback!!.complete(Unit) }
        compose.waitUntil(5_000) { !workflow.state.value.busy }
        assertNull(workflow.state.value.error)
    }

    private fun launchNotebook(submit: Boolean, saved: WorkflowSnapshot? = null, scale: Float = 1f) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Match MainActivity: older Android does not force edge-to-edge on test hosts.
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val audio = feedbackAudioFixture(InstrumentationRegistry.getInstrumentation().targetContext, scope)
        val storage = FeedbackNotebookStorage(JournalRecord(entry, JournalDraft.from(entry)))
        notebook = NotebookController(JournalRepository(FeedbackNotebookGateway(), storage), scope)
        workflow = WorkflowController(WorkflowAccess(api, UiWorkflowStorage(saved)), scope)
        compose.setContent {
            val state by notebook.state.collectAsState()
            val feedback by workflow.state.collectAsState()
            val density = LocalDensity.current
            val arguments = InstrumentationRegistry.getArguments()
            val typography = JournalTypography.restored(arguments.getString("journalFont"), arguments.getString("journalSize")?.toIntOrNull() ?: 20)
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalJournalTypography provides typography, LocalJournalAudio provides audio) {
                ConvoLabTheme { NotebookScreen(state, notebook, NotebookPanels(FeedbackPresentation(feedback, workflow))) { AccountMenu("Naomi", {}, null) } }
            }
        }
        compose.runOnIdle { notebook.start(); if (submit) workflow.submit(entry) else workflow.open(entry) }
        compose.waitUntil(5_000) { !notebook.state.value.loading && !workflow.state.value.busy }
    }

    private fun assertAboveKeyboard(tag: String) {
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        compose.waitForIdle()
        if (tag == "clarification-send") compose.onNodeWithTag(tag).performScrollTo()
        val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val keyboardTop = compose.runOnIdle {
            val view = compose.activity.window.decorView
            view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        }
        assertTrue("Action is clipped or hidden by the keyboard", bounds.height > 0 && bounds.bottom <= keyboardTop + 1)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        device.waitForIdle()
        assertTrue(device.takeScreenshot(TestOutput.file("$name.png")))
    }
}

private class UiFeedbackGateway : FeedbackGateway {
    private val clarificationId = JournalIds.newId()
    private val questionId = JournalIds.newId()
    private val analysisId = JournalIds.newId()
    var finishFeedback: CompletableDeferred<Unit>? = null
    var missingEntry = false
    var answerText: String? = null
    var editedText: String? = null
    private fun clarification(status: WorkStatus) = Clarification(clarificationId, 1, 1, status,
        ClarificationQuestion(questionId, "Who was driving?", listOf("I did", "Molly did")), null, "updated", "{}")
    private fun analysis(): Analysis {
        val segment = FeedbackSegment("映画を見た。", "映画[えいが]を見[み]た。", SegmentKind.Optional,
            "映画を見ました。", "映画[えいが]を見[み]ました。", "Use the polite form when writing to your tutor.", null, null, null)
        val feedback = Feedback("A movie night", "映画を見ました。", "映画[えいが]を見[み]ました。", "", "", "", listOf(segment), null, null, null)
        return Analysis(analysisId, 1, WorkStatus.Ready, feedback, null,
            editedText?.let { CorrectedDocument(it, it) }, 0, null, "updated", "{}")
    }
    override suspend fun startClarification(entry: JournalEntry) = clarification(WorkStatus.Waiting)
    override suspend fun clarification(entry: JournalEntry) = clarification(WorkStatus.Waiting)
    override suspend fun answer(entry: JournalEntry, answer: ClarificationAnswer): Clarification { answerText = answer.text; return clarification(WorkStatus.Resolved) }
    override suspend fun submit(entry: JournalEntry, clarificationId: String) = analysis().let { if (finishFeedback == null) it else it.copy(status = WorkStatus.Queued) }
    override suspend fun analysis(entry: JournalEntry): AnalysisResponse {
        if (missingEntry) throw ApiFailure(404)
        finishFeedback?.await()
        return AnalysisResponse(if (finishFeedback == null) entry else entry.copy(title = "A generated title", version = entry.version + 1), analysis())
    }
    override suspend fun editFinal(entry: JournalEntry, edit: FinalEdit): Analysis { editedText = edit.text; return analysis() }
}

private class UiWorkflowStorage(private var record: WorkflowSnapshot? = null) : WorkflowStorage {
    override fun read(id: JournalId) = record?.takeIf { it.entryId == id }
    override fun archive(id: JournalId) { record = null }
    override fun write(snapshot: WorkflowSnapshot) { record = snapshot }
}

private class FeedbackNotebookStorage(record: JournalRecord) : JournalStorage {
    private val records = mutableMapOf(record.id to record)
    override fun load() = JournalLoad(records.values.toList(), emptyList())
    override fun read(id: String) = records[id]
    override fun write(record: JournalRecord) { records[record.id] = record }
    override fun remove(id: String) { records.remove(id) }
}

private class FeedbackNotebookGateway : JournalGateway {
    override suspend fun archive(cursor: String?) = JournalPage(emptyList(), null)
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("Unexpected fixture read")
    override suspend fun save(draft: JournalDraft) = JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, draft.expectedVersion + 1, "created", "updated")
    override suspend fun delete(id: String, version: Int) = Unit
}
