package com.cdcg.convolab.typing

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class EngineAssets(private val context: Context) {
    fun prepare(): File {
        val directory = File(context.noBackupFilesDir, "typing-v1")
        if (File(directory, "ready").exists()) return directory
        directory.deleteRecursively()
        check(directory.mkdirs()) { "Could not prepare the typing engine" }
        copyAsset("typing/Dictionary", File(directory, "Dictionary"))
        copyAsset("typing/model.gguf", File(directory, "model.gguf"))
        check(modelHash(File(directory, "model.gguf")) == MODEL_SHA) { "Typing model checksum mismatch" }
        File(directory, "ready").writeText(MODEL_SHA)
        return directory
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
    }
}
