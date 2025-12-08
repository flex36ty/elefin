package com.flex.elefin.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.flex.elefin.MainActivity
import com.flex.elefin.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val TAG = "AudioPlayerService"
private const val NOTIFICATION_CHANNEL_ID = "elefin_music_playback"
private const val NOTIFICATION_ID = 1001

/**
 * Foreground service for music playback using Media3 MediaSessionService.
 * Handles background playback, notification controls, and media session.
 */
@OptIn(UnstableApi::class)
class AudioPlayerService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioPlayerService created")

        createNotificationChannel()

        // Build ExoPlayer with audio focus handling
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Create MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(createSessionActivityPendingIntent())
            .build()

        Log.d(TAG, "MediaSession created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "AudioPlayerService destroyed")
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openMusic", true)
        }
        
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * MediaSession callback for handling custom commands and connection requests
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Log.d(TAG, "Controller connected: ${controller.packageName}")
            // Accept all connections with default commands
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                )
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                )
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            Log.d(TAG, "Controller post-connected: ${controller.packageName}")
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            Log.d(TAG, "Controller disconnected: ${controller.packageName}")
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            Log.d(TAG, "Custom command received: ${customCommand.customAction}")
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            Log.d(TAG, "Adding ${mediaItems.size} media items")
            return Futures.immediateFuture(mediaItems)
        }
    }
}
