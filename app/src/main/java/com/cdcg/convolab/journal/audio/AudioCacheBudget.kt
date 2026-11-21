package com.cdcg.convolab.journal.audio

import java.io.File

/** Derived media is expendable; keep the newly generated recording and evict oldest cache files. */
object AudioCacheBudget {
    fun prune(directory: File, maximum: Long, protected: Set<String>) {
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        var bytes = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (bytes <= maximum) return
            if (file.name in protected) continue
            val size = file.length()
            if (file.delete()) bytes -= size
        }
    }
}
