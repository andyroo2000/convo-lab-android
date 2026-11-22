package com.cdcg.convolab.settings

import android.view.WindowInsets
import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.auth.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.ui.cancelFixtureAutofill
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val session = Session("fixture-token", Account(101, "Naomi", "naomi@example.invalid"))
    private val api = UiSettingsGateway(session.account)
    private lateinit var controller: SettingsController
    @After fun stop() { compose.activity.cancelFixtureAutofill(); scope.cancel() }

    @Test fun profileAndPasswordFormsRemainUsableWithKeyboard() {
        launchSettings()
        screenshot(TestOutput.file("settings-account.png"))
        compose.onNodeWithText("Edit Profile").performClick()
        screenshot(TestOutput.file("settings-profile.png"))
        compose.onNodeWithTag("auth-Name").performClick().performTextReplacement("Naomi Updated")
        aboveKeyboard(compose.onNodeWithTag("settings-save-profile"))
        screenshot(TestOutput.file("settings-profile-keyboard.png"))
        compose.activity.cancelFixtureAutofill()
        compose.onNodeWithTag("settings-save-profile").performClick()
        compose.waitUntil(5_000) { controller.state.value.account.name == "Naomi Updated" }
        // Successful form replacement dismisses its input; Back can navigate away after that.
        device.waitForIdle()
        compose.onNodeWithText("Change Password").performScrollTo().performClick()
        screenshot(TestOutput.file("settings-password.png"))
        compose.onNodeWithTag("auth-Current password").performClick().performTextInput("fixture-current")
        compose.onNodeWithTag("auth-New password").performScrollTo().performClick().performTextInput("fixture-new-password")
        compose.onNodeWithTag("auth-Confirm password").performScrollTo().performClick().performTextInput("fixture-new-password")
        aboveKeyboard(compose.onNodeWithTag("settings-save-password"))
        screenshot(TestOutput.file("settings-password-keyboard.png"))
        compose.activity.cancelFixtureAutofill()
        compose.onNodeWithTag("settings-save-password").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { api.passwordChanged }
        compose.onNodeWithText("Password updated.").assertExists()
    }

    @Test fun wanikaniConnectSyncAndConfirmedDisconnectOmitStudyOptions() {
        launchSettings()
        compose.onNodeWithText("Connections").performClick()
        screenshot(TestOutput.file("settings-connections.png"))
        compose.onNodeWithText("WaniKani").performClick()
        screenshot(TestOutput.file("settings-wanikani-empty.png"))
        compose.onNodeWithTag("auth-WaniKani API token").performScrollTo().performClick().performTextInput("fixture-provider-token")
        screenshot(TestOutput.file("settings-wanikani-focus.png"))
        aboveKeyboard(compose.onNodeWithTag("settings-connect-wanikani"))
        screenshot(TestOutput.file("settings-wanikani-token-keyboard.png"))
        compose.activity.cancelFixtureAutofill()
        compose.onNodeWithTag("settings-connect-wanikani").performClick()
        compose.waitUntil(5_000) { controller.state.value.known.connected && !controller.state.value.busy }
        // Successful form replacement dismisses its input; Back can navigate away after that.
        device.waitForIdle()
        compose.onNodeWithText("Known kanji").assertExists()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("WaniKani synced").assertExists()
        compose.onNodeWithText("2026-11-22T16:30:00Z").assertDoesNotExist()
        compose.onNodeWithText("Refresh connection").assertDoesNotExist()
        compose.onNodeWithText("Daily vocabulary transfer").assertDoesNotExist()
        screenshot(TestOutput.file("settings-wanikani-connected.png"))
        compose.onNodeWithText("Disconnect WaniKani").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(controller.state.value.known.connected)
        compose.onNodeWithText("Disconnect WaniKani").performClick()
        compose.onNodeWithText("Disconnect", substring = false).performClick()
        compose.waitUntil(5_000) { !controller.state.value.known.connected && !controller.state.value.busy }
        assertEquals(2, controller.state.value.known.kanji.size)
    }

    @Test fun largeTextFieldsGrowAndKeepSaveAboveKeyboard() {
        launchSettings(2f)
        compose.onNodeWithText("Edit Profile").performScrollTo().performClick()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Name").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Exercise actual enlarged text inside the modal window", layouts.single().layoutInput.density.fontScale >= 1.9f)
        compose.onNodeWithTag("auth-Name").performClick().performTextReplacement("Large text name")
        aboveKeyboard(compose.onNodeWithTag("settings-save-profile"))
        screenshot(TestOutput.file("settings-profile-large-keyboard.png"))
        compose.activity.cancelFixtureAutofill()
        compose.onNodeWithTag("settings-save-profile").performClick()
        compose.waitUntil(5_000) { controller.state.value.account.name == "Large text name" }
    }

    private fun launchSettings(scale: Float = 1f) {
        val auth = AuthApi(SettingsTransport { """{"data":{"id":101,"name":"Naomi","email":"naomi@example.invalid"}}""" })
        val sessions = SessionController(auth, UiSettingsSessionStorage(session))
        runBlocking { sessions.restore() }
        controller = SettingsController(SettingsAccess(session, api, UiKnownStorage(), sessions), scope)
        compose.setContent {
            val state by controller.state.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                ConvoLabTheme { SettingsScreen(state, controller, SettingsActions({}, {}, {})) }
            }
        }
        compose.runOnIdle { controller.start() }
        compose.waitUntil(5_000) { !controller.state.value.busy }
    }

    private fun aboveKeyboard(action: SemanticsNodeInteraction) {
        compose.waitUntil(10_000) { settingsWindow().rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        device.waitForIdle()
        compose.waitForIdle()
        val node = action.performScrollTo().assertIsDisplayed()
        val top = compose.runOnIdle {
            val view = settingsWindow()
            view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        }
        val bounds = node.fetchSemanticsNode().boundsInWindow
        assertTrue("Settings action is hidden by keyboard", bounds.height > 0 && bounds.bottom <= top + 1)
    }

    private fun settingsWindow(): View {
        // Settings lives in its own sheet window; the activity can retain stale IME insets.
        if (Build.VERSION.SDK_INT >= 29) {
            WindowInspector.getGlobalWindowViews().firstOrNull { it.hasWindowFocus() }?.let { return it }
        }
        return compose.activity.window.decorView
    }

    private fun screenshot(file: File) {
        compose.waitForIdle()
        device.waitForIdle()
        assertTrue(device.takeScreenshot(file))
    }
}

private class UiSettingsGateway(var account: Account) : SettingsGateway {
    private var snapshot = KnownKanjiSnapshot()
    var passwordChanged = false
    override suspend fun profile(change: ProfileChange): Account { account = account.copy(name = change.name, email = change.email); return account }
    override suspend fun password(change: PasswordChange) { passwordChanged = true }
    override suspend fun knownKanji() = snapshot
    override suspend fun connect(token: String): KnownKanjiSnapshot { snapshot = KnownKanjiSnapshot(1, setOf('映'.code, '画'.code), true, "2026-11-22T16:30:00Z"); return snapshot }
    override suspend fun sync() = Unit
    override suspend fun disconnect() { snapshot = snapshot.copy(connected = false) }
}

private class UiKnownStorage : KnownKanjiStorage {
    private var snapshot: KnownKanjiSnapshot? = null
    override fun read() = snapshot
    override fun write(snapshot: KnownKanjiSnapshot) { this.snapshot = snapshot }
}

private class UiSettingsSessionStorage(private var session: Session?) : SessionStorage {
    override fun read() = session
    override fun write(session: Session) { this.session = session }
    override fun clear() { session = null }
}
