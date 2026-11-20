package com.cdcg.convolab.journal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDiskStoreTest {
    @Test fun accountDirectoriesAndAtomicEnvelopeSurviveReopen() = isolated { directory ->
        val draft = JournalDraft.blank().edited("今日は晴れです。", " 日記 ")
        val newer = draft.edited("明日は映画を見ます。")
        val expected = JournalRecord(draft = newer, pendingSave = draft)
        JournalDiskStore(directory, 101).write(expected)
        assertEquals(expected, JournalDiskStore(directory, 101).read(draft.id))
        assertTrue(JournalDiskStore(directory, 202).load().records.isEmpty())
        val pendingDeletion = JournalRecord(draft = newer, pendingDelete = true, acknowledgedVersion = 3)
        JournalDiskStore(directory, 101).write(pendingDeletion)
        assertEquals(pendingDeletion, JournalDiskStore(directory, 101).read(draft.id))
        assertEquals("日記", draft.title)
        JournalDiskStore(directory, 101).remove(draft.id)
        assertTrue(JournalDiskStore(directory, 101).load().records.isEmpty())
    }

    @Test fun interruptedAtomicReplacementRestoresPreviousRecord() = isolated { directory ->
        val draft = JournalDraft.blank().edited("元の文章。")
        val storage = JournalDiskStore(directory, 101)
        storage.write(JournalRecord(draft = draft))
        val file = File(directory, "journal/101/${draft.id}.json")
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("partial write")
        assertEquals(draft, JournalDiskStore(directory, 101).read(draft.id)?.draft)
    }

    @Test fun corruptAndFutureSchemaRecordsArePreservedForRecovery() = isolated { directory ->
        val storage = JournalDiskStore(directory, 101)
        for (contents in listOf("broken json", "{\"schemaVersion\":99}")) {
            val draft = JournalDraft.blank().edited("保存した文章。")
            storage.write(JournalRecord(draft = draft))
            val file = File(directory, "journal/101/${draft.id}.json")
            file.writeText(contents)
            assertThrows(Exception::class.java) { storage.write(JournalRecord(draft = draft.edited("new"))) }
            assertThrows(Exception::class.java) { storage.remove(draft.id) }
            assertEquals(contents, file.readText())
            assertTrue(storage.load().recoveryFiles.contains(file.name))
        }
    }

    @Test fun invalidIdentifiersCannotEscapeTheAccountDirectory() = isolated { directory ->
        val storage = JournalDiskStore(directory, 101)
        for (id in listOf("../outside", "INVALID", "zzzzzzzzzzzzzzzzzzzzzzzzzz")) {
            assertThrows(IllegalArgumentException::class.java) { storage.read(id) }
        }
    }

    private fun isolated(test: (File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "journal-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        try { test(directory) } finally { directory.deleteRecursively() }
    }
}
