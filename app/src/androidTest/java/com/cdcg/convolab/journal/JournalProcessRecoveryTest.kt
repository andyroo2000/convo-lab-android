package com.cdcg.convolab.journal

import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** Run with restartPhase=seed, stop the app process, then restartPhase=resume. All data is a local fixture. */
class JournalProcessRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "journal-process-fixture")
    private var scope = owner()
    private var notebook by mutableStateOf<NotebookController?>(null)
    private val text = "再起動しても、この日記を残します。"
    private val marker get() = File(root, "process.json")
    @After fun stop() = runBlocking { scope.cancel(); scope.coroutineContext.job.join() }

    @Test fun persistedRequestAutomaticallySyncsAfterAProcessRestart() {
        val phase = InstrumentationRegistry.getArguments().getString("restartPhase", "complete")
        compose.setContent {
            ConvoLabTheme { notebook?.let { controller ->
                val state by controller.state.collectAsState()
                NotebookScreen(state, controller) {}
            } }
        }
        if (phase != "resume") seed()
        if (phase != "seed") resume(requireNewProcess = phase == "resume")
    }
    private fun seed() {
        root.deleteRecursively()
        val api = RestartGateway(offline = true)
        val controller = launch(api)
        compose.onNodeWithTag("journal-editor").performClick().performTextInput(text)
        compose.onNodeWithTag("journal-editor").assertTextContains(text)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        compose.waitForIdle()
        compose.runOnIdle { controller.startSync() }
        compose.waitUntil(5_000) { controller.state.value.syncStatus == "Saved on this device" && api.sent.isNotEmpty() }
        val sent = api.sent.first()
        val pending = requireNotNull(JournalDiskStore(root, 101).read(sent.id)?.pendingSave)
        assertEquals(sent, pending)
        marker.writeText(JSONObject().put("pid", Process.myPid()).put("id", sent.id).put("editId", sent.editId)
            .put("version", sent.expectedVersion).toString())
        compose.onNodeWithText("Saved on this device").assertIsDisplayed()
        screenshot("journal-before-process-stop")
    }
    private fun resume(requireNewProcess: Boolean) {
        val expected = JSONObject(marker.readText())
        if (requireNewProcess) assertNotEquals(expected.getInt("pid"), Process.myPid())
        val api = RestartGateway(offline = false)
        val controller = launch(api)
        compose.onNodeWithTag("journal-editor").assertTextContains(text)
        assertEquals(expected.getString("id"), controller.state.value.draft.id)
        screenshot("journal-after-process-restart")
        compose.runOnIdle { controller.startSync() }
        compose.waitUntil(5_000) { controller.state.value.syncStatus == "Synced" }
        val sent = api.sent.single()
        assertEquals(expected.getString("id"), sent.id)
        assertEquals(expected.getString("editId"), sent.editId)
        assertEquals(expected.getInt("version"), sent.expectedVersion)
        assertEquals(text, sent.body)
        val acknowledged = requireNotNull(JournalDiskStore(root, 101).read(sent.id))
        assertNull(acknowledged.pendingSave)
        assertEquals(text, acknowledged.entry?.body)
        compose.onNodeWithText("Synced").assertIsDisplayed()
        screenshot("journal-automatically-synced-after-process-restart")
        runBlocking { scope.cancel(); scope.coroutineContext.job.join() }
        root.deleteRecursively()
    }
    private fun launch(api: RestartGateway): NotebookController {
        runBlocking { scope.cancel(); scope.coroutineContext.job.join() }
        scope = owner()
        val controller = NotebookController(JournalRepository(api, JournalDiskStore(root, 101)), scope)
        compose.runOnIdle { notebook = controller; controller.start() }
        compose.waitUntil(5_000) { !controller.state.value.loading }
        return controller
    }
    private fun owner() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(TestOutput.file("$name.png")))
    }
}
private class RestartGateway(val offline: Boolean) : JournalGateway {
    val sent = mutableListOf<JournalDraft>()
    override suspend fun archive(cursor: String?): JournalPage {
        if (offline) throw IOException("Fixture network unavailable")
        return JournalPage(emptyList(), null)
    }
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("No remote fixture")
    override suspend fun save(draft: JournalDraft): JournalEntry {
        sent.add(draft)
        if (offline) throw IOException("Fixture response lost")
        return JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, draft.expectedVersion + 1, "created", "updated")
    }
    override suspend fun delete(id: String, version: Int) = Unit
}
