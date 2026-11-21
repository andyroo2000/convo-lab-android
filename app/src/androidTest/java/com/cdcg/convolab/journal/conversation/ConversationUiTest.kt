package com.cdcg.convolab.journal.conversation

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.Session
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.journal.feedback.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConversationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val transport = ConversationFixture()
    private lateinit var directory: File
    private lateinit var conversation: ConversationController
    private lateinit var repository: JournalRepository
    @After fun stop() { scope.cancel() }

    @Test fun answerKeyboardAndNestedFeedbackSupportAnotherTurn() {
        launchNotebook()
        val first = composer()
        write(first, "アニメの映画を見ました。")
        compose.runOnIdle { current().refresh() }
        compose.waitUntil(5_000) { !current().state.value.busy }
        compose.onNodeWithTag("conversation-answer-${first.state.value.record.id}").assertIsFocused()
        capture(TestOutput.file("conversation-answer-keyboard.png"))
        submit(first)
        compose.waitUntil(10_000) { current().state.value.replies.size == 1 && current().state.value.composer != null }
        val next = composer()
        assertNotEquals(first.state.value.record.id, next.state.value.record.id)
        val reply = current().state.value.replies.single().controller
        compose.waitUntil(5_000) { reply.workflow.state.value.snapshot?.analysis?.status == WorkStatus.Ready }
        compose.onNodeWithText("Edit answer").performScrollTo().assertIsDisplayed()
        capture(TestOutput.file("conversation-reply-feedback.png"))
        write(next, "とても面白かったです。")
        capture(TestOutput.file("conversation-second-turn-keyboard.png"))
        assertEquals(reply.state.value.record.id, next.state.value.record.answerOrigin?.parentId?.value)
        runBlocking { current().flush() }
        val reopened = JournalDiskStore(directory, 101).read(next.state.value.record.id)
        assertEquals("とても面白かったです。", reopened?.draft?.body)
        assertEquals(next.state.value.record.answerOrigin, reopened?.answerOrigin)
    }

    @Test fun lostResponseRetriesTheSameAnswerWithoutDuplicateChildren() {
        launchNotebook()
        val first = composer()
        write(first, "一度だけ保存してください。")
        transport.loseNextAnswer = true
        submit(first)
        compose.waitUntil(5_000) { !first.state.value.busy && first.state.value.record.pendingSave != null }
        compose.onNodeWithTag("conversation-answer-${first.state.value.record.id}").assertIsNotEnabled()
        capture(TestOutput.file("conversation-answer-retry.png"))
        submit(first)
        compose.waitUntil(10_000) { current().state.value.replies.size == 1 && current().state.value.composer != null }
        assertEquals(2, transport.sent.size)
        assertEquals(transport.sent[0], transport.sent[1])
        assertEquals(1, transport.children.size)
    }

    @Test fun replyFinalEditorKeepsItsKeyboardActionsVisibleAndConversationMounted() {
        launchNotebook()
        val first = composer()
        write(first, "映画を見ました。")
        submit(first)
        compose.waitUntil(10_000) { current().state.value.replies.size == 1 && current().state.value.composer != null }
        val nextId = composer().state.value.record.id
        val reply = current().state.value.replies.single().controller
        compose.waitUntil(5_000) { reply.workflow.state.value.snapshot?.analysis?.status == WorkStatus.Ready }
        compose.runOnIdle { reply.workflow.beginFinalEdit() }
        compose.onNodeWithTag("final-editor").performClick().performTextReplacement("友達と映画を見ました。\n".repeat(12))
        device.waitForIdle()
        compose.onNodeWithTag("final-save").assertIsDisplayed()
        capture(TestOutput.file("conversation-final-editor-keyboard.png"))
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { reply.workflow.state.value.finalEdit == null }
        assertEquals(nextId, composer().state.value.record.id)
        assertEquals(1, current().state.value.replies.size)
    }

    @Test fun discardRequiresConfirmationAndKeepsTheOriginalOnCancel() {
        launchNotebook()
        val first = composer()
        write(first, "残したい答え。")
        device.pressBack()
        compose.waitUntil(10_000) { !compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        compose.onNodeWithText("Discard unsent answer").performScrollTo().performClick()
        compose.onNodeWithText("Keep writing").performClick()
        assertEquals("残したい答え。", first.state.value.text)
        compose.onNodeWithText("Discard unsent answer").performClick()
        compose.onNodeWithText("Discard answer", substring = false).performClick()
        compose.waitUntil(5_000) { current().state.value.composer?.controller?.state?.value?.record?.id != first.state.value.record.id }
        assertNull(JournalDiskStore(directory, 101).read(first.state.value.record.id))
    }

    private fun launchNotebook() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "conversation-ui-${UUID.randomUUID()}")
        val session = Session("fixture-token", Account(101, "Naomi", "naomi@example.invalid"))
        val api = JournalApi(transport, session) { session }
        repository = JournalRepository(api, JournalDiskStore(directory, 101))
        val workflows = WorkflowAccess(FeedbackApi(api), WorkflowDiskStore(directory, 101))
        val root = WorkflowController(workflows, scope)
        val tutor = TutorController(TutorApi(api), scope)
        conversation = ConversationController(ConversationAccess(ConversationApi(api), repository, { WorkflowController(workflows, it) }, tutor))
        val notebook = NotebookController(repository, scope, conversation::beforeDelete)
        compose.setContent {
            val notebookState by notebook.state.collectAsState()
            val feedback by root.state.collectAsState()
            val teacher by tutor.state.collectAsState()
            val active by conversation.session.collectAsState()
            val replies = active?.state?.collectAsState()?.value
            ConvoLabTheme { NotebookScreen(notebookState, notebook, NotebookPanels(FeedbackPresentation(feedback, root), TutorPresentation(teacher, tutor),
                replies?.let { ConversationPresentation(it, active!!) })) {} }
        }
        compose.runOnIdle {
            scope.launch {
                repository.accept(transport.root)
                notebook.start().join()
                notebook.select(requireNotNull(repository.record(JournalId(transport.root.id)))).join()
                launch { conversation.follow(root) }
                root.open(transport.root)
            }
        }
        compose.waitUntil(10_000) { conversation.session.value?.state?.value?.composer != null }
    }
    private fun current() = requireNotNull(conversation.session.value)
    private fun composer() = requireNotNull(current().state.value.composer).controller
    private fun write(reply: ReplyController, text: String) {
        val field = compose.onNodeWithTag("conversation-answer-${reply.state.value.record.id}")
        field.performScrollTo()
        device.waitForIdle()
        compose.waitForIdle()
        field.performClick().assertIsFocused()
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        field.performTextInput(text)
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        assertEquals(text, reply.state.value.text)
        val button = compose.onNodeWithTag("conversation-submit-${reply.state.value.record.id}").performScrollTo().assertIsDisplayed()
        val top = compose.runOnIdle { val view = compose.activity.window.decorView; view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom }
        assertTrue(button.fetchSemanticsNode().boundsInWindow.bottom <= top + 1)
    }
    private fun submit(reply: ReplyController) {
        compose.onNodeWithTag("conversation-submit-${reply.state.value.record.id}").performScrollTo().performClick()
        // Submission hides the old input session asynchronously. Let that transition finish
        // before the next fixture answer taps a new field, as a real reader would.
        compose.waitUntil(10_000) { !compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
    }
    private fun capture(file: File) { compose.waitForIdle(); device.waitForIdle(); assertTrue(device.takeScreenshot(file)) }
}
