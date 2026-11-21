package com.cdcg.convolab.journal

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    private val original = Session("fixture-first", Account(1, "First", "first@example.invalid"))
    private val replacement = Session("fixture-second", Account(2, "Second", "second@example.invalid"))

    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { store.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun activityStoreRetainsTheControllerAcrossRouteRecreation() = runTest {
        val auth = FakeAuth().apply { loginResult = { original } }
        val sessions = SessionController(auth, MemoryStorage(), dispatcher)
        val factory = viewModelFactory { initializer { NotebookViewModel(sessions) {
            JournalRepository(NotebookGateway(), NotebookMemory(), dispatcher)
        } } }
        val first = ViewModelProvider(owner, factory)["active-journal", NotebookViewModel::class.java]
        sessions.login("first@example.invalid", "fixture-password")
        runCurrent()
        val notebook = requireNotNull(first.binding.value).controller
        notebook.edit("回転しても残す文章。")
        val recreatedOwner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val rebound = ViewModelProvider(recreatedOwner, factory)["active-journal", NotebookViewModel::class.java]
        assertSame(first, rebound)
        assertSame(notebook, rebound.binding.value?.controller)
        assertEquals("回転しても残す文章。", rebound.binding.value?.controller?.state?.value?.draft?.body)
    }

    @Test fun deletionRegistryDoesNotCancelTheCollectorForTheNextAccount() = runTest {
        val auth = FakeAuth().apply { loginResult = { original } }
        val sessions = SessionController(auth, MemoryStorage(), dispatcher)
        val factory = viewModelFactory { initializer { NotebookViewModel(sessions) {
            JournalRepository(NotebookGateway(), NotebookMemory(), dispatcher)
        } } }
        val model = ViewModelProvider(owner, factory)["active-journal", NotebookViewModel::class.java]
        sessions.login("first@example.invalid", "fixture")
        runCurrent()
        sessions.work.stop(1)
        sessions.forgetDeletedAccount(1)
        auth.loginResult = { replacement }
        sessions.login("second@example.invalid", "fixture")
        runCurrent()
        assertEquals(2L, model.binding.value?.accountId)
        model.binding.value!!.controller.edit("次のアカウント。")
        assertEquals("次のアカウント。", model.binding.value?.controller?.state?.value?.draft?.body)
    }

    @Test fun switchingAccountsFlushesPreviousWritingBeforeOpeningTheNextWorkspace() = runTest {
        val auth = FakeAuth().apply { loginResult = { original } }
        val sessions = SessionController(auth, MemoryStorage(), dispatcher)
        val oldStorage = NotebookMemory()
        var previousFlushed = false
        val factory = viewModelFactory { initializer { NotebookViewModel(sessions) { session ->
            val storage = if (session.account.id == 1L) oldStorage else {
                previousFlushed = oldStorage.records.values.single().draft?.body == "前のアカウントの文章。"
                NotebookMemory()
            }
            JournalRepository(NotebookGateway(), storage, dispatcher)
        } } }
        val model = ViewModelProvider(owner, factory)["active-journal", NotebookViewModel::class.java]
        sessions.login("first@example.invalid", "fixture-password")
        runCurrent()
        model.binding.value!!.controller.edit("前のアカウントの文章。")
        sessions.signOut()
        auth.loginResult = { replacement }
        sessions.login("second@example.invalid", "fixture-password")
        runCurrent()
        assertTrue(previousFlushed)
        assertEquals(2L, model.binding.value?.accountId)
        assertEquals("", model.binding.value?.controller?.state?.value?.draft?.body)
    }
}
