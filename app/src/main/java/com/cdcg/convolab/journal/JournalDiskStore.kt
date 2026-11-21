package com.cdcg.convolab.journal

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** One account directory, one atomic server/draft record per entry. Corrupt records are never overwritten. */
class JournalDiskStore(noBackupDirectory: File, accountId: Long) : JournalStorage {
    private val directory = File(noBackupDirectory, "journal/$accountId")

    @Synchronized override fun load(): JournalLoad {
        if (!directory.exists()) return JournalLoad(emptyList(), emptyList())
        val files = directory.listFiles() ?: throw IOException("Journal directory could not be read")
        val records = mutableListOf<JournalRecord>()
        val recovery = mutableListOf<String>()
        val ids = files.map { it.name.removeSuffix(".bak") }.filter { it.endsWith(".json") }.distinct()
        for (name in ids) {
            try {
                read(name.removeSuffix(".json"))?.let(records::add)
            } catch (_: Exception) {
                recovery += name
            }
        }
        return JournalLoad(records, recovery)
    }

    @Synchronized override fun read(id: String): JournalRecord? {
        val file = file(id)
        val bytes = try { file.readFully() } catch (_: FileNotFoundException) { return null }
        return JournalJson.decode(bytes.toString(Charsets.UTF_8)).also {
            require(it.id == JournalIds.normalize(id)) { "Journal filename does not match record" }
        }
    }

    @Synchronized override fun write(record: JournalRecord) {
        // Validate an existing record before replacing it; unknown schema/corruption stays available for recovery.
        read(record.id)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Journal directory could not be created")
        val file = file(record.id)
        val stream = file.startWrite()
        try {
            stream.write(JournalJson.encode(record).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    @Synchronized override fun remove(id: String) {
        read(id) // Refuse to silently delete an unreadable record.
        val file = file(id)
        file.delete()
        if (file.baseFile.exists()) throw IOException("Journal record could not be removed")
    }

    private fun file(id: String) = AtomicFile(File(directory, JournalIds.normalize(id) + ".json"))
}
