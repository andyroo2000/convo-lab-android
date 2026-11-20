package com.cdcg.convolab.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.security.KeyStoreException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSessionStorageTest {
    @Test fun encryptedAtomicRecordSurvivesReopenAndClearsTogether() = isolated { context, directory ->
        val session = Session("secret-fixture-token", Account(42, "Sample learner", "sample@example.invalid"))
        val storage = KeystoreSessionStorage(context, "test.${directory.name}")
        storage.write(session)
        val bytes = File(directory, "session.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains(session.token))
        assertFalse(bytes.contains(session.account.email))
        val restored = KeystoreSessionStorage(context, "test.${directory.name}").read()!!
        assertEquals(session.token, restored.token)
        assertEquals(session.account, restored.account)
        storage.clear()
        assertNull(KeystoreSessionStorage(context, "test.${directory.name}").read())
    }

    @Test fun transientKeyAccessFailurePreservesTheEncryptedRecord() = isolated { context, directory ->
        val alias = "test.${directory.name}"
        val session = Session("fixture-token", Account(42, "Sample", "sample@example.invalid"))
        KeystoreSessionStorage(context, alias).write(session)
        val file = File(directory, "session.enc")
        val before = file.readBytes()
        val unavailable = KeystoreSessionStorage(context, alias) { throw KeyStoreException("temporarily busy") }
        assertThrows(KeyStoreException::class.java) { unavailable.read() }
        assertArrayEquals(before, file.readBytes())
        assertEquals(session.token, KeystoreSessionStorage(context, alias).read()?.token)
    }

    @Test fun corruptCiphertextIsRejectedAndPreserved() = isolated { context, directory ->
        val storage = KeystoreSessionStorage(context, "test.${directory.name}")
        storage.write(Session("fixture-token", Account(42, "Sample", "sample@example.invalid")))
        val file = File(directory, "session.enc")
        val corrupted = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(corrupted)
        assertThrows(InvalidSessionStorage::class.java) { storage.read() }
        assertArrayEquals(corrupted, file.readBytes())
        storage.resetInvalid()
        assertNull(storage.read())
        val replacement = Session("replacement-fixture", Account(43, "New", "new@example.invalid"))
        storage.write(replacement)
        assertEquals(replacement.token, storage.read()?.token)
    }

    private fun isolated(test: (Context, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "session-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir() = directory
        }
        try { test(context, directory) } finally {
            KeystoreSessionStorage(context, "test.${directory.name}").resetInvalid()
            directory.deleteRecursively()
        }
    }
}
