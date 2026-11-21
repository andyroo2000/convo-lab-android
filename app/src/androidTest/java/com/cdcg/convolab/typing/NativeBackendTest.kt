package com.cdcg.convolab.typing

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackendTest {
    @Test fun packagedBackendMatchesTheDevicesInstructionSupport() {
        val arm64 = Build.SUPPORTED_ABIS.first() == "arm64-v8a"
        val flags = File("/proc/cpuinfo").readText().split(Regex("\\s+"))
        val expected = if (arm64 && "asimddp" in flags) "arm64-dotprod" else "generic"
        // Independent kernel capability report; catches missing plugins and loader failures.
        assertEquals(expected, NativeTyping.backend())
    }

    @Test fun disposingRepeatedModelsDoesNotAccumulateNativeWorkers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = threadCount()
        repeat(12) {
            val engine = OnDeviceEngine(context)
            try {
                engine.prepare()
                assertTrue(engine.convert("nihongo").candidates.contains("日本語"))
            } finally { engine.close() }
        }
        val after = threadCount()
        // Allow ART/Coroutine workers; leaking a pool per model far exceeds this.
        assertTrue("Threads grew from $before to $after after model disposal", after <= before + 8)
    }

    private fun threadCount() = checkNotNull(File("/proc/self/task").list()).size
}
