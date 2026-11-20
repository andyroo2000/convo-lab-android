package com.cdcg.convolab.auth

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun signInAndResetRemainReachableWithKeyboard() {
        val state = mutableStateOf(AuthState(restoring = false))
        var submitted: AuthPage? = null
        compose.setContent {
            ConvoLabTheme { AuthScreen(state.value, { page, _ ->
                submitted = page
                state.value = state.value.copy(resetSent = page == AuthPage.Reset)
            }, { state.value = AuthState(restoring = false) }) }
        }
        compose.onNodeWithText("CONVO-LAB").assertIsDisplayed()
        screenshot("auth-login")
        compose.onNodeWithTag("auth-submit").performScrollTo().assertIsNotEnabled()
        input("Email", "sample@example.invalid")
        input("Password", "fixture-password")
        compose.onNodeWithTag("auth-submit").performScrollTo().assertIsEnabled()
        assertSubmitAboveKeyboard()
        screenshot("auth-login-keyboard")
        compose.onNodeWithTag("auth-submit").performClick()
        assertEquals(AuthPage.SignIn, submitted)
        compose.onNodeWithText("Forgot password?").performScrollTo().performClick()
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        compose.onNodeWithText("If an account exists for that email, a reset link is on its way.").performScrollTo().assertIsDisplayed()
        assertEquals(AuthPage.Reset, submitted)
        screenshot("auth-reset-confirmation")
    }

    @Test fun registrationRequiresInviteAndMatchingPassword() {
        compose.setContent { ConvoLabTheme { AuthScreen(AuthState(restoring = false), { _, _ -> }, {}) } }
        compose.onNodeWithText("Create an account").performScrollTo().performClick()
        input("Name", "Sample learner")
        input("Email", "sample@example.invalid")
        input("Password", "fixture-password")
        input("Confirm password", "different-password")
        compose.onNodeWithTag("auth-submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Passwords do not match.").assertIsDisplayed()
        screenshot("auth-registration-validation")
    }

    private fun input(label: String, value: String) {
        compose.onNodeWithTag("auth-$label").performScrollTo().performClick().performTextInput(value)
    }

    private fun assertSubmitAboveKeyboard() {
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        val keyboardTop = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            decor.height - decor.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        }
        val button = compose.onNodeWithTag("auth-submit").fetchSemanticsNode().boundsInWindow
        assertTrue("Submit covered by keyboard", button.bottom <= keyboardTop + 1)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        device.waitForIdle()
        assertTrue(device.takeScreenshot(TestOutput.file("$name.png")))
    }
}
