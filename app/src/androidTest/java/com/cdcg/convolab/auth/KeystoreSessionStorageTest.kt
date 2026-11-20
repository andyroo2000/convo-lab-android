package com.cdcg.convolab.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSessionStorageTest {
    @Test fun encryptedAtomicRecordSurvivesReopenAndClearsTogether() = isolated { context, directory ->
        val session = Session("secret-fixture-token", Account(42, "Sample learner", "sample@example.invalid"))
        val storage = KeystoreSessionStorage(context)
        storage.write(session)
        val bytes = File(directory, "session.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains(session.token))
        assertFalse(bytes.contains(session.account.email))
        val restored = KeystoreSessionStorage(context).read()!!
        assertEquals(session.token, restored.token)
        assertEquals(session.account, restored.account)
        storage.clear()
        assertNull(KeystoreSessionStorage(context).read())
    }

    @Test fun corruptCiphertextIsRejectedAndPreserved() = isolated { context, directory ->
        val storage = KeystoreSessionStorage(context)
        storage.write(Session("fixture-token", Account(42, "Sample", "sample@example.invalid")))
        val file = File(directory, "session.enc")
        val corrupted = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(corrupted)
        assertThrows(Exception::class.java) { storage.read() }
        assertArrayEquals(corrupted, file.readBytes())
    }

    private fun isolated(test: (Context, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "session-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir() = directory
        }
        try { test(context, directory) } finally { directory.deleteRecursively() }
    }
}
