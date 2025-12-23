package com.flex.elefin

import android.net.Uri
import android.os.Bundle
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.leanback.LeanbackPlayerAdapter

/** Handles video playback with media controls. */
@UnstableApi
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<LeanbackPlayerAdapter>
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        val movie = activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
        val (_, title, description, _, _, videoUrl) = movie ?: return

        if (videoUrl == null) return

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)

        // Optimize buffering for high bitrate content
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // Min buffer 15s (reduced from 50s to avoid aggressive loading)
                50000, // Max buffer 50s
                5000,  // Buffer for playback 5s (increased from 2.5s)
                10000  // Buffer for rebuffer 10s (increased from 5s)
            )
            .build()
        
        player = ExoPlayer.Builder(requireContext())
            .setLoadControl(loadControl)
            .build()
            
        val playerAdapter = LeanbackPlayerAdapter(requireContext(), player!!, 1000)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = PlaybackTransportControlGlue(getActivity(), playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = title
        mTransportControlGlue.subtitle = description
        mTransportControlGlue.playWhenPrepared()

        // Use OkHttp for better performance
        val client = okhttp3.OkHttpClient.Builder()
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client)

        if (videoUrl.contains("|")) {
            val urls = videoUrl.split("|")
            val videoUri = Uri.parse(urls[0])
            val audioUri = Uri.parse(urls[1])

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            
            val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUri))
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUri))
            
            val mergingSource = androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
            player?.setMediaSource(mergingSource)
        } else {
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            val source = mediaSourceFactory.createMediaSource(mediaItem)
            player?.setMediaSource(source)
        }
        player?.prepare()
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}