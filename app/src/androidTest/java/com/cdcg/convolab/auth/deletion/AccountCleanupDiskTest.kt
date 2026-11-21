package com.cdcg.convolab.auth.deletion

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class AccountCleanupDiskTest {
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cleanup-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = AccountCleanupDiskStore(root)
    @After fun clean() { root.deleteRecursively() }
    @Test fun restartFindsConfirmedIntentAndPurgesOnlyThatAccountsFiles() = runBlocking {
        for (family in listOf("journal", "journal-workflow", "journal-audio", "known-kanji", "journal-typing")) {
            for (id in listOf(1, 2)) File(root, "$family/$id/nested/data").apply { parentFile!!.mkdirs(); writeText("public fixture") }
        }
        store.markConfirmed(1)
        val restarted = AccountCleanupDiskStore(root)
        assertEquals(setOf(1L), restarted.pending())
        restarted.purge(1)
        for (family in listOf("journal", "journal-workflow", "journal-audio", "known-kanji", "journal-typing")) {
            assertFalse(File(root, "$family/1").exists())
            assertEquals("public fixture", File(root, "$family/2/nested/data").readText())
        }
        restarted.complete(1)
        assertTrue(restarted.pending().isEmpty())
    }
    @Test fun symlinkInsideDeletedAccountDoesNotFollowAnotherAccount() = runBlocking {
        val other = File(root, "journal/2/data").apply { parentFile!!.mkdirs(); writeText("keep") }
        val first = File(root, "journal/1").apply { mkdirs() }
        Files.createSymbolicLink(File(first, "link").toPath(), other.parentFile!!.toPath())
        store.purge(1)
        assertFalse(first.exists())
        assertEquals("keep", other.readText())
    }
    @Test fun interruptedAtomicMarkerRemainsDiscoverable() = runBlocking {
        val directory = File(root, "account-cleanup").apply { mkdirs() }
        File(directory, "7.pending.new").writeText("")
        File(directory, "8.pending.bak").writeText("8")
        File(directory, "invalid.pending").writeText("fixture")
        assertEquals(setOf(7L, 8L), store.pending())
        store.complete(7); store.complete(8)
        assertTrue(store.pending().isEmpty())
    }
}
