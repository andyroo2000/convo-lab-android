package com.cdcg.convolab.typing

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class EngineAssets(private val context: Context) {
    fun prepare(): File {
        val directory = File(context.noBackupFilesDir, "typing-v1")
        if (isPrepared(directory)) return directory
        val staging = File(context.noBackupFilesDir, "typing-v1.pending")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Could not prepare the typing engine" }
        copyAsset("typing/Dictionary", File(staging, "Dictionary"))
        copyAsset("typing/model.gguf", File(staging, "model.gguf"))
        check(modelHash(File(staging, "model.gguf")) == MODEL_SHA) { "Typing model checksum mismatch" }
        File(staging, "ready").writeText(MODEL_SHA)
        directory.deleteRecursively()
        check(staging.renameTo(directory)) { "Could not finish preparing the typing engine" }
        return directory
    }

    private fun isPrepared(directory: File): Boolean {
        val marker = File(directory, "ready")
        val model = File(directory, "model.gguf")
        return marker.isFile && marker.readText() == MODEL_SHA && model.length() == MODEL_SIZE &&
            File(directory, "Dictionary/louds/charID.chid").isFile &&
            File(directory, "Dictionary/mm.binary").isFile && modelHash(model) == MODEL_SHA
    }

    private fun copyAsset(path: String, destination: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            context.assets.open(path).use { input -> destination.outputStream().use { input.copyTo(it) } }
        } else {
            check(destination.mkdirs())
            children.forEach { copyAsset("$path/$it", File(destination, it)) }
        }
    }

    private fun modelHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var count = input.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_SHA = "501f605d088f5b988791a00ae19ed46985ed7c48144f364b2f3f1f951c9b2083"
        const val MODEL_SIZE = 72298816L
    }
}
