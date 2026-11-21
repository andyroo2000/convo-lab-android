package com.cdcg.convolab.settings

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONException
import org.json.JSONObject

/** Re-downloadable, account-scoped knowledge cache; it never contains a provider token. */
class KnownKanjiDiskStore(noBackupDirectory: File, accountId: Long) : KnownKanjiStorage {
    private val directory = File(noBackupDirectory, "known-kanji/$accountId")
    private val file = AtomicFile(File(directory, "snapshot.json"))

    @Synchronized override fun read(): KnownKanjiSnapshot? {
        val bytes = try { file.readFully() } catch (_: FileNotFoundException) { return null }
        return try { KnownKanjiJson.decode(JSONObject(bytes.toString(Charsets.UTF_8))) }
        catch (_: JSONException) { null }
    }

    @Synchronized override fun write(snapshot: KnownKanjiSnapshot) {
        check(directory.isDirectory || directory.mkdirs())
        val stream = file.startWrite()
        try {
            stream.write(KnownKanjiJson.encode(snapshot).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}
