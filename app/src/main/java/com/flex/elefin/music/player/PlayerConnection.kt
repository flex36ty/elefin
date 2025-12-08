package com.flex.elefin.music.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flex.elefin.music.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PlayerConnection"

/**
 * Singleton that manages the connection to the AudioPlayerService.
 * Provides a MediaController for controlling playback from the UI.
 */
object PlayerConnection {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var _controller: MediaController? = null
    
    val controller: MediaController?
        get() = _controller

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            Log.d(TAG, "isPlaying changed: $isPlaying")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state: $playbackState")
            when (playbackState) {
                Player.STATE_ENDED -> {
                    // Auto-advance to next track
                    val nextTrack = AudioQueueManager.next()
                    if (nextTrack != null) {
                        playTrack(nextTrack)
                    }
                }
                Player.STATE_READY -> {
                    _duration.value = _controller?.duration ?: 0L
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            Log.d(TAG, "Media item transition: ${mediaItem?.mediaMetadata?.title}")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _currentPosition.value = newPosition.positionMs
        }
    }

    /**
     * Connect to the AudioPlayerService
     */
    fun connect(context: Context) {
        if (_isConnected.value) {
            Log.d(TAG, "Already connected")
            return
        }

        Log.d(TAG, "Connecting to AudioPlayerService...")
        
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlayerService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                _controller = controllerFuture?.get()
                _controller?.addListener(playerListener)
                _isConnected.value = true
                Log.d(TAG, "Connected to AudioPlayerService")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to AudioPlayerService", e)
                _isConnected.value = false
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Disconnect from the AudioPlayerService
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from AudioPlayerService...")
        _controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _controller = null
        controllerFuture = null
        _isConnected.value = false
    }

    /**
     * Play a track
     */
    fun playTrack(track: Track) {
        val controller = _controller ?: run {
            Log.w(TAG, "Controller not connected")
            return
        }

        Log.d(TAG, "Playing track: ${track.name}")

        val mediaItem = MediaItem.Builder()
            .setUri(track.streamUrl)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.name)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.imageUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    /**
     * Play a list of tracks starting from a specific index
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val controller = _controller ?: run {
            Log.w(TAG, "Controller not connected")
            return
        }

        Log.d(TAG, "Playing ${tracks.size} tracks, starting at index $startIndex")

        // Set the queue
        AudioQueueManager.setQueue(tracks, startIndex)

        // Build media items
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.streamUrl)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.imageUrl?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    /**
     * Play/Pause toggle
     */
    fun playPause() {
        val controller = _controller ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    /**
     * Skip to next track
     */
    fun skipNext() {
        val controller = _controller ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            AudioQueueManager.next()
        }
    }

    /**
     * Skip to previous track
     */
    fun skipPrevious() {
        val controller = _controller ?: return
        // If we're more than 3 seconds into the track, restart it
        // Otherwise, go to previous track
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            AudioQueueManager.previous()
        } else {
            controller.seekTo(0)
        }
    }

    /**
     * Seek to position
     */
    fun seekTo(positionMs: Long) {
        _controller?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    /**
     * Stop playback
     */
    fun stop() {
        _controller?.stop()
    }

    /**
     * Update current position (call this from a timer/coroutine)
     */
    fun updatePosition() {
        _controller?.let {
            _currentPosition.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0L)
        }
    }
}

