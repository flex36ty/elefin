package com.flex.elefin.player.cinema

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.flex.elefin.jellyfin.MediaStream
import java.io.File

internal fun requiresNativeDolbyVision(stream: MediaStream?): Boolean = stream != null && (
    stream.VideoRangeType?.startsWith("DOVI", ignoreCase = true) == true ||
    stream.VideoDoViTitle?.contains("Dolby Vision", ignoreCase = true) == true ||
    (stream.DvProfile ?: 0) > 0)

/** Platform Dolby Vision decoding keeps the RPU metadata and native video surface intact. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CinemaDolbyPlayer(context: Context, url: String, headers: String?, startMs: Long,
    onReady: () -> Unit, onTracks: () -> Unit, onEnded: () -> Unit, onError: (String) -> Unit) {
    private val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(
        headers.orEmpty().lineSequence().mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
        }.toMap())
    val player = ExoPlayer.Builder(context, DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))).build()
    val view = PlayerView(context).apply {
        useController = false
        val settings = com.flex.elefin.jellyfin.AppSettings(context)
        subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.exoSubtitleTextSize.toFloat())
            setStyle(androidx.media3.ui.CaptionStyleCompat(
                settings.exoSubtitleTextColor,
                if (settings.exoSubtitleBgTransparent) android.graphics.Color.TRANSPARENT else settings.exoSubtitleBgColor,
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK, null))
        }

        isFocusable = false
        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setEnableComposeSurfaceSyncWorkaround(true)
        player = this@CinemaDolbyPlayer.player
    }
    fun retryStream(url: String, positionMs: Long) {
        ready = false
        player.setMediaItem(MediaItem.fromUri(url), positionMs)
        player.prepare()
        player.playWhenReady = true
    }
    private val choices = mutableMapOf<Int, Pair<Tracks.Group, Int>>()
    private var sidecarIndex: Int? = null
    private var sidecarMetadata: MediaStream? = null
    private var ready = false
    private var selectSidecar = false
    init {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !ready) { ready = true; onReady() }
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (selectSidecar) {
                    tracks.groups.firstOrNull { group -> group.type == C.TRACK_TYPE_TEXT &&
                        (0 until group.length).any { group.getTrackFormat(it).label == SIDECAR_LABEL } }?.let { group ->
                        val index = (0 until group.length).first { group.getTrackFormat(it).label == SIDECAR_LABEL }
                        selectSidecar = false
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
                        Log.i("CinemaPlayer", "Native downloaded subtitle selected: server index=$sidecarIndex")
                    }
                }
                onTracks()
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("CinemaPlayer", "Native Dolby Vision failed: ${error.errorCodeName}")
                onError("Dolby Vision playback failed (${error.errorCodeName}). Retry or choose another version.")
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long) {
                Log.i("CinemaPlayer", "Native video decoder=$decoderName")
            }
            override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                Log.i("CinemaPlayer", "Native source=${format.width}x${format.height} fps=${format.frameRate} bitrate=${format.bitrate} codecs=${format.codecs} colors=${format.colorInfo}")
            }
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                Log.i("CinemaPlayer", "Native Dolby Vision first frame rendered")
            }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                Log.w("CinemaPlayer", "Native dropped=$droppedFrames over ${elapsedMs}ms")
            }
        })
        player.setMediaItem(MediaItem.fromUri(url), startMs)
        player.prepare()
        player.playWhenReady = true
    }
    fun tracks(streams: List<MediaStream>): List<CinemaTrack> {
        choices.clear()
        val ordinals = mutableMapOf<Int, Int>()
        return buildList {
            player.currentTracks.groups.forEach { group ->
                if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_TEXT) return@forEach
                for (i in 0 until group.length) {
                    val ordinal = ordinals.getOrDefault(group.type, 0)
                    ordinals[group.type] = ordinal + 1
                    val kind = if (group.type == C.TRACK_TYPE_AUDIO) "audio" else "sub"
                    val format = group.getTrackFormat(i)
                    val external = format.label == SIDECAR_LABEL
                    val stream = streams.filter { it.Type == (if (kind == "audio") "Audio" else "Subtitle") && it.IsExternal != true }.getOrNull(ordinal)
                    val id = choices.size
                    choices[id] = group to i
                    val metadata = if (external) sidecarMetadata ?: streams.firstOrNull { it.Index == sidecarIndex } else stream
                    add(CinemaTrack(id, kind, cinemaTrackLabel(metadata, if (external) null else format.label,
                        format.language, if (external) "Downloaded subtitle" else "Track ${ordinal + 1}"),
                        if (external) sidecarIndex else stream?.Index))
                }
            }
        }
    }
    fun selected(kind: String): Int = choices.entries.firstOrNull { (_, choice) ->
        choice.first.type == (if (kind == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT) && choice.first.isTrackSelected(choice.second)
    }?.key ?: -1
    fun select(id: Int) {
        selectSidecar = false
        val (group, index) = choices[id] ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
    }
    fun subtitlesOff() {
        selectSidecar = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
    }
    fun attachSubtitle(path: String, index: Int, codec: String?, metadata: MediaStream? = null) {
        sidecarMetadata = metadata
        val mime = when (codec?.lowercase() ?: path.substringAfterLast('.').lowercase()) {
            "ass", "ssa", "substationalpha" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            "pgs", "hdmv_pgs_subtitle", "sup" -> MimeTypes.APPLICATION_PGS
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime)
            .setLabel(SIDECAR_LABEL).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val item = player.currentMediaItem ?: return
        sidecarIndex = index
        selectSidecar = true
        val position = player.currentPosition
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(null).build()
        player.setMediaItem(item.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build(), position)
        player.prepare()
    }
    fun setAspect(mode: com.flex.elefin.screens.AspectMode) {
        view.resizeMode = when (mode) {
            com.flex.elefin.screens.AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            com.flex.elefin.screens.AspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.setAspectRatio(when (mode) {
            com.flex.elefin.screens.AspectMode.LETTERBOX -> 16f / 9f
            com.flex.elefin.screens.AspectMode.CINEMA -> 2.39f
            else -> player.videoSize.let { if (it.height > 0) it.width * it.pixelWidthHeightRatio / it.height else 0f }
        })
    }
    fun release() { view.player = null; player.release() }
    companion object { private const val SIDECAR_LABEL = "Elefin downloaded subtitle" }
}
