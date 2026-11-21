package com.cdcg.convolab.journal.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cdcg.convolab.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.UUID

data class PlaybackLease(val accountId: Long, val id: String = UUID.randomUUID().toString())
object PlaybackOwner { @Volatile var active: PlaybackLease? = null }

@androidx.annotation.OptIn(UnstableApi::class)
class JournalPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).setCallback(PlaybackCallback()).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }

    private inner class PlaybackCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.uid != Process.myUid() && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
            return super.onConnect(session, controller)
        }
        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
            if (controller.uid != Process.myUid()) return Futures.immediateFuture(emptyList())
            return Futures.immediateFuture(mediaItems.filter(::permitted))
        }
        private fun permitted(item: MediaItem): Boolean {
            val owner = PlaybackOwner.active ?: return false
            val uri = item.localConfiguration?.uri ?: return false
            if (uri.scheme != "file" || !item.mediaId.startsWith(owner.id + ":")) return false
            val path = uri.path ?: return false
            val directory = File(noBackupFilesDir, "journal-audio/${owner.accountId}").canonicalFile
            return File(path).canonicalFile.parentFile == directory
        }
    }
}
