package com.cdcg.convolab.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.GeneralSecurityException
import org.json.JSONException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** One encrypted, atomic record keeps the verified profile and bearer token consistent. */
class KeystoreSessionStorage(context: Context, private val keyAlias: String = "convolab.session.v1") : SessionStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "session.enc"))

    @Synchronized override fun read(): Session? {
        val bytes = try { file.readFully() } catch (_: FileNotFoundException) { return null }
        return try { decrypt(bytes) }
        catch (failure: GeneralSecurityException) { throw InvalidSessionStorage(failure) }
        catch (failure: JSONException) { throw InvalidSessionStorage(failure) }
        catch (failure: IllegalArgumentException) { throw InvalidSessionStorage(failure) }
    }

    private fun decrypt(bytes: ByteArray): Session {
        require(bytes.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        val json = JSONObject(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        return Session(json.getString("token"), Account(json.getLong("id"), json.getString("name"), json.getString("email")))
    }

    @Synchronized override fun write(session: Session) {
        val json = JSONObject().put("version", 1).put("token", session.token).put("id", session.account.id)
            .put("name", session.account.name).put("email", session.account.email)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(cipher.iv.size == IV_BYTES)
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    @Synchronized override fun clear() {
        file.delete()
        check(!file.baseFile.exists()) { "Could not remove local session" }
    }

    @Synchronized override fun resetInvalid() {
        clear()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
