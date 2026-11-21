package com.cdcg.convolab.journal.input

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.auth.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class KeyboardOwnerTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    @Test fun rotationRetainsEngineAndAccountSwitchWaitsForOldOwner() = runBlocking {
        val auth = OwnerAuth()
        val sessions = SessionController(auth, OwnerStorage())
        sessions.restore()
        val engines = mutableListOf<OwnerEngine>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preference = KeyboardPreference(context.getSharedPreferences("owner-${UUID.randomUUID()}", Context.MODE_PRIVATE))
        val factory = ownerFactory(sessions, preference, engines)
        lateinit var before: KeyboardViewModel
        activity.scenario.onActivity { before = ViewModelProvider(it, factory)[KeyboardViewModel::class.java] }
        await { before.binding.value != null }
        val first = before.binding.value!!.session
        activity.scenario.recreate()
        activity.scenario.onActivity {
            val after = ViewModelProvider(it, factory)[KeyboardViewModel::class.java]
            assertSame(before, after)
            assertSame(first, after.binding.value!!.session)
        }
        assertEquals(1, engines.size)
        auth.account = ownerAccount(2)
        sessions.login("next@example.invalid", "fixture")
        await { before.binding.value?.accountId == 2L }
        assertTrue(engines.first().closed)
        assertEquals(2, engines.size)
        sessions.signOut()
        await { engines.last().closed }
        assertNull(before.binding.value)
    }
    private fun ownerFactory(sessions: SessionController, preference: KeyboardPreference, engines: MutableList<OwnerEngine>) =
        viewModelFactory { initializer {
            KeyboardViewModel(sessions) {
                assertTrue(engines.all { it.closed })
                val engine = OwnerEngine().also { engines.add(it) }
                JournalInputSession(preference, engine)
            }
        } }
    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { condition() }) delay(10)
    }
}
private fun ownerAccount(id: Long) = Account(id, "Fixture", "$id@example.invalid")
private class OwnerAuth : AuthGateway {
    var account = ownerAccount(1)
    override suspend fun verify(token: String) = account
    override suspend fun login(email: String, password: String) = Session("fixture-${account.id}", account)
    override suspend fun register(registration: Registration) = Session("fixture", account)
    override suspend fun resetPassword(email: String) = Unit
    override suspend fun revoke(token: String) = Unit
}
private class OwnerStorage : SessionStorage {
    private var value: Session? = Session("fixture", ownerAccount(1))
    override fun read() = value
    override fun write(session: Session) { value = session }
    override fun clear() { value = null }
}
private class OwnerEngine : JournalTypingEngine {
    var closed = false
    override suspend fun prepare() = Unit
    override suspend fun candidates(query: CompositionQuery, expanded: Boolean) = emptyList<JapaneseCandidate>()
    override suspend fun learn(choice: ConfirmedInput) = Unit
    override suspend fun reset() = Unit
    override suspend fun close() { closed = true }
}
