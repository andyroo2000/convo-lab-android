package com.cdcg.convolab.typing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineAssetsTest {
    @Test fun repairsCorruptedModelAndAnInterruptedExtraction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = EngineAssets(context)
        val directory = assets.prepare()
        val model = File(directory, "model.gguf")
        RandomAccessFile(model, "rw").use { it.writeByte(0) }
        val pending = File(context.noBackupFilesDir, "typing-v1.pending")
        pending.mkdirs()
        File(pending, "partial").writeText("interrupted")
        assets.prepare()
        assertEquals(EngineAssets.MODEL_SIZE, model.length())
        assertEquals('G'.code, model.inputStream().use { it.read() })
        assertEquals(EngineAssets.MODEL_SHA, File(directory, "ready").readText())
        assertFalse(pending.exists())
        assertTrue(File(directory, "Dictionary/louds/charID.chid").isFile)
    }
}
