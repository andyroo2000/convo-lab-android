package com.cdcg.convolab.journal

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import android.os.Looper
import android.os.SystemClock
import com.cdcg.convolab.journal.feedback.WorkflowDiskStore
import com.cdcg.convolab.journal.feedback.WorkflowSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JournalArchiveDiskStoreTest {
    @Test fun largeCachedJournalAndFeedbackLoadOffTheMainThread() = isolated { directory ->
        val records = JournalDiskStore(directory, 101)
        val workflows = WorkflowDiskStore(directory, 101)
        repeat(250) {
            val draft = JournalDraft.blank().edited("Cached writing $it")
            records.write(JournalRecord(entry = JournalEntry(draft.id, null, draft.body, draft.entryDate, 1, "created", "updated")))
            workflows.write(WorkflowSnapshot(draft.key, 1))
        }
        val api = object : JournalGateway {
            override suspend fun archive(cursor: String?) = error("No network expected")
            override suspend fun unfinished() = error("No network expected")
            override suspend fun entry(id: String) = error("No network expected")
            override suspend fun save(draft: JournalDraft) = error("No network expected")
            override suspend fun delete(id: String, version: Int) = error("No network expected")
        }
        val repository = JournalRepository(api, records, archive = JournalArchiveDiskStore(directory, 101), localWork = JournalLocalWork {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            workflows.read(it)?.pendingAnswer != null
        })
        val started = SystemClock.elapsedRealtime()
        val load = runBlocking { repository.load() }
        println("ARCHIVE_LOAD_250_MS=${SystemClock.elapsedRealtime() - started}")
        assertEquals(250, load.records.size)
        assertTrue(load.localWorkRoots.isEmpty())
    }

    @Test fun membershipSurvivesReopenAndDoesNotBecomeAJournalRecord() = isolated { directory ->
        val draft = JournalDraft.blank().edited("Local writing")
        JournalDiskStore(directory, 101).write(JournalRecord(draft = draft))
        val expected = JournalArchive(setOf(draft.id), "next-page")
        JournalArchiveDiskStore(directory, 101).write(expected)
        assertEquals(expected, JournalArchiveDiskStore(directory, 101).read())
        assertNull(JournalArchiveDiskStore(directory, 202).read())
        val load = JournalDiskStore(directory, 101).load()
        assertEquals(listOf(JournalRecord(draft = draft)), load.records)
        assertTrue(load.recoveryFiles.isEmpty())
        JournalArchiveDiskStore(directory, 101).write(JournalArchive(emptySet(), null))
        assertTrue(JournalArchiveDiskStore(directory, 101).read()!!.entryIds.isEmpty())
        assertEquals(draft, JournalDiskStore(directory, 101).read(draft.id)?.draft)
    }

    @Test fun interruptedIndexWriteRestoresItsPreviousMembership() = isolated { directory ->
        val expected = JournalArchive(setOf(JournalIds.newId()), null)
        JournalArchiveDiskStore(directory, 101).write(expected)
        val file = File(directory, "journal/101/archive-index")
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("partial")
        assertEquals(expected, JournalArchiveDiskStore(directory, 101).read())
    }

    @Test fun malformedDerivedIndexCanBeRebuiltWithoutTouchingWriting() = isolated { directory ->
        val draft = JournalDraft.blank().edited("Keep this")
        val records = JournalDiskStore(directory, 101)
        records.write(JournalRecord(draft = draft))
        File(directory, "journal/101/archive-index").writeText("broken index")
        val index = JournalArchiveDiskStore(directory, 101)
        assertNull(index.read())
        index.write(JournalArchive(emptySet(), null))
        assertEquals(draft, records.read(draft.id)?.draft)
        assertTrue(records.load().recoveryFiles.isEmpty())
    }

    private fun isolated(test: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "archive-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        try { test(directory) } finally { directory.deleteRecursively() }
    }
}
