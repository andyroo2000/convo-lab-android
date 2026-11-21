package com.cdcg.convolab.journal.feedback

import androidx.test.platform.app.InstrumentationRegistry
import com.cdcg.convolab.auth.deletion.AccountCleanupDiskStore
import com.cdcg.convolab.journal.JournalIds
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class JournalViewPreferencesTest {
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "views-${UUID.randomUUID()}")
    private val owners = mutableListOf<CoroutineScope>()
    @After fun clean() = runBlocking { owners.forEach { it.cancel() }; owners.forEach { it.coroutineContext.job.join() }; root.deleteRecursively(); Unit }
    @Test fun entryChoicesSurviveRestartAndStaySeparateAcrossAccounts() = runBlocking {
        val entry = JournalIds.newId()
        val firstOwner = owner()
        val first = JournalViewPreferences(root, 1, firstOwner)
        withContext(Dispatchers.Main) { first.select(entry, true) }
        firstOwner.cancel(); firstOwner.coroutineContext.job.join()
        val restarted = JournalViewPreferences(root, 1, owner())
        withTimeout(5_000) { while (restarted.versions.value[entry] != true) delay(10) }
        val other = JournalViewPreferences(root, 2, owner())
        withContext(Dispatchers.Main) { other.select(entry, false) }
        assertEquals(false, other.versions.value[entry])
        assertEquals(true, restarted.versions.value[entry])
    }
    @Test fun deletionRemovesVersionPreferencesAlongWithAccountData() = runBlocking {
        val scope = owner()
        val preferences = JournalViewPreferences(root, 1, scope)
        withContext(Dispatchers.Main) { preferences.select(JournalIds.newId(), true) }
        scope.cancel(); scope.coroutineContext.job.join()
        assertTrue(File(root, "journal-preferences/1/versions.json").exists())
        AccountCleanupDiskStore(root).purge(1)
        assertFalse(File(root, "journal-preferences/1").exists())
    }
    private fun owner() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { owners.add(it) }
}
