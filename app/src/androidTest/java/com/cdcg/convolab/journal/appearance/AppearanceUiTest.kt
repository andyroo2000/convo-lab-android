package com.cdcg.convolab.journal.appearance

import android.content.Context
import androidx.core.content.edit
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.view.WindowManager
import org.junit.Before
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.AccountMenu
import com.cdcg.convolab.ui.ConvoLabTheme
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val preferences = context.getSharedPreferences("appearance-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Before fun prepareWindow() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
                navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK))
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    @After fun stop() { preferences.edit(commit = true) { clear() }; scope.cancel() }

    @Test fun fontAndSizePreviewUpdateAndRestoreFromDevicePreferences() {
        val store = AppearanceStore(preferences)
        compose.setContent {
            val value by store.state.collectAsState()
            ConvoLabTheme { CompositionLocalProvider(LocalAppearanceStore provides store, LocalJournalTypography provides value) {
                Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) { AppearancePanel() } }
            } }
        }
        compose.onNodeWithText("Serif", substring = false).performClick()
        compose.onNodeWithContentDescription("Font size").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.waitUntil { store.state.value == JournalTypography(JournalFont.Serif, 30) }
        assertEquals(store.state.value, AppearanceStore(preferences).state.value)
        compose.onNodeWithTag("appearance-preview").performScrollTo().assertIsDisplayed()
        capture("journal-appearance-serif-30.png")
        compose.onNodeWithText("Sans-serif", substring = false).performScrollTo().performClick()
        assertEquals(JournalFont.Sans, store.state.value.font)
        compose.onNodeWithText("Klee One").performClick()
        assertEquals(JournalFont.Klee, store.state.value.font)
    }

    @Test fun invalidStoredPreferencesRecoverAndLargeWritingKeepsActionsAboveKeyboard() {
        preferences.edit(commit = true) { putString("size", "damaged") }
        assertEquals(JournalTypography(), AppearanceStore(preferences).state.value)
        preferences.edit(commit = true) { clear(); putInt("size", 99); putString("font", "unknown") }
        assertEquals(JournalTypography(JournalFont.Klee, 30), AppearanceStore(preferences).state.value)
        val notebook = NotebookController(JournalRepository(AppearanceJournal(), AppearanceRecords()), scope)
        compose.setContent {
            val state by notebook.state.collectAsState()
            val density = LocalDensity.current
            ConvoLabTheme { CompositionLocalProvider(LocalJournalTypography provides JournalTypography(JournalFont.Sans, 30),
                LocalDensity provides Density(density.density, 1.6f)) {
                NotebookScreen(state, notebook) { AccountMenu("Naomi", {}, null) }
            } }
        }
        compose.runOnIdle { notebook.start() }
        compose.waitUntil(5_000) { !notebook.state.value.loading }
        compose.onNodeWithTag("journal-editor").performScrollTo().performClick().performTextInput("大きな文字でも、日記を書けます。\n".repeat(4))
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        val button = compose.onNodeWithTag("journal-save").assertIsDisplayed().assertIsEnabled()
        val top = compose.runOnIdle { val view = compose.activity.window.decorView; view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom }
        val buttonBounds = button.fetchSemanticsNode().boundsInWindow
        assertTrue(buttonBounds.bottom <= top + 1)
        assertTrue("The action must retain a readable height", buttonBounds.height >= 44 * context.resources.displayMetrics.density)
        val editorBounds = compose.onNodeWithTag("journal-editor").fetchSemanticsNode().boundsInWindow
        assertTrue("Writing must retain visible space", editorBounds.height >= 40 * context.resources.displayMetrics.density)
        capture("journal-large-text-keyboard.png")
        compose.runOnIdle { notebook.flush() }
        assertTrue(notebook.state.value.draft.body.contains("大きな文字"))
    }
    private fun capture(name: String) { compose.waitForIdle(); device.waitForIdle(); assertTrue(device.takeScreenshot(TestOutput.file(name))) }
}

private class AppearanceRecords : JournalStorage {
    private val records = linkedMapOf<String, JournalRecord>()
    override fun load() = JournalLoad(records.values.toList(), emptyList())
    override fun read(id: String) = records[id]
    override fun write(record: JournalRecord) { records[record.id] = record }
    override fun remove(id: String) { records.remove(id) }
}
private class AppearanceJournal : JournalGateway {
    override suspend fun archive(cursor: String?) = JournalPage(emptyList(), null)
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("No server fixture")
    override suspend fun save(draft: JournalDraft): JournalEntry = error("Not submitting")
    override suspend fun delete(id: String, version: Int) = Unit
}
