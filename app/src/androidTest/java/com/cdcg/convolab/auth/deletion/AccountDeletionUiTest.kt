package com.cdcg.convolab.auth.deletion

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.auth.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AccountDeletionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "delete-ui-${UUID.randomUUID()}")
    private var calls = 0
    private lateinit var controller: AccountDeletionController
    private lateinit var sessions: SessionController
    @After fun clean() { scope.cancel(); root.deleteRecursively() }
    @Test fun confirmationCanCancelWithoutDeletingAnything() {
        launch()
        compose.onNodeWithText("Permanently delete account").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, calls)
        assertNotNull(sessions.state.value.account)
        assertTrue(File(root, "journal/101/draft").exists())
    }
    @Test fun passwordConfirmationRemovesFixtureAccountAndItsLocalFiles() {
        launch()
        compose.onNodeWithTag("auth-Current password").performClick().performTextInput("fixture-password")
        compose.onNodeWithText("Permanently delete account").assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        compose.waitForIdle(); device.waitForIdle()
        assertTrue(device.takeScreenshot(TestOutput.file("account-deletion-confirmation.png")))
        compose.onNodeWithText("Permanently delete account").performClick()
        compose.waitUntil(5_000) { !controller.state.value.busy && controller.state.value.confirmed }
        compose.onNodeWithText("Your account and its local data have been removed.").assertIsDisplayed()
        assertEquals(1, calls)
        assertNull(sessions.state.value.account)
        assertFalse(File(root, "journal/101").exists())
    }
    private fun launch() {
        val credentials = UiDeletionCredentials()
        sessions = SessionController(UiDeletionAuth(), credentials)
        runBlocking { sessions.restore() }
        File(root, "journal/101/draft").apply { parentFile!!.mkdirs(); writeText("Public fixture") }
        controller = AccountDeletionController(sessions, AccountDeletionGateway { session, password ->
            assertEquals(101L, session.account.id)
            assertEquals("fixture-password", password)
            calls++
        }, AccountCleanupDiskStore(root), scope)
        controller.open()
        compose.setContent {
            val state by controller.state.collectAsState()
            ConvoLabTheme { AccountDeletionDialog(state, controller) }
        }
    }
}
private val uiDeletionAccount = Account(101, "Fixture", "fixture@example.invalid")
private class UiDeletionCredentials : SessionStorage {
    private var value: Session? = Session("fixture", uiDeletionAccount)
    override fun read() = value
    override fun write(session: Session) { value = session }
    override fun clear() { value = null }
}
private class UiDeletionAuth : AuthGateway {
    override suspend fun verify(token: String) = uiDeletionAccount
    override suspend fun login(email: String, password: String) = Session("fixture", uiDeletionAccount)
    override suspend fun register(registration: Registration) = Session("fixture", uiDeletionAccount)
    override suspend fun resetPassword(email: String) = Unit
    override suspend fun revoke(token: String) = Unit
}
