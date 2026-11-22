package com.cdcg.convolab.journal

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class JournalArchiveDiskStoreTest {
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
