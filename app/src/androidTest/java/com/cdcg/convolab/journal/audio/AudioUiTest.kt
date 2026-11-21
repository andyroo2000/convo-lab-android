package com.cdcg.convolab.journal.audio

import android.content.Intent
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cdcg.convolab.journal.*
import com.cdcg.convolab.typing.TestOutput
import com.cdcg.convolab.ui.ConvoLabTheme
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class AudioUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val lease = PlaybackLease(999997)
    private lateinit var coordinator: AudioCoordinator
    private var connected: ListenableFuture<MediaController>? = null
    private var connections = 0
    private val source = AudioSource(JournalId(JournalIds.newId()), AudioContent.Final(JournalId(JournalIds.newId()), 1,
        "今日は晴れです。公園へ行きました。", "今日[きょう]は晴[は]れです。公園[こうえん]へ行[い]きました。", "A day at the park"))
    @After fun stop() {
        compose.runOnIdle { if (::coordinator.isInitialized) coordinator.close(); PlaybackOwner.active = null; scope.cancel() }
        File(context.noBackupFilesDir, "journal-audio/${lease.accountId}").deleteRecursively()
    }
    @Test fun realPlayerSupportsSeekingVoiceChoiceAndCachedReplay() {
        launchAudio()
        compose.onNodeWithText("Voice", substring = false).performClick()
        compose.onNodeWithText("Ren").performClick()
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.playback.value.playing && coordinator.playback.value.duration > 0 }
        assertEquals("ren", coordinator.state.value.active?.request?.voice)
        capture("journal-audio-playing.png")
        compose.onNodeWithText("Next sentence").performClick()
        compose.waitUntil(5_000) { coordinator.playback.value.position >= 9_500 }
        compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(5_000) { !coordinator.playback.value.playing }
        capture("journal-audio-paused.png")
        compose.onNodeWithText("Voice", substring = false).performClick()
        capture("journal-audio-voices.png")
        compose.onNodeWithText("Hana").performClick()
        assertNull(coordinator.state.value.active)
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.playback.value.playing }
        assertEquals("hana", coordinator.state.value.active?.request?.voice)
        compose.runOnIdle { coordinator.close() }
        assertNull(coordinator.state.value.active)
        assertFalse(coordinator.playback.value.playing)
    }
    @Test fun playbackContinuesBehindTheHomeScreenAndStopsWhenAccountCloses() {
        launchAudio()
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.playback.value.playing }
        val before = coordinator.playback.value.position
        device.pressHome()
        compose.waitUntil(5_000) { coordinator.playback.value.position > before + 500 }
        assertTrue(coordinator.playback.value.playing)
        context.startActivity(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        compose.waitForIdle()
        compose.onNodeWithText("Pause").assertIsDisplayed()
        compose.runOnIdle { coordinator.close(); PlaybackOwner.active = null }
        assertFalse(coordinator.playback.value.playing)
    }
    @Test fun failedServiceConnectionCanRetryWithoutChangingAccounts() {
        launchAudio(failFirstConnection = true)
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.state.value.errors[source.key] != null }
        assertNull(coordinator.state.value.active)
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.playback.value.playing }
        assertNotNull(coordinator.state.value.active)
    }

    @Test fun releasedControllerCanReconnectAndResumeCachedAudio() {
        launchAudio()
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.playback.value.playing }
        compose.runOnIdle { connected!!.get().release(); coordinator.toggle(source) }
        compose.waitUntil(5_000) { coordinator.playback.value.error != null }
        compose.onNodeWithText("Listen").performClick()
        compose.waitUntil(10_000) { coordinator.playback.value.playing }
        assertEquals(2, connections)
    }

    private fun launchAudio(failFirstConnection: Boolean = false) {
        PlaybackOwner.active = lease
        val api = AudioUiGateway()
        val player = AndroidAudioPlayer(context, lease, scope) {
            connections++
            if (failFirstConnection && connections == 1) Futures.immediateFailedFuture(IOException("Fixture connection failure"))
            else MediaController.Builder(context, SessionToken(context, ComponentName(context, JournalPlaybackService::class.java))).buildAsync().also { connected = it }
        }
        coordinator = AudioCoordinator(AudioAccess(api, AudioDiskStore(context.noBackupFilesDir, lease.accountId), player), scope)
        compose.setContent {
            ConvoLabTheme { CompositionLocalProvider(LocalJournalAudio provides coordinator) {
                Surface { Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Journal · Final", style = MaterialTheme.typography.headlineMedium)
                    AudioReading(source)
                    AudioControls(source)
                } }
            } }
        }
        compose.waitUntil(5_000) { coordinator.state.value.options[source.key] != null }
    }
    private fun capture(name: String) { compose.waitForIdle(); device.waitForIdle(); assertTrue(device.takeScreenshot(TestOutput.file(name))) }
}

private class AudioUiGateway : JournalAudioGateway {
    override suspend fun voices() = listOf(NarrationVoice("hana", "Hana", "/api/journal/avatars/female-01-v2"), NarrationVoice("ren", "Ren", "/api/journal/avatars/male-01-v2"))
    override suspend fun selectedVoice(source: AudioSource) = "hana"
    override suspend fun generate(request: AudioRequest) = AudioPayload(silentWave(), listOf(SentenceTiming("今日は晴れです。", 0.0, 10.0), SentenceTiming("公園へ行きました。", 10.0, 20.0)))
    private fun silentWave(): ByteArray {
        val sampleRate = 8000
        val length = sampleRate * 2 * 20
        return ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + length); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(length)
        }.array()
    }
}
