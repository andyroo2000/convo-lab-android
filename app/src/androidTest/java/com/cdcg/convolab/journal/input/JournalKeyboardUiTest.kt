package com.cdcg.convolab.journal.input

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.AccountMenu
import com.cdcg.convolab.ui.ConvoLabTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.File

class JournalKeyboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var session: JournalInputSession
    private lateinit var notebook: NotebookController
    private lateinit var preferences: KeyboardPreference
    private val engine = KeyboardFixtureEngine()
    private var native: NativeJournalTyping? = null
    private lateinit var directory: File
    @After fun stop() {
        if (::session.isInitialized) session.close()
        runBlocking { native?.close() }
        if (native != null) File(compose.activity.noBackupFilesDir, "journal-typing/${Long.MAX_VALUE}").deleteRecursively()
        scope.cancel()
        runBlocking { scope.coroutineContext[Job]?.join() }
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test fun kanaStaysImmediateWhileSuggestionsWaitAndCandidatesStayAboveTheKeyboard() {
        launchNotebook()
        engine.wait = CompletableDeferred()
        type("nihongo")
        assertEquals("にほんご", notebook.state.value.draft.body)
        compose.onNodeWithTag("journal-editor").assertTextContains("にほんご")
        assertDockVisible()
        screenshot(TestOutput.file("journal-built-in-waiting.png"))
        engine.wait!!.complete(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("日本語").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("日本語").performClick()
        assertEquals("日本語", notebook.state.value.draft.body)
        compose.waitUntil(5_000) { engine.confirmed.isNotEmpty() }
        assertEquals("日本語", engine.confirmed.single().text)
        compose.onNodeWithTag("journal-save").performClick()
        compose.waitUntil(5_000) { !notebook.state.value.busy }
        assertEquals("日本語", notebook.state.value.draft.body)
        screenshot(TestOutput.file("journal-built-in-confirmed.png"))
    }
    @Test fun pastedEnglishAndEmojiStayLiteralAndDeviceKeyboardBypassesConversion() {
        launchNotebook()
        compose.onNodeWithTag("journal-editor").performClick().performTextInput("Hello 😊 日本語")
        assertEquals("Hello 😊 日本語", notebook.state.value.draft.body)
        compose.onNodeWithTag("journal-candidates").assertDoesNotExist()
        compose.runOnIdle { preferences.change(JournalKeyboard.Device) }
        compose.onNodeWithTag("journal-editor").performTextInput(" nihongo")
        assertEquals("Hello 😊 日本語 nihongo", notebook.state.value.draft.body)
    }
    @Test fun submittingPendingNCommitsItBeforeSaving() {
        launchNotebook()
        type("n")
        assertEquals("n", notebook.state.value.draft.body)
        compose.onNodeWithTag("journal-save").performClick()
        compose.waitUntil(5_000) { !notebook.state.value.busy }
        assertEquals("ん", notebook.state.value.draft.body)
    }
    @Test fun englishSoftwareKeysProduceKanaWithoutDuplicatingComposingText() {
        launchNotebook()
        compose.onNodeWithTag("journal-editor").performClick()
        for (letter in "nihongo") {
            val key = device.wait(Until.findObject(By.desc(letter.toString())), 5_000)
            assertNotNull("English software key $letter was not available", key)
            key.click()
        }
        compose.waitUntil(5_000) { notebook.state.value.draft.body == "にほんご" }
        screenshot(TestOutput.file("journal-real-english-keyboard.png"))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("日本語").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("日本語").performClick()
        assertEquals("日本語", notebook.state.value.draft.body)
    }
    @Test fun realBundledEngineSuppliesAndLearnsJournalCandidates() {
        launchNotebook(realEngine = true)
        type("nihongo")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("日本語").fetchSemanticsNodes().isNotEmpty() }
        assertDockVisible()
        screenshot(TestOutput.file("journal-native-keyboard.png"))
        compose.onNodeWithText("日本語").performClick()
        assertEquals("日本語", notebook.state.value.draft.body)
        val learned = File(compose.activity.noBackupFilesDir, "journal-typing/${Long.MAX_VALUE}/confirmed.json")
        compose.waitUntil(5_000) { learned.isFile }
        assertTrue(learned.readText().contains("日本語"))
    }
    private fun launchNotebook(realEngine: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = KeyboardPreference(context.getSharedPreferences("keyboard-fixture-${UUID.randomUUID()}", Context.MODE_PRIVATE))
        preferences.change(JournalKeyboard.BuiltIn)
        if (realEngine) native = NativeJournalTyping(context, Long.MAX_VALUE)
        session = JournalInputSession(preferences, native ?: engine)
        directory = File(context.noBackupFilesDir, "keyboard-ui-${UUID.randomUUID()}")
        notebook = NotebookController(JournalRepository(KeyboardFixtureGateway(), JournalDiskStore(directory, 101)), scope)
        compose.setContent {
            val state by notebook.state.collectAsState()
            ConvoLabTheme { CompositionLocalProvider(LocalJournalInput provides session) {
                NotebookScreen(state, notebook) { AccountMenu("Naomi", {}, null) }
            } }
        }
        compose.runOnIdle { notebook.start() }
        compose.waitUntil(5_000) { !notebook.state.value.loading && session.ready }
    }
    private fun type(text: String) {
        val field = compose.onNodeWithTag("journal-editor").performClick()
        for (character in text) field.performTextInput(character.toString())
    }
    private fun assertDockVisible() {
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        val top = compose.runOnIdle { val view = compose.activity.window.decorView; view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom }
        for (tag in listOf("journal-candidates", "journal-save", "journal-editor")) {
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertTrue("$tag overlaps keyboard", bounds.bottom <= top + 1)
        }
    }
    private fun screenshot(file: File) { compose.waitForIdle(); assertTrue(device.takeScreenshot(file)) }
}

private class KeyboardFixtureEngine : JournalTypingEngine {
    var wait: CompletableDeferred<Unit>? = null
    val confirmed = mutableListOf<ConfirmedInput>()
    override suspend fun prepare() = Unit
    override suspend fun candidates(query: CompositionQuery, expanded: Boolean): List<JapaneseCandidate> {
        wait?.await()
        return if (query.reading == "にほんご") listOf(JapaneseCandidate("日本語", query.reading, listOf(CandidatePart("日本語", query.reading)))) else emptyList()
    }
    override suspend fun learn(choice: ConfirmedInput) { confirmed += choice }
    override suspend fun reset() { confirmed.clear() }
    override suspend fun close() = Unit
}
private class KeyboardFixtureGateway : JournalGateway {
    override suspend fun archive(cursor: String?) = JournalPage(emptyList(), null)
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("No remote fixture")
    override suspend fun save(draft: JournalDraft) = JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, draft.expectedVersion + 1, "created", "updated")
    override suspend fun delete(id: String, version: Int) = Unit
}
