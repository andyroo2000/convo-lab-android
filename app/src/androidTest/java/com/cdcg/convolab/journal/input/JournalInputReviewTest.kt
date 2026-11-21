package com.cdcg.convolab.journal.input

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.ui.ConvoLabTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class JournalInputReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val engine = ReviewEngine()
    private lateinit var session: JournalInputSession
    private lateinit var focus: FocusManager
    private var text by mutableStateOf("")
    @After fun stop() { if (::session.isInitialized) session.close() }

    @Test fun oversizedDraftCanBeShortenedAndPasteLimitIsExplained() {
        launch("a".repeat(20_001), JournalKeyboard.Device)
        compose.onNodeWithTag("input").performTextReplacement("a".repeat(20_000))
        assertEquals(20_000, text.length)
        compose.onNodeWithTag("input").performTextInput("b")
        assertEquals(20_000, text.length)
        compose.onNodeWithText("Journal entries can contain up to 20,000 characters.").assertExists()
        compose.onNodeWithTag("input").performTextReplacement("Shorter")
        assertEquals("Shorter", text)
        compose.onNodeWithText("Journal entries can contain up to 20,000 characters.").assertDoesNotExist()
    }
    @Test fun candidateSurvivesFieldFocusLoss() {
        launch()
        val field = compose.onNodeWithTag("input").performClick()
        "nihongo".forEach { field.performTextInput(it.toString()) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("日本語").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { focus.clearFocus() }
        compose.onNodeWithText("日本語").performClick()
        assertEquals("日本語", text)
    }
    @Test fun resetReportsSuccessSeparatelyAndCancelsBusyQuery() {
        launch()
        engine.wait = CompletableDeferred()
        compose.runOnIdle { session.query(CompositionQuery("にほんご", emptyList(), 1)) {} }
        compose.waitUntil(5_000) { session.busy }
        compose.runOnIdle { session.reset() }
        compose.waitUntil(5_000) { session.status != null }
        assertFalse(session.busy)
        assertNull(session.error)
        assertEquals("Typing learning was reset on this device.", session.status)
    }
    private fun launch(initial: String = "", mode: JournalKeyboard = JournalKeyboard.BuiltIn) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preference = KeyboardPreference(context.getSharedPreferences("review-${UUID.randomUUID()}", Context.MODE_PRIVATE))
        preference.change(mode)
        session = JournalInputSession(preference, engine)
        text = initial
        compose.setContent {
            focus = LocalFocusManager.current
            val host = remember { JournalInputHost() }
            ConvoLabTheme { CompositionLocalProvider(LocalJournalInput provides session, LocalInputHost provides host) {
                Column {
                    JournalWritingField(text, { text = it }, WritingOptions("Writing"), Modifier.testTag("input"))
                    JournalInputDock()
                }
            } }
        }
    }
}

private class ReviewEngine : JournalTypingEngine {
    var wait: CompletableDeferred<Unit>? = null
    override suspend fun prepare() = Unit
    override suspend fun candidates(query: CompositionQuery, expanded: Boolean): List<JapaneseCandidate> {
        wait?.await()
        return listOf(JapaneseCandidate("日本語", query.reading, listOf(CandidatePart("日本語", query.reading))))
    }
    override suspend fun learn(choice: ConfirmedInput) = Unit
    override suspend fun reset() = Unit
    override suspend fun close() = Unit
}
