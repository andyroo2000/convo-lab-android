package com.cdcg.convolab.typing

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal object TestOutput {
    fun file(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "typing-test-output")
        check(directory.isDirectory || directory.mkdirs())
        return File(directory, name)
    }
}
