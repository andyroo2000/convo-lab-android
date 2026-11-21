package com.cdcg.convolab.auth.deletion

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/** Markers contain only an acknowledged account ID, never its password or token. */
class AccountCleanupDiskStore(private val root: File) : AccountCleanupStorage {
    private val markers = File(root, "account-cleanup")
    override suspend fun markConfirmed(accountId: Long) = withContext(Dispatchers.IO) { writeMarker(accountId) }
    override suspend fun pending(): Set<Long> = withContext(Dispatchers.IO) { pendingMarkers() }
    override suspend fun purge(accountId: Long) = withContext(Dispatchers.IO) { purgeAccount(accountId) }
    override suspend fun complete(accountId: Long) = withContext(Dispatchers.IO) { completeMarker(accountId) }

    private fun writeMarker(accountId: Long) {
        require(accountId > 0)
        if (!markers.isDirectory && !markers.mkdirs()) throw IOException("Cannot save cleanup intent")
        val file = marker(accountId)
        val stream = file.startWrite()
        try { stream.write(accountId.toString().toByteArray()); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    private fun pendingMarkers(): Set<Long> {
        if (!markers.exists()) return emptySet()
        val names = markers.list() ?: throw IOException("Cannot read cleanup intents")
        return names.mapNotNull { name ->
            val stem = name.removeSuffix(".bak").removeSuffix(".new").removeSuffix(".pending")
            stem.toLongOrNull()?.takeIf { it > 0 && name in markerNames(it) }
        }.toSet()
    }
    private fun purgeAccount(accountId: Long) {
        require(accountId > 0)
        for (family in listOf("journal", "journal-workflow", "journal-audio", "known-kanji", "journal-typing", "journal-preferences")) {
            val parent = File(root, family).toPath()
            if (Files.isSymbolicLink(parent)) throw IOException("Unsafe account storage path")
            removeTree(parent.resolve(accountId.toString()))
        }
    }
    private fun completeMarker(accountId: Long) {
        marker(accountId).delete()
        if (markerNames(accountId).any { File(markers, it).exists() }) throw IOException("Cannot finish cleanup intent")
    }
    private fun marker(id: Long) = AtomicFile(File(markers, "$id.pending"))
    private fun markerNames(id: Long) = setOf("$id.pending", "$id.pending.bak", "$id.pending.new")
    private fun removeTree(path: Path) {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, AccountFileRemoval())
    }
}

private class AccountFileRemoval : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
        Files.delete(file)
        return FileVisitResult.CONTINUE
    }
    override fun postVisitDirectory(directory: Path, failure: IOException?): FileVisitResult {
        if (failure != null) throw failure
        Files.delete(directory)
        return FileVisitResult.CONTINUE
    }
}
