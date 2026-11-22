package com.cdcg.convolab.journal

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/** A derived, account-scoped index. Journal records and workflow files are never removed here. */
class JournalArchiveDiskStore(noBackupDirectory: File, accountId: Long) : JournalArchiveStorage {
    // No .json suffix: JournalDiskStore enumerates only the per-entry JSON records.
    private val file = AtomicFile(File(noBackupDirectory, "journal/$accountId/archive-index"))

    @Synchronized override fun read(): JournalArchive? {
        val bytes = try { file.readFully() } catch (_: FileNotFoundException) { return null }
        return try {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.getInt("schemaVersion") == 1)
            val ids = json.getJSONArray("entryIds")
            JournalArchive((0 until ids.length()).map { JournalIds.normalize(ids.getString(it)) }.toSet(), json.nullableString("nextCursor"))
        } catch (_: Exception) { null } // Rebuild a corrupt derived index from the server, retaining every entry.
    }

    @Synchronized override fun write(archive: JournalArchive) {
        val directory = file.baseFile.parentFile!!
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Journal list could not be saved")
        val bytes = JSONObject().put("schemaVersion", 1).put("entryIds", JSONArray(archive.entryIds.toList()))
            .put("nextCursor", archive.nextCursor ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
}
