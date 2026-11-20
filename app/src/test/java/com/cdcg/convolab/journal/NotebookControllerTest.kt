package com.cdcg.convolab.journal

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookControllerTest {
    @Test fun typingAndSwitchingEntriesFlushesTheLatestTextPerEntry() = runTest {
        val storage = NotebookMemory()
        val repository = JournalRepository(NotebookGateway(), storage, StandardTestDispatcher(testScheduler))
        val controller = NotebookController(repository, backgroundScope)
        controller.start().join()
        val first = controller.state.value.draft.id
        controller.edit("今")
        controller.edit("今日は晴れです。")
        controller.newEntry().join()
        val second = controller.state.value.draft.id
        controller.edit("明日は雨です。")
        controller.flush().join()
        assertEquals("今日は晴れです。", storage.records[first]?.draft?.body)
        assertEquals("明日は雨です。", storage.records[second]?.draft?.body)
        assertTrue(controller.state.value.savedLocally)
    }

    @Test fun failedKeepAndCloseKeepsTheEditorAndDurableDraft() = runTest {
        val storage = NotebookMemory()
        val api = NotebookGateway().apply { failSaves = true }
        val controller = NotebookController(JournalRepository(api, storage, StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.start().join()
        controller.edit("書いた文章。")
        controller.requestClose()
        controller.keepAndClose().join()
        assertTrue(controller.state.value.closePrompt)
        assertEquals("書いた文章。", controller.state.value.draft.body)
        assertNotNull(controller.state.value.error)
        assertEquals("書いた文章。", storage.records.values.single().pendingSave?.body)
    }

    @Test fun failedLocalWriteBlocksNavigationAndRetriesWithoutLosingText() = runTest {
        val storage = NotebookMemory().apply { failWrites = true }
        val controller = NotebookController(JournalRepository(NotebookGateway(), storage, StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.start().join()
        controller.edit("大切な文章。")
        val id = controller.state.value.draft.id
        controller.newEntry().join()
        assertEquals(id, controller.state.value.draft.id)
        assertFalse(controller.state.value.savedLocally)
        storage.failWrites = false
        controller.flush().join()
        assertEquals("大切な文章。", storage.records[id]?.draft?.body)
        assertTrue(controller.state.value.savedLocally)
    }

    @Test fun restartingOfflineResumesANonemptyLocalDraft() = runTest {
        val draft = JournalDraft.blank().edited("続けたい文章。")
        val storage = NotebookMemory().apply { records[draft.id] = JournalRecord(draft = draft) }
        val api = NotebookGateway().apply { failArchive = true }
        val controller = NotebookController(JournalRepository(api, storage, StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.start().join()
        assertEquals(draft, controller.state.value.draft)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.savedLocally)
        assertNotNull(controller.state.value.error)
    }

    @Test fun closingAnEmptyNewScratchDoesNotLeaveABlankContentsRow() = runTest {
        val storage = NotebookMemory()
        val controller = NotebookController(JournalRepository(NotebookGateway(), storage, StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.start().join()
        controller.edit("消す文章")
        controller.edit("")
        controller.keepAndClose().join()
        assertTrue(storage.records.isEmpty())
        assertFalse(controller.state.value.closePrompt)
    }

    @Test fun typingLimitCountsUnicodeCodePointsAndPreservesLastAcceptedText() = runTest {
        val controller = NotebookController(JournalRepository(NotebookGateway(), NotebookMemory(), StandardTestDispatcher(testScheduler)), backgroundScope)
        controller.start().join()
        val text = "𠮷".repeat(20_000)
        controller.edit(text)
        controller.edit(text + "あ")
        assertEquals(text, controller.state.value.draft.body)
        assertNotNull(controller.state.value.error)
        runCurrent()
    }
}

internal class NotebookMemory : JournalStorage {
    val records = mutableMapOf<String, JournalRecord>()
    var failWrites = false
    override fun load() = JournalLoad(records.values.toList(), emptyList())
    override fun read(id: String) = records[id]
    override fun write(record: JournalRecord) {
        if (failWrites) throw IOException("fixture storage unavailable")
        records[record.id] = record
    }
    override fun remove(id: String) { records.remove(id) }
}

internal class NotebookGateway : JournalGateway {
    var failSaves = false
    var failArchive = false
    override suspend fun archive(cursor: String?): JournalPage {
        if (failArchive) throw IOException("fixture offline")
        return JournalPage(emptyList(), null)
    }
    override suspend fun unfinished() = archive(null)
    override suspend fun entry(id: String): JournalEntry = error("Unexpected request")
    override suspend fun save(draft: JournalDraft): JournalEntry {
        if (failSaves) throw IOException("fixture offline")
        return JournalEntry(draft.id, draft.title, draft.body, draft.entryDate, draft.expectedVersion + 1, "created", "updated")
    }
    override suspend fun delete(id: String, version: Int) = Unit
}
