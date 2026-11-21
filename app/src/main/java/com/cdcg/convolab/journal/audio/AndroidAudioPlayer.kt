package com.cdcg.convolab.journal.audio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
class AndroidAudioPlayer(private val context: Context, private val owner: PlaybackLease, private val scope: CoroutineScope) : JournalAudioPlayer {
    private val mutableState = MutableStateFlow(AudioPlayback())
    override val state = mutableState.asStateFlow()
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progress: Job? = null
    private var closed = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish(player) }
        override fun onPlayerError(error: PlaybackException) {
            mutableState.value = state.value.copy(playing = false, buffering = false, error = "Audio could not be played. Try again.")
        }
    }

    override suspend fun play(audio: CachedAudio) {
        check(!closed && PlaybackOwner.active == owner)
        val player = connect()
        currentCoroutineContext().ensureActive()
        check(!closed && PlaybackOwner.active == owner)
        val metadata = MediaMetadata.Builder().setTitle(audio.request.source.content.title).setArtist("ConvoLab Journal").build()
        player.setMediaItem(MediaItem.Builder().setMediaId(owner.id + ":" + audio.request.key).setUri(audio.file.toUri()).setMediaMetadata(metadata).build())
        player.prepare()
        player.play()
        mutableState.value = AudioPlayback(buffering = true)
        progress?.cancel()
        progress = scope.launch { while (isActive) { publish(player); delay(100) } }
    }
    override fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playerError != null) player.prepare()
            player.play()
        }
        publish(player)
    }
    override fun seek(milliseconds: Long) {
        val player = controller ?: return
        player.seekTo(milliseconds.coerceIn(0, player.duration.coerceAtLeast(0)))
        publish(player)
    }
    override fun stop() {
        progress?.cancel()
        controller?.stop()
        controller?.clearMediaItems()
        mutableState.value = AudioPlayback()
    }
    override fun close() {
        closed = true
        stop()
        controller?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        controller = null
        future = null
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val pending = future ?: MediaController.Builder(context, SessionToken(context, ComponentName(context, JournalPlaybackService::class.java)))
            .buildAsync().also { future = it }
        val player = suspendCancellableCoroutine { continuation ->
            pending.addListener({
                if (continuation.isActive) {
                    try { continuation.resume(pending.get()) }
                    catch (failure: Exception) { continuation.resumeWithException(failure) }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        controller = player
        player.addListener(listener)
        return player
    }
    private fun publish(player: Player) {
        mutableState.value = AudioPlayback(player.isPlaying, player.playbackState == Player.STATE_BUFFERING,
            player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0),
            if (player.playerError == null) null else "Audio could not be played. Try again.")
    }
}
